import { randomUUID } from "node:crypto";
import { and, eq, inArray, isNull, lt } from "drizzle-orm";
import { HTTPException } from "hono/http-exception";
import mammoth from "mammoth";
import { attachment, type AttachmentDigest } from "../db/app-schema.js";
import { db } from "../db/index.js";
import type { GeminiFile } from "./gemini.js";
import { fetchPage, parseLink, UnreadableLinkError, youtubeTitle, youtubeUrl } from "./links.js";
import { getProfile } from "./profile.js";
import { deleteObjects, getObjectBytes, headObject, presignDownload, presignUpload } from "./storage.js";
import { digestAttachment } from "./tutor.js";

export type AttachmentRow = typeof attachment.$inferSelect;

const MB = 1024 * 1024;
export const MAX_ATTACHMENTS_PER_MESSAGE = 4;

const DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

/** Accepted uploads. Images and PDFs go to Gemini as-is; DOCX and TXT are sent as extracted text. */
const TYPES: Record<string, { kind: "image" | "document"; maxBytes: number; inline: boolean }> = {
  "image/jpeg": { kind: "image", maxBytes: 10 * MB, inline: true },
  "image/png": { kind: "image", maxBytes: 10 * MB, inline: true },
  "image/webp": { kind: "image", maxBytes: 10 * MB, inline: true },
  "image/heic": { kind: "image", maxBytes: 10 * MB, inline: true },
  "image/heif": { kind: "image", maxBytes: 10 * MB, inline: true },
  "application/pdf": { kind: "document", maxBytes: 20 * MB, inline: true },
  [DOCX]: { kind: "document", maxBytes: 20 * MB, inline: false },
  "text/plain": { kind: "document", maxBytes: 2 * MB, inline: false },
};

/** Unsent uploads older than this are deleted by the sweep. Long enough for drafts kept across "Remind me to continue". */
const ORPHAN_TTL_MS = 14 * 24 * 60 * 60 * 1000;

const LINK_MIME = { video: "video/x-youtube", page: "text/html" } as const;

/** Learning material for a new topic can hold more files than a single message. */
export const MAX_TOPIC_MATERIAL = 8;
/** How many earlier images are resent as pixels (rather than just their digest) on each turn. */
const RECENT_IMAGES_RESENT = 2;
const HISTORY_TEXT_PER_ATTACHMENT = 1500;

function typeOf(mimeType: string) {
  const type = TYPES[mimeType];
  if (!type) throw new HTTPException(415, { message: "This file type isn't supported. Use a photo, PDF, DOCX or TXT file." });
  return type;
}

function sanitizeFileName(name: string) {
  const cleaned = name.replace(/[\u0000-\u001f/\\]/g, "_").trim();
  return (cleaned || "file").slice(0, 120);
}

export async function createUpload(
  userId: string,
  input: { fileName: string; mimeType: string; sizeBytes: number; width?: number; height?: number },
) {
  const type = typeOf(input.mimeType);
  if (input.sizeBytes > type.maxBytes) {
    throw new HTTPException(413, { message: `This file is too large. The limit is ${type.maxBytes / MB} MB.` });
  }
  const id = randomUUID();
  const [row] = await db
    .insert(attachment)
    .values({
      id,
      userId,
      kind: type.kind,
      mimeType: input.mimeType,
      fileName: sanitizeFileName(input.fileName),
      sizeBytes: input.sizeBytes,
      storageKey: `u/${userId}/${id}`,
      width: input.width,
      height: input.height,
    })
    .returning();
  return { row, uploadUrl: await presignUpload(row.storageKey, row.mimeType) };
}

/** Creates a link attachment and reads the article or video in the background. */
export async function createLink(userId: string, raw: string) {
  const url = parseLink(raw);
  const video = youtubeUrl(url);
  const [row] = await db
    .insert(attachment)
    .values({
      id: randomUUID(),
      userId,
      kind: "link",
      mimeType: video ? LINK_MIME.video : LINK_MIME.page,
      fileName: url.hostname.replace(/^www\./, ""),
      sizeBytes: 0,
      storageKey: "",
      url: video ?? url.toString(),
      status: "processing",
    })
    .returning();
  void processAttachment(row);
  return row;
}

export async function findOwnAttachment(userId: string, id: string) {
  const row = await db.query.attachment.findFirst({ where: and(eq(attachment.id, id), eq(attachment.userId, userId)) });
  if (!row) throw new HTTPException(404, { message: "Attachment not found" });
  return row;
}

