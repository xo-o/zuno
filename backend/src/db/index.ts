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
    await pool.query(`ALTER TABLE "topic" ADD COLUMN IF NOT EXISTS "level" text NOT NULL DEFAULT 'B1';`);
    await pool.query(`ALTER TABLE "roleplay" ADD COLUMN IF NOT EXISTS "level" text NOT NULL DEFAULT 'B1';`);
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
    await pool.query(`ALTER TABLE "live_room" ALTER COLUMN "host_user_id" DROP NOT NULL;`);
    await pool.query(`ALTER TABLE "live_room" ADD COLUMN IF NOT EXISTS "starter_phrases" jsonb NOT NULL DEFAULT '[]'::jsonb;`);
    await pool.query(`ALTER TABLE "live_room" ADD COLUMN IF NOT EXISTS "started_at" timestamp;`);
    await pool.query(`ALTER TABLE "live_room" ADD COLUMN IF NOT EXISTS "ended_at" timestamp;`);
    await pool.query(`ALTER TABLE "live_room_member" ADD COLUMN IF NOT EXISTS "last_seen_at" timestamp NOT NULL DEFAULT now();`);
    await pool.query(`ALTER TABLE "conversation" ADD COLUMN IF NOT EXISTS "unread_count" integer NOT NULL DEFAULT 0;`);
    await pool.query(`ALTER TABLE "conversation" ADD COLUMN IF NOT EXISTS "last_read_at" timestamp NOT NULL DEFAULT now();`);
    await pool.query(`ALTER TABLE "conversation" ADD COLUMN IF NOT EXISTS "tutor_id" text;`);
    await pool.query(`
      CREATE INDEX IF NOT EXISTS "user_profile_presence_idx"
      ON "user_profile" ("target_language", "open_to_speak", "last_seen_at" DESC);
    `);
    await pool.query(`
      CREATE TABLE IF NOT EXISTS "user_daily_activity" (
        "user_id" text NOT NULL REFERENCES "user"("id") ON DELETE CASCADE,
        "local_date" date NOT NULL,
        "language" text NOT NULL,
        "spoken_sec" integer NOT NULL DEFAULT 0,
        "messages_count" integer NOT NULL DEFAULT 0,
        "lessons_completed" integer NOT NULL DEFAULT 0,
        "vocab_reviewed" integer NOT NULL DEFAULT 0,
        "xp" integer NOT NULL DEFAULT 0,
        "updated_at" timestamp NOT NULL DEFAULT now(),
        PRIMARY KEY ("user_id", "local_date", "language")
      );
    `);
    await pool.query(`
      CREATE INDEX IF NOT EXISTS "user_daily_activity_user_date_idx"
      ON "user_daily_activity" ("user_id", "local_date" DESC);
    `);
    await pool.query(`
      CREATE INDEX IF NOT EXISTS "user_daily_activity_lang_date_xp_idx"
      ON "user_daily_activity" ("language", "local_date", "xp" DESC);
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

    // One-time idempotent cleanup of legacy synthetic seed:* accounts, fake peer threads, and fake live room members.
    await pool.query(`UPDATE "live_room" SET "host_user_id" = NULL WHERE "source_kind" = 'system' AND "host_user_id" LIKE 'seed:%';`);
    await pool.query(`DELETE FROM "live_room" WHERE "id" LIKE 'pub-food-%' AND ("host_user_id" LIKE 'seed:%' OR "host_user_id" IS NULL);`);
    await pool.query(`DELETE FROM "live_room_member" WHERE "user_id" LIKE 'seed:%';`);
    await pool.query(`DELETE FROM "user" WHERE "id" LIKE 'seed:%';`);

    // One-time historical backfill of user_daily_activity for existing real learners when the table is empty.
    const existingActivity = await pool.query(`SELECT 1 FROM "user_daily_activity" LIMIT 1;`);
    if ((existingActivity.rowCount ?? 0) === 0) {
      await pool.query(`
        WITH msg_daily AS (
          SELECT
            c.user_id,
            (m.created_at AT TIME ZONE 'UTC')::date AS local_date,
            c.language,
            COUNT(*) FILTER (WHERE m.role = 'user')::int AS messages_count,
            COALESCE(SUM(CASE WHEN m.kind = 'call_summary' THEN GREATEST(0, COALESCE((m.metadata->>'durationSec')::int, 0)) ELSE 0 END), 0)::int
              + (COUNT(*) FILTER (WHERE m.role = 'user' AND m.via_voice = true AND m.kind = 'text') * 15)::int AS spoken_sec
          FROM "message" m
          INNER JOIN "conversation" c ON c.id = m.conversation_id
          WHERE c.user_id NOT LIKE 'seed:%'
          GROUP BY c.user_id, (m.created_at AT TIME ZONE 'UTC')::date, c.language
        ),
        lesson_daily AS (
          SELECT
            le.user_id,
            (le.created_at AT TIME ZONE 'UTC')::date AS local_date,
            t.language,
            COUNT(*)::int AS lessons_completed,
            COALESCE(SUM(GREATEST(0, le.duration_sec)), 0)::int AS spoken_sec
          FROM "lesson_evaluation" le
          INNER JOIN "topic_lesson" tl ON tl.id = le.lesson_id
          INNER JOIN "topic" t ON t.id = tl.topic_id
          WHERE le.user_id NOT LIKE 'seed:%'
          GROUP BY le.user_id, (le.created_at AT TIME ZONE 'UTC')::date, t.language
        ),
        combined AS (
          SELECT
            COALESCE(m.user_id, l.user_id) AS user_id,
            COALESCE(m.local_date, l.local_date) AS local_date,
            COALESCE(m.language, l.language) AS language,
            GREATEST(COALESCE(m.spoken_sec, 0), COALESCE(l.spoken_sec, 0))::int AS spoken_sec,
            COALESCE(m.messages_count, 0)::int AS messages_count,
            COALESCE(l.lessons_completed, 0)::int AS lessons_completed
          FROM msg_daily m
          FULL OUTER JOIN lesson_daily l
            ON m.user_id = l.user_id AND m.local_date = l.local_date AND m.language = l.language
        )
        INSERT INTO "user_daily_activity" ("user_id", "local_date", "language", "spoken_sec", "messages_count", "lessons_completed", "vocab_reviewed", "xp")
        SELECT
          user_id,
          local_date,
          language,
          spoken_sec,
          messages_count,
          lessons_completed,
          0,
          (ROUND(spoken_sec / 6.0)::int + messages_count * 5 + lessons_completed * 50)
        FROM combined
        WHERE spoken_sec > 0 OR messages_count > 0 OR lessons_completed > 0
        ON CONFLICT ("user_id", "local_date", "language") DO NOTHING;
      `);
    }
  } catch (err) {
    console.warn("Runtime schema check warning:", err);
  }
}
