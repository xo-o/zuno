import { randomUUID } from "node:crypto";
import { and, asc, desc, eq } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { vocabPhrase } from "../db/app-schema.js";
import { db } from "../db/index.js";
import { recordPracticeActivity } from "../lib/activity.js";
import { generateJson } from "../lib/gemini.js";
import { parseBody, requireAuth, type AuthEnv } from "../lib/http.js";
import { languageName } from "../lib/languages.js";
import { getProfile } from "../lib/profile.js";

const createPhraseSchema = z.object({
  phrase: z.string().trim().min(1).max(200),
  translation: z.string().trim().max(240).optional(),
  source: z.string().trim().max(100).optional(),
  contextNote: z.string().trim().max(240).optional(),
  autoTranslate: z.boolean().optional().default(true),
});

const updatePhraseSchema = z.object({
  phrase: z.string().trim().min(1).max(200).optional(),
  translation: z.string().trim().min(1).max(240).optional(),
  contextNote: z.string().trim().max(240).nullable().optional(),
});

const reviewSchema = z.object({
  passed: z.boolean(),
});

const captureSchema = z.object({
  phrases: z
    .array(
      z.object({
        phrase: z.string().trim().min(1).max(200),
        translation: z.string().trim().max(240).optional(),
        source: z.string().trim().max(100).optional(),
      }),
    )
    .min(1)
    .max(20),
});

const extractSchema = z.object({
  turns: z
    .array(
      z.object({
        isUser: z.boolean(),
        text: z.string().trim().min(1).max(600),
      }),
    )
    .min(1)
    .max(60),
  tutorName: z.string().trim().max(80).optional(),
});

const extractResponseSchema = z.object({
  phrases: z
    .array(
      z.object({
        phrase: z.string().trim().min(1).max(120),
        translation: z.string().trim().min(1).max(160),
        source: z.enum(["user", "tutor"]),
      }),
    )
    .max(4),
});

/**
 * Language-agnostic structural sanitization: strips surrounding whitespace/quotes and ensures
 * the candidate contains at least one Unicode letter in any script and stays within flashcard bounds.
 */
