import { Hono } from "hono";
import { getWeeklyLeague } from "../lib/activity.js";
import { requireAuth, type AuthEnv } from "../lib/http.js";
import { getProfile } from "../lib/profile.js";

export const communityRoutes = new Hono<AuthEnv>()
  .use(requireAuth)
  .get("/league", async (c) => {
    const userId = c.get("user").id;
    const profile = await getProfile(userId);
    const league = await getWeeklyLeague(userId, profile.targetLanguage);
    return c.json(league);
  });
