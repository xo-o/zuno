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
