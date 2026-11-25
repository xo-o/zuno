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
    await pool.query(`ALTER TABLE "user_profile" ADD COLUMN IF NOT EXISTS "allow_call_invites" boolean NOT NULL DEFAULT true;`);
    await pool.query(`ALTER TABLE "user_profile" ADD COLUMN IF NOT EXISTS "who_can_message" text NOT NULL DEFAULT 'partners';`);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS "blocked_learner" (
        "user_id" text NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
        "blocked_user_id" text NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
        "created_at" timestamp NOT NULL DEFAULT now(),
        PRIMARY KEY ("user_id", "blocked_user_id")
      );
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS "vocab_phrase" (
        "id" text PRIMARY KEY,
        "user_id" text NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
        "language" text NOT NULL,
        "phrase" text NOT NULL,
        "translation" text NOT NULL,
        "source" text NOT NULL DEFAULT 'Added manually',
        "context_note" text,
        "example_sentence" text,
        "example_translation" text,
        "syllable_stress" text,
        "recall_cue" text,
        "streak" integer NOT NULL DEFAULT 0,
        "interval_days" integer NOT NULL DEFAULT 0,
        "bucket" text NOT NULL DEFAULT 'due',
        "due_at" timestamp NOT NULL DEFAULT now(),
        "created_at" timestamp NOT NULL DEFAULT now(),
        "updated_at" timestamp NOT NULL DEFAULT now()
      );
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS "live_room" (
        "id" text PRIMARY KEY,
        "host_user_id" text NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
        "title" text NOT NULL,
        "description" text NOT NULL DEFAULT '',
        "language" text NOT NULL,
        "level" text NOT NULL DEFAULT 'B1',
        "max_members" integer NOT NULL DEFAULT 4,
        "visibility" text NOT NULL DEFAULT 'public',
        "code" text UNIQUE,
        "source_kind" text NOT NULL DEFAULT 'user',
        "status" text NOT NULL DEFAULT 'lobby',
        "livekit_room" text NOT NULL UNIQUE,
        "discussion_prompts" jsonb NOT NULL DEFAULT '[]'::jsonb,
        "current_round" integer NOT NULL DEFAULT 1,
        "created_at" timestamp NOT NULL DEFAULT now(),
        "updated_at" timestamp NOT NULL DEFAULT now()
      );
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS "live_room_member" (
        "room_id" text NOT NULL REFERENCES "live_room"("id") ON DELETE CASCADE,
        "user_id" text NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
        "role" text NOT NULL DEFAULT 'member',
        "ready" boolean NOT NULL DEFAULT true,
        "joined_at" timestamp NOT NULL DEFAULT now(),
        PRIMARY KEY ("room_id", "user_id")
      );
    `);
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
