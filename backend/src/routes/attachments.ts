import { Hono } from "hono";
import { z } from "zod";
import {
  completeUpload,
  createLink,
  createUpload,
  deleteAttachment,
  findOwnAttachment,
  serializeAttachment,
} from "../lib/attachments.js";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";

const createSchema = z.object({
  fileName: z.string().trim().min(1).max(255),
  mimeType: z.string().trim().min(1).max(120),
  sizeBytes: z.number().int().positive(),
  width: z.number().int().positive().max(20_000).optional(),
  height: z.number().int().positive().max(20_000).optional(),
});

const linkSchema = z.object({ url: z.string().trim().min(4).max(2048) });

const uploadLimit = rateLimit({ windowMs: 60_000, max: 20 });

/**
 * Uploads go straight from the app to R2: create → PUT to `uploadUrl` → complete.
 * Zuno then reads the file in the background; poll GET until status is ready or failed.
 */
export const attachmentRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .post("/", uploadLimit, async (c) => {
    const body = await parseBody(c, createSchema);
    const { row, uploadUrl } = await createUpload(c.get("user").id, body);
    return c.json(
      { attachment: await serializeAttachment(row), uploadUrl, uploadHeaders: { "Content-Type": row.mimeType } },
      201,
    );
  })
  /** A pasted article or YouTube link; read in the background like files (poll GET until ready). */
  .post("/link", uploadLimit, async (c) => {
    const { url } = await parseBody(c, linkSchema);
    return c.json({ attachment: await serializeAttachment(await createLink(c.get("user").id, url)) }, 201);
  })
  .post("/:id/complete", async (c) => {
    const row = await findOwnAttachment(c.get("user").id, c.req.param("id"));
    return c.json({ attachment: await serializeAttachment(await completeUpload(row)) });
  })
  .get("/:id", async (c) => {
    const row = await findOwnAttachment(c.get("user").id, c.req.param("id"));
    return c.json({ attachment: await serializeAttachment(row) });
  })
  .delete("/:id", async (c) => {
    const row = await findOwnAttachment(c.get("user").id, c.req.param("id"));
    await deleteAttachment(row);
    return c.body(null, 204);
  });