/** Called after the client's PUT to R2: verifies the object, then reads it in the background. */
export async function completeUpload(row: AttachmentRow) {
  if (row.status === "ready" || row.status === "processing") return row;
  if (row.kind === "link") throw new HTTPException(400, { message: "Links don't need uploading." });
  const head = await headObject(row.storageKey);
  if (!head || head.size === 0) throw new HTTPException(409, { message: "Upload not found. Please try again." });
  if (head.size > typeOf(row.mimeType).maxBytes) {
    await deleteObjects([row.storageKey]);
    throw new HTTPException(413, { message: "This file is too large." });
  }
  const [updated] = await db
    .update(attachment)
    .set({ status: "processing", sizeBytes: head.size, error: null })
    .where(eq(attachment.id, row.id))
    .returning();
  void processAttachment(updated);
  return updated;
}

async function extractText(row: AttachmentRow, bytes: Buffer) {
  if (row.mimeType === DOCX) return (await mammoth.extractRawText({ buffer: bytes })).value;
  return bytes.toString("utf8");
}

async function processLink(row: AttachmentRow) {
  const profile = await getProfile(row.userId);
  const url = new URL(row.url!);
  let title: string | null;
  let result;
  let extracted: string | undefined;
  if (row.mimeType === LINK_MIME.video) {
    title = await youtubeTitle(row.url!);
    result = await digestAttachment(
      { kind: "link", fileName: title ?? row.fileName, file: { fileUri: row.url!, mimeType: "video/*" } },
      profile,
    );
  } else {
    const page = await fetchPage(url);
    title = page.title;
    extracted = page.text;
    if (extracted.length < 80) throw new UnreadableError();
    result = await digestAttachment({ kind: "link", fileName: title ?? row.fileName, text: extracted }, profile);
  }
  if (!result.readable) throw new UnreadableError();
  const digest: AttachmentDigest = {
    label: result.label.trim() || (row.mimeType === LINK_MIME.video ? "video" : "article"),
    summary: result.summary,
    items: result.items,
    text: (result.text || extracted || "").slice(0, 8000),
    sectionCount: result.sectionCount || undefined,
  };
  await db
    .update(attachment)
    .set({ status: "ready", digest, error: null, fileName: (title ?? row.fileName).slice(0, 200) })
    .where(eq(attachment.id, row.id));
}

/** Reads the file once with Gemini and stores the digest used as context on later turns. */
async function processAttachment(row: AttachmentRow) {
  if (row.kind === "link") {
    try {
      await processLink(row);
    } catch (err) {
      console.error(`Link ${row.id} failed:`, err instanceof Error ? err.message : err);
      const unreadable = err instanceof UnreadableError || err instanceof UnreadableLinkError;
      await db
        .update(attachment)
        .set({ status: "failed", error: unreadable ? "Couldn't open this link" : "Couldn't read this link" })
        .where(eq(attachment.id, row.id));
    }
    return;
  }
  try {
    const bytes = await getObjectBytes(row.storageKey);
    const type = typeOf(row.mimeType);
    const profile = await getProfile(row.userId);
    const extracted = type.inline ? undefined : (await extractText(row, bytes)).trim();
    if (extracted !== undefined && !extracted) throw new UnreadableError();

    const result = await digestAttachment(
      {
        kind: row.kind,
        fileName: row.fileName,
        file: type.inline ? { mimeType: row.mimeType, data: bytes.toString("base64") } : undefined,
        text: extracted,
      },
      profile,
    );
    if (!result.readable) throw new UnreadableError();

    const digest: AttachmentDigest = {
      label: result.label.trim() || (row.kind === "image" ? "photo" : "document"),
      summary: result.summary,
      items: result.items,
      text: (extracted ?? result.text).slice(0, 8000),
      pageCount: result.pageCount || undefined,
      sectionCount: result.sectionCount || undefined,
    };
    await db.update(attachment).set({ status: "ready", digest, error: null }).where(eq(attachment.id, row.id));
  } catch (err) {
    console.error(`Attachment ${row.id} failed:`, err instanceof Error ? err.message : err);
    const error = err instanceof UnreadableError ? "We couldn't read this file" : "Couldn't process this file";
    await db.update(attachment).set({ status: "failed", error }).where(eq(attachment.id, row.id));
  }
}

class UnreadableError extends Error {}

/**
 * Resolves the attachments for a new message: they must belong to the user, be unsent,
 * and finish processing (waits briefly for files still being read).
 */
