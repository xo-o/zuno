import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { z } from "zod";
import { parseBody, rateLimit, requireAuth, type AuthEnv } from "../lib/http.js";
import {
  declinePeerCall,
  deletePeerConversation,
  endPeerCall,
  findOrCreatePeerConversation,
  generateCoachPrompts,
  getChatsOverview,
  getPeerConversationDetail,
  joinPeerCall,
  pingPeerCall,
  reportPeerUser,
  sendPeerMessage,
  setOpenToSpeak,
  setPartner,
  startPeerCall,
  updatePeerConversation,
} from "../lib/peers.js";
import { resolveTutor } from "../lib/voices.js";
import { getProfile } from "../lib/profile.js";

const openToSpeakSchema = z.object({
  openToSpeak: z.boolean(),
});

const peerMessageSchema = z.object({
  text: z.string().trim().min(1).max(2000),
});

const peerUpdateSchema = z.object({
  muted: z.boolean(),
});

const peerReportSchema = z.object({
  reason: z.string().trim().min(1).max(60),
  details: z.string().trim().max(1000).optional(),
});

const startCallSchema = z.object({
  cameraEnabled: z.boolean().default(false),
});

const endCallSchema = z.object({
  durationSec: z.number().int().min(0).max(36_000).default(0),
  isVideo: z.boolean().default(false),
});

const coachPromptsSchema = z.object({
  mode: z.enum(["freetalk", "peer"]).optional(),
  targetLanguage: z.string().trim().min(2).max(16).optional(),
  contextLabel: z.string().trim().max(160).optional(),
  partnerName: z.string().trim().max(80).optional(),
  lastLine: z.string().trim().max(400).optional(),
});

const messageLimit = rateLimit({ windowMs: 60_000, max: 60 });

export const chatRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/overview", async (c) => {
    const overview = await getChatsOverview(c.get("user").id);
    return c.json(overview);
  })
  .patch("/open-to-speak", async (c) => {
    const { openToSpeak } = await parseBody(c, openToSpeakSchema);
    const updated = await setOpenToSpeak(c.get("user").id, openToSpeak);
    return c.json({ openToSpeak: updated });
  })
  .post("/partners/:partnerId", async (c) => {
    await setPartner(c.get("user").id, c.req.param("partnerId"), true);
    return c.json({ isPartner: true });
  })
  .delete("/partners/:partnerId", async (c) => {
    await setPartner(c.get("user").id, c.req.param("partnerId"), false);
    return c.json({ isPartner: false });
  })
  .post("/people/:partnerId/open", async (c) => {
    const userId = c.get("user").id;
    const partnerId = c.req.param("partnerId");
    if (partnerId === userId) throw new HTTPException(400, { message: "Cannot start a chat with yourself" });
    const conv = await findOrCreatePeerConversation(userId, partnerId);
    const detail = await getPeerConversationDetail(userId, conv.id);
    if (!detail) throw new HTTPException(404, { message: "Conversation not found" });
    return c.json(detail);
  })
  .get("/people/:conversationId", async (c) => {
    const detail = await getPeerConversationDetail(c.get("user").id, c.req.param("conversationId"));
    if (!detail) throw new HTTPException(404, { message: "Conversation not found" });
    return c.json(detail);
  })
  .post("/people/:conversationId/messages", messageLimit, async (c) => {
    const { text } = await parseBody(c, peerMessageSchema);
    const saved = await sendPeerMessage(c.get("user").id, c.req.param("conversationId"), text);
    if (!saved) throw new HTTPException(404, { message: "Conversation not found" });
    return c.json({ message: saved }, 201);
  })
  .patch("/people/:conversationId", async (c) => {
    const { muted } = await parseBody(c, peerUpdateSchema);
    const updated = await updatePeerConversation(c.get("user").id, c.req.param("conversationId"), muted);
    if (!updated) throw new HTTPException(404, { message: "Conversation not found" });
    return c.json(updated);
  })
  .post("/people/:conversationId/report", async (c) => {
    const body = await parseBody(c, peerReportSchema);
    const reported = await reportPeerUser(c.get("user").id, c.req.param("conversationId"), body.reason, body.details);
    if (!reported) throw new HTTPException(404, { message: "Conversation not found" });
    return c.json(reported, 201);
  })
  .delete("/people/:conversationId", async (c) => {
    const ok = await deletePeerConversation(c.get("user").id, c.req.param("conversationId"));
    if (!ok) throw new HTTPException(404, { message: "Conversation not found" });
    return c.body(null, 204);
  })
  .post("/people/:conversationId/call", async (c) => {
    const { cameraEnabled } = await parseBody(c, startCallSchema);
    const started = await startPeerCall(c.get("user").id, c.req.param("conversationId"), cameraEnabled);
    if (!started) throw new HTTPException(404, { message: "Conversation not found" });
    return c.json(started, 201);
  })
  .post("/calls/:callId/join", async (c) => {
    const joined = await joinPeerCall(c.get("user").id, c.req.param("callId"));
    if (!joined) throw new HTTPException(404, { message: "Call room not found" });
    return c.json(joined);
  })
  .post("/calls/:callId/decline", async (c) => {
    const declined = await declinePeerCall(c.get("user").id, c.req.param("callId"));
    if (!declined) throw new HTTPException(404, { message: "Call room not found" });
    return c.json({ call: declined });
  })
  .post("/calls/:callId/ping", async (c) => {
    const pinged = await pingPeerCall(c.get("user").id, c.req.param("callId"));
    if (!pinged) throw new HTTPException(404, { message: "Call room not found" });
    return c.json(pinged);
  })
  .post("/calls/:callId/end", async (c) => {
    const body = await parseBody(c, endCallSchema);
    const ended = await endPeerCall(c.get("user").id, c.req.param("callId"), body.durationSec, body.isVideo);
    if (!ended) throw new HTTPException(404, { message: "Call room not found" });
    return c.json(ended);
  })
  .post("/coach-prompts", async (c) => {
    const profile = await getProfile(c.get("user").id);
    const body = await parseBody(c, coachPromptsSchema);
    const tutor = resolveTutor(profile.tutorId, profile.tutorVoice);
    const prompts = await generateCoachPrompts({
      mode: body.mode,
      targetLanguage: body.targetLanguage ?? profile.targetLanguage,
      nativeLanguage: profile.nativeLanguage,
      level: profile.level,
      tutorName: tutor.name,
      contextLabel: body.contextLabel,
      partnerName: body.partnerName,
      lastLine: body.lastLine,
    });
    return c.json(prompts);
  });
