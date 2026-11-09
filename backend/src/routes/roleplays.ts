import { randomUUID } from "node:crypto";
import { and, desc, eq } from "drizzle-orm";
import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { conversation, roleplay } from "../db/app-schema.js";
import { db } from "../db/index.js";
import { collectConversationFiles } from "../lib/attachments.js";
import { conversationIdsWhere, createConversation, listMessages } from "../lib/conversations.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";
import { serializeConversation, serializeMessages, serializeRoleplay } from "../lib/serializers.js";
import { generateRoleplay } from "../lib/tutor.js";

const createSchema = z.object({
  scenario: z.string().trim().min(2).max(120),
  userRole: z.string().trim().max(40).optional(),
  aiRole: z.string().trim().max(40).optional(),
});

async function loadRoleplay(userId: string, id: string) {
  const row = await db.query.roleplay.findFirst({ where: and(eq(roleplay.id, id), eq(roleplay.userId, userId)) });
  if (!row) throw new HTTPException(404, { message: "Roleplay not found" });
  return row;
}

async function startRun(row: typeof roleplay.$inferSelect) {
  const profile = await getProfile(row.userId);
  const thread = await createConversation(
    { userId: row.userId, kind: "roleplay", title: row.title, language: row.language, roleplayId: row.id },
    profile,
    { withOpening: true },
  );
  const messages = await listMessages(thread.id);
  return { conversation: serializeConversation(thread), messages: await serializeMessages(messages) };
}

const aiLimit = rateLimit({ windowMs: 60_000, max: 10 });

export const roleplayRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/", async (c) => {
    const userId = c.get("user").id;
    const { targetLanguage } = await getProfile(userId);
    const rows = await db.query.roleplay.findMany({
      where: and(eq(roleplay.userId, userId), eq(roleplay.language, targetLanguage)),
      orderBy: [desc(roleplay.updatedAt)],
    });
    return c.json({ roleplays: rows.map(serializeRoleplay) });
  })
  /** Creates the roleplay and immediately opens its first run, so the app can jump into the scene. */
  .post("/", aiLimit, async (c) => {
    const userId = c.get("user").id;
    const body = await parseBody(c, createSchema);
    const profile = await getProfile(userId);
    const generated = await generateRoleplay(body, profile);
    const [row] = await db
      .insert(roleplay)
      .values({
        id: randomUUID(),
        userId,
        language: profile.targetLanguage,
        ...generated,
        // Roles the learner typed win over the model's wording.
        userRole: body.userRole || generated.userRole,
        aiRole: body.aiRole || generated.aiRole,
      })
      .returning();
    return c.json({ roleplay: serializeRoleplay(row), ...(await startRun(row)) }, 201);
  })
  .get("/:id", async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    return c.json({ roleplay: serializeRoleplay(row) });
  })
  .delete("/:id", async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    const removeFiles = await collectConversationFiles(await conversationIdsWhere(eq(conversation.roleplayId, row.id)));
    await db.delete(roleplay).where(eq(roleplay.id, row.id));
    await removeFiles();
    return c.body(null, 204);
  })
  /** Resumes the latest run of this roleplay, or starts one if there is none. */
  .post("/:id/open", aiLimit, async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    const latest = await db.query.conversation.findFirst({
      where: and(eq(conversation.roleplayId, row.id), eq(conversation.userId, row.userId)),
      orderBy: [desc(conversation.updatedAt)],
    });
    if (!latest) return c.json(await startRun(row));
    const messages = await listMessages(latest.id);
    return c.json({ conversation: serializeConversation(latest), messages: await serializeMessages(messages) });
  })
  .post("/:id/restart", aiLimit, async (c) => {
    const row = await loadRoleplay(c.get("user").id, c.req.param("id"));
    return c.json(await startRun(row), 201);
  });