export function sanitizeExtractedPhrase(raw: string): string | null {
  const text = raw.replace(/^[\s“”"«»'‘’`]+|[\s“”"«»'‘’`]+$/g, "").trim();
  if (text.length < 1 || text.length > 120) return null;
  if (!/\p{L}/u.test(text)) return null;
  return text;
}

const translateSchema = z.object({
  translation: z.string(),
  exampleSentence: z.string(),
  exampleTranslation: z.string(),
  syllableStress: z.string(),
  recallCue: z.string(),
});

async function enrichPhrase(phrase: string, targetLang: string, nativeLang: string, contextNote?: string) {
  try {
    return await generateJson({
      system: `You are a concise bilingual dictionary and SRS flashcard generator. Target language: ${languageName(targetLang)}. Learner language: ${languageName(nativeLang)}.`,
      contents: `Phrase: "${phrase}"${contextNote ? `\nContext: ${contextNote}` : ""}\nReturn concise natural translation, one realistic short example sentence in the target language, its translation, syllable stress pattern (e.g. "es·CA·la"), and a short recall cue in ${languageName(nativeLang)}.`,
      schema: translateSchema,
      thinking: "low",
      temperature: 0.3,
    });
  } catch {
    return {
      translation: "",
      exampleSentence: "",
      exampleTranslation: "",
      syllableStress: "",
      recallCue: "",
    };
  }
}

function serializePhrase(row: typeof vocabPhrase.$inferSelect) {
  const dueNow = row.bucket === "due" || row.dueAt.getTime() <= Date.now();
  const intervalLabel = dueNow
    ? "Due now"
    : row.intervalDays > 0
      ? `${row.intervalDays}d`
      : "1d";
  return {
    id: row.id,
    language: row.language,
    phrase: row.phrase,
    translation: row.translation,
    source: row.source,
    contextNote: row.contextNote,
    exampleSentence: row.exampleSentence,
    exampleTranslation: row.exampleTranslation,
    syllableStress: row.syllableStress,
    recallCue: row.recallCue,
    streak: row.streak,
    intervalDays: row.intervalDays,
    intervalLabel,
    bucket: row.bucket,
    dueNow,
    dueAt: row.dueAt.toISOString(),
  };
}

const INTERVAL_LADDER = [1, 3, 7, 14, 30];

export const vocabularyRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const userId = c.get("user").id;
    const profile = await getProfile(userId);
    const rows = await db
      .select()
      .from(vocabPhrase)
      .where(and(eq(vocabPhrase.userId, userId), eq(vocabPhrase.language, profile.targetLanguage)))
      .orderBy(asc(vocabPhrase.dueAt), desc(vocabPhrase.createdAt));

    const phrases = rows.map(serializePhrase);
    const due = phrases.filter((p) => p.dueNow).length;
    const learning = phrases.filter((p) => !p.dueNow && p.bucket === "learning").length;
    const mastered = phrases.filter((p) => !p.dueNow && p.bucket === "mastered").length;
    return c.json({
      phrases,
      counts: { due, learning, mastered, total: phrases.length },
    });
  })
  .post("/preview", async (c) => {
    const profile = await getProfile(c.get("user").id);
    const body = await parseBody(c, z.object({ phrase: z.string().trim().min(1).max(200), contextNote: z.string().trim().max(240).optional() }));
    const enriched = await enrichPhrase(body.phrase, profile.targetLanguage, profile.nativeLanguage, body.contextNote);
    return c.json(enriched);
  })
  .post("/extract", async (c) => {
    const userId = c.get("user").id;
    const profile = await getProfile(userId);
    const body = await parseBody(c, extractSchema);
    const targetName = languageName(profile.targetLanguage);
    const nativeName = languageName(profile.nativeLanguage);
    const tutorLabel = body.tutorName?.trim() || "Tutor";
    const transcript = body.turns
      .map((t) => `${t.isUser ? "Learner" : tutorLabel}: ${t.text}`)
      .join("\n");

    const existingRows = await db
      .select({ phrase: vocabPhrase.phrase })
      .from(vocabPhrase)
      .where(and(eq(vocabPhrase.userId, userId), eq(vocabPhrase.language, profile.targetLanguage)));
    const seen = new Set(existingRows.map((row) => row.phrase.trim().toLowerCase()));

    try {
      const result = await generateJson({
        system: [
          `You extract clean, genuinely useful vocabulary words, idioms, and short everyday expressions from a spoken voice call transcript for a ${nativeName} speaker learning ${targetName} (CEFR ${profile.level}).`,
          `Return up to 4 complete, self-contained ${targetName} lexical items or expressions that appeared in the conversation and are worth saving as flashcards.`,
          `Exclude speech disfluencies and vocal fillers, hesitation or stalling remarks, incomplete or cut-off speech-to-text fragments, meta-conversation about the call itself, and utterances purely in ${nativeName} (unless ${targetName} is ${nativeName}, in which case still exclude all disfluencies, hesitations, fragments, and meta-talk).`,
          `Strip any leading speech fillers or conversational framing so each returned phrase is clean, natural, and self-contained.`,
          `For each item set "translation" to a crisp ${nativeName} meaning and "source" to "user" or "tutor".`,
          `If the transcript contains no clean, worthwhile ${targetName} vocabulary items, return {"phrases": []}.`,
        ].join(" "),
        contents: `Transcript:\n${transcript}`,
        schema: extractResponseSchema,
        thinking: "low",
        temperature: 0.2,
        timeoutMs: 6_000,
      });

      const cleaned = [];
      for (const item of result.phrases) {
        const phrase = sanitizeExtractedPhrase(item.phrase);
        if (!phrase) continue;
        const translation = item.translation.trim();
        if (!translation) continue;
        const key = phrase.toLowerCase();
        if (seen.has(key)) continue;
        seen.add(key);
        cleaned.push({
          phrase,
          translation,
          tag: item.source === "user" ? "Your reply" : `Used by ${tutorLabel}`,
        });
      }
      return c.json({ phrases: cleaned.slice(0, 4) });
    } catch {
      return c.json({ phrases: [] });
    }
  })
  .post("/", async (c) => {
    const userId = c.get("user").id;
    const profile = await getProfile(userId);
    const body = await parseBody(c, createPhraseSchema);

    const existing = await db
      .select()
      .from(vocabPhrase)
      .where(and(eq(vocabPhrase.userId, userId), eq(vocabPhrase.language, profile.targetLanguage), eq(vocabPhrase.phrase, body.phrase)))
      .limit(1);
    if (existing[0]) {
      return c.json({ phrase: serializePhrase(existing[0]) });
    }

    const enriched =
      body.autoTranslate || !body.translation
        ? await enrichPhrase(body.phrase, profile.targetLanguage, profile.nativeLanguage, body.contextNote)
        : { translation: "", exampleSentence: "", exampleTranslation: "", syllableStress: "", recallCue: "" };

    const [created] = await db
      .insert(vocabPhrase)
      .values({
        id: randomUUID(),
        userId,
        language: profile.targetLanguage,
        phrase: body.phrase,
        translation: body.translation?.trim() || enriched.translation || "Saved phrase",
        source: body.source?.trim() || "Added manually",
        contextNote: body.contextNote?.trim() || null,
        exampleSentence: enriched.exampleSentence || null,
        exampleTranslation: enriched.exampleTranslation || null,
        syllableStress: enriched.syllableStress || null,
        recallCue: enriched.recallCue || null,
        streak: 0,
        intervalDays: 0,
        bucket: "due",
        dueAt: new Date(),
      })
      .returning();

    return c.json({ phrase: serializePhrase(created) }, 201);
  })
  .post("/capture", async (c) => {
    const userId = c.get("user").id;
    const profile = await getProfile(userId);
    const body = await parseBody(c, captureSchema);

    const saved = [];
    for (const item of body.phrases) {
      const existing = await db
        .select()
        .from(vocabPhrase)
        .where(and(eq(vocabPhrase.userId, userId), eq(vocabPhrase.language, profile.targetLanguage), eq(vocabPhrase.phrase, item.phrase)))
        .limit(1);
      if (existing[0]) {
        saved.push(serializePhrase(existing[0]));
        continue;
      }
      const enriched = item.translation
        ? { translation: item.translation, exampleSentence: "", exampleTranslation: "", syllableStress: "", recallCue: "" }
        : await enrichPhrase(item.phrase, profile.targetLanguage, profile.nativeLanguage);

      const [row] = await db
        .insert(vocabPhrase)
        .values({
          id: randomUUID(),
          userId,
          language: profile.targetLanguage,
          phrase: item.phrase,
          translation: item.translation?.trim() || enriched.translation || "Saved phrase",
          source: item.source?.trim() || "Call capture",
          exampleSentence: enriched.exampleSentence || null,
          exampleTranslation: enriched.exampleTranslation || null,
          syllableStress: enriched.syllableStress || null,
          recallCue: enriched.recallCue || null,
          streak: 0,
          intervalDays: 0,
          bucket: "due",
          dueAt: new Date(),
        })
        .returning();
      saved.push(serializePhrase(row));
    }

    return c.json({ phrases: saved }, 201);
  })
  .patch("/:id", async (c) => {
    const userId = c.get("user").id;
    const id = c.req.param("id");
    const body = await parseBody(c, updatePhraseSchema);
    const [existing] = await db
      .select()
      .from(vocabPhrase)
      .where(and(eq(vocabPhrase.id, id), eq(vocabPhrase.userId, userId)))
      .limit(1);
    if (!existing) throw new HTTPException(404, { message: "Phrase not found" });

    const [updated] = await db
      .update(vocabPhrase)
      .set({
        ...(body.phrase !== undefined && { phrase: body.phrase }),
        ...(body.translation !== undefined && { translation: body.translation }),
        ...(body.contextNote !== undefined && { contextNote: body.contextNote }),
      })
      .where(and(eq(vocabPhrase.id, id), eq(vocabPhrase.userId, userId)))
      .returning();

    return c.json({ phrase: serializePhrase(updated) });
  })
  .post("/:id/review", async (c) => {
    const userId = c.get("user").id;
    const id = c.req.param("id");
    const { passed } = await parseBody(c, reviewSchema);
    const [existing] = await db
      .select()
      .from(vocabPhrase)
      .where(and(eq(vocabPhrase.id, id), eq(vocabPhrase.userId, userId)))
      .limit(1);
    if (!existing) throw new HTTPException(404, { message: "Phrase not found" });

    const nextStreak = passed ? existing.streak + 1 : 0;
    const intervalDays = passed
      ? INTERVAL_LADDER[Math.min(nextStreak - 1, INTERVAL_LADDER.length - 1)]
      : 1;
    const bucket: "due" | "learning" | "mastered" = !passed
      ? "due"
      : intervalDays >= 30
        ? "mastered"
        : "learning";
    const dueAt = new Date(Date.now() + intervalDays * 86_400_000);

    const [updated] = await db
      .update(vocabPhrase)
      .set({ streak: nextStreak, intervalDays, bucket, dueAt })
      .where(and(eq(vocabPhrase.id, id), eq(vocabPhrase.userId, userId)))
      .returning();

    await recordPracticeActivity(userId, existing.language, { vocabReviewed: 1 }).catch((err) =>
      console.warn("recordPracticeActivity(vocab) failed:", err),
    );

    return c.json({ phrase: serializePhrase(updated) });
  })
  .delete("/:id", async (c) => {
    const userId = c.get("user").id;
    const id = c.req.param("id");
    await db.delete(vocabPhrase).where(and(eq(vocabPhrase.id, id), eq(vocabPhrase.userId, userId)));
    return c.body(null, 204);
  });
