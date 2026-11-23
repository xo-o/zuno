import { drizzle } from "drizzle-orm/node-postgres";
import pg from "pg";
import * as appSchema from "./app-schema.js";
import * as authSchema from "./auth-schema.js";

const { Pool } = pg;
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

export const db = drizzle(pool, { schema: { ...authSchema, ...appSchema } });

export async function ensureRuntimeSchema(): Promise<void> {
  if (!process.env.DATABASE_URL) return;
  try {
    await pool.query(`ALTER TABLE "roleplay" ADD COLUMN IF NOT EXISTS "goals" jsonb;`);
    await pool.query(`ALTER TABLE "user_profile" ADD COLUMN IF NOT EXISTS "tutor_id" text NOT NULL DEFAULT 'sofia';`);
    await pool.query(`
      UPDATE "user_profile"
      SET "tutor_id" = CASE "tutor_voice"
        WHEN 'calm' THEN 'valeria'
        WHEN 'crisp' THEN 'claire'
        WHEN 'energetic' THEN 'mateo'
        WHEN 'steady' THEN 'lucas'
        WHEN 'casual' THEN 'ethan'
        ELSE 'sofia'
      END
      WHERE "tutor_id" = 'sofia' AND "tutor_voice" <> 'warm';
    `);
  } catch (err) {
    console.warn("Runtime schema check warning:", err);
  }
}
