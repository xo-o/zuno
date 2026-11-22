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
  } catch (err) {
    console.warn("Runtime schema check warning:", err);
  }
}