export async function attachmentsForSend(userId: string, ids: string[]) {
  const unique = [...new Set(ids)];
  if (unique.length === 0) return [];
  if (unique.length > MAX_ATTACHMENTS_PER_MESSAGE) {
    throw new HTTPException(400, { message: `You can attach up to ${MAX_ATTACHMENTS_PER_MESSAGE} files.` });
  }
  const deadline = Date.now() + 20_000;
  for (;;) {
    const rows = await db.query.attachment.findMany({
      where: and(
        eq(attachment.userId, userId),
        inArray(attachment.id, unique),
        isNull(attachment.messageId),
        isNull(attachment.topicId),
      ),
    });
    if (rows.length !== unique.length) throw new HTTPException(400, { message: "Some attachments are no longer available." });
    if (rows.some((row) => row.status === "failed" || row.status === "pending")) {
      throw new HTTPException(409, { message: "Some attachments didn't upload. Remove or retry them." });
    }
    if (rows.every((row) => row.status === "ready")) {
      return unique.map((id) => rows.find((row) => row.id === id)!);
    }
    if (Date.now() > deadline) throw new HTTPException(409, { message: "Still reading your files. Try again in a moment." });
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

/** Text stand-in for a file shared on an earlier turn. */
export function describeAttachment(row: AttachmentRow) {
  const digest = row.digest;
  const name = row.kind === "link" ? `${row.fileName}" (${row.url})` : `${row.fileName}"`;
  if (!digest) return `[Attachment "${name}]`;
  const lines = [`[Attachment "${name} — ${digest.label}: ${digest.summary}`];
  if (digest.items.length) lines.push(`Key items: ${digest.items.join("; ")}`);
  if (digest.text) lines.push(`Text: ${digest.text.slice(0, HISTORY_TEXT_PER_ATTACHMENT)}`);
  return `${lines.join("\n")}]`;
}

/** Full context for files on the turn they are sent: the file itself when Gemini can read it, else the extracted text. */
export async function filesForTurn(rows: AttachmentRow[]) {
  const files: GeminiFile[] = [];
  const notes: string[] = [];
  await Promise.all(
    rows.map(async (row, index) => {
      if (row.kind === "link") {
        const digest = row.digest;
        notes[index] = `[Shared link "${row.fileName}" (${row.url}) — ${digest?.label ?? "link"}: ${digest?.summary ?? ""}\nKey items: ${digest?.items.join("; ") ?? ""}\nContent:\n${digest?.text ?? ""}]`;
        return;
      }
      const type = typeOf(row.mimeType);
      if (type.inline && row.sizeBytes <= 15 * MB) {
        files[index] = { mimeType: row.mimeType, data: (await getObjectBytes(row.storageKey)).toString("base64") };
        notes[index] = `[Attached ${row.kind} "${row.fileName}"]`;
      } else {
        const digest = row.digest;
        notes[index] = `[Attached document "${row.fileName}" — ${digest?.label ?? "document"}: ${digest?.summary ?? ""}\nText:\n${digest?.text ?? ""}]`;
      }
    }),
  );
  return { files: files.filter(Boolean), notes: notes.filter(Boolean) };
}

/** Earlier images worth resending as pixels so follow-up questions about visual detail still work. */
export function recentImages(rows: AttachmentRow[]) {
  return rows.filter((row) => row.kind === "image" && row.status === "ready").slice(-RECENT_IMAGES_RESENT);
}

export async function loadImageFiles(rows: AttachmentRow[]) {
  const entries = await Promise.all(
    rows.map(async (row) => {
      try {
        const data = (await getObjectBytes(row.storageKey)).toString("base64");
        return [row.id, { mimeType: row.mimeType, data }] as const;
      } catch {
        return null;
      }
    }),
  );
  return new Map(entries.filter((entry) => entry !== null));
}

export async function serializeAttachment(row: AttachmentRow) {
  const isLink = row.kind === "link";
  return {
    id: row.id,
    kind: row.kind,
    mimeType: row.mimeType,
    fileName: row.fileName,
    sizeBytes: row.sizeBytes,
    width: row.width,
    height: row.height,
    status: row.status,
    error: row.error,
    url: isLink ? row.url : row.status === "pending" ? null : await presignDownload(row.storageKey, row.fileName),
    pageCount: row.digest?.pageCount ?? null,
    sectionCount: row.digest?.sectionCount ?? null,
  };
}

export type SerializedAttachment = Awaited<ReturnType<typeof serializeAttachment>>;

function storedKeys(rows: { key: string }[]) {
  return rows.map((row) => row.key).filter(Boolean);
}

export async function deleteAttachment(row: AttachmentRow) {
  if (row.messageId || row.topicId) throw new HTTPException(409, { message: "Sent attachments can't be removed." });
  await db.delete(attachment).where(eq(attachment.id, row.id));
  await deleteObjects(storedKeys([{ key: row.storageKey }]));
}

/** Call before deleting conversations; returns a function that removes their files from R2 afterwards. */
export async function collectConversationFiles(conversationIds: string[]) {
  if (conversationIds.length === 0) return async () => {};
  const rows = await db
    .select({ key: attachment.storageKey })
    .from(attachment)
    .where(inArray(attachment.conversationId, conversationIds));
  return () => deleteObjects(storedKeys(rows));
}

/** Call before deleting a topic; returns a function that removes its learning material from R2 afterwards. */
export async function collectTopicFiles(topicId: string) {
  const rows = await db.select({ key: attachment.storageKey }).from(attachment).where(eq(attachment.topicId, topicId));
  return () => deleteObjects(storedKeys(rows));
}

/**
 * Resolves learning material for a topic: the learner's own attachments, ready to use. Unsent drafts
 * and files already shared in a chat ("Turn into a topic") are both allowed.
 */
export async function materialForTopic(userId: string, ids: string[]) {
  const unique = [...new Set(ids)];
  if (unique.length === 0) return [];
  if (unique.length > MAX_TOPIC_MATERIAL) {
    throw new HTTPException(400, { message: `You can add up to ${MAX_TOPIC_MATERIAL} files.` });
  }
  const deadline = Date.now() + 20_000;
  for (;;) {
    const rows = await db.query.attachment.findMany({
      where: and(eq(attachment.userId, userId), inArray(attachment.id, unique)),
    });
    if (rows.length !== unique.length) throw new HTTPException(400, { message: "Some files are no longer available." });
    if (rows.some((row) => row.status === "failed" || row.status === "pending")) {
      throw new HTTPException(409, { message: "Some files didn't upload. Remove or retry them." });
    }
    if (rows.every((row) => row.status === "ready")) return unique.map((id) => rows.find((row) => row.id === id)!);
    if (Date.now() > deadline) throw new HTTPException(409, { message: "Still reading your files. Try again in a moment." });
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
}

/** Text version of learning material, used to plan a topic and as context in its lessons. */
export function materialNotes(rows: AttachmentRow[], maxChars = 24_000) {
  const perFile = Math.floor(maxChars / Math.max(rows.length, 1));
  return rows
    .map((row) => {
      const digest = row.digest;
      const lines = [`## ${row.fileName}${row.kind === "link" ? ` (${row.url})` : ""} — ${digest?.label ?? row.kind}`];
      if (digest?.summary) lines.push(digest.summary);
      if (digest?.items.length) lines.push(`Key items: ${digest.items.join("; ")}`);
      if (digest?.text) lines.push(digest.text);
      return lines.join("\n").slice(0, perFile);
    })
    .join("\n\n");
}

/** Keeps unsent material files with the topic, so they live (and are deleted) with it. */
export async function bindMaterial(topicId: string, rows: AttachmentRow[]) {
  const unsent = rows.filter((row) => !row.messageId && !row.topicId).map((row) => row.id);
  if (unsent.length) await db.update(attachment).set({ topicId }).where(inArray(attachment.id, unsent));
}

async function sweepOrphans() {
  const cutoff = new Date(Date.now() - ORPHAN_TTL_MS);
  const rows = await db
    .delete(attachment)
    .where(and(isNull(attachment.messageId), isNull(attachment.topicId), lt(attachment.createdAt, cutoff)))
    .returning({ key: attachment.storageKey });
  if (rows.length) {
    await deleteObjects(storedKeys(rows));
    console.log(`Swept ${rows.length} unsent attachment(s)`);
  }
}

/** Re-reads files interrupted by a restart and starts the hourly orphan sweep. */
export function startAttachmentJobs() {
  const resume = async () => {
    const stuck = await db.query.attachment.findMany({ where: eq(attachment.status, "processing") });
    for (const row of stuck) void processAttachment(row);
  };
  resume().catch((err) => console.error("Attachment resume failed:", err));
  sweepOrphans().catch((err) => console.error("Attachment sweep failed:", err));
  setInterval(() => sweepOrphans().catch((err) => console.error("Attachment sweep failed:", err)), 60 * 60 * 1000).unref();
}
