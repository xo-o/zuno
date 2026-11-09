import { randomUUID } from "node:crypto";
import { and, eq, sql } from "drizzle-orm";
import { job } from "../db/app-schema.js";
import { db } from "../db/index.js";

export type JobRow = typeof job.$inferSelect;
type Handler = (payload: Record<string, unknown>, row: JobRow) => Promise<void>;
type Sweep = () => Promise<void>;

const POLL_INTERVAL_MS = 30_000;
const BATCH_SIZE = 25;
const MAX_ATTEMPTS = 5;
/** A job left "running" this long (crashed instance) is picked up again. */
const STALE_LOCK_MS = 10 * 60 * 1000;

const handlers = new Map<string, Handler>();
const sweeps: Sweep[] = [];

export function registerJob(type: string, handler: Handler) {
  handlers.set(type, handler);
}

/** Runs on every poll, before due jobs are claimed (e.g. enqueue today's daily reminders). */
export function registerSweep(sweep: Sweep) {
  sweeps.push(sweep);
}

/**
 * Queues a job. With a [dedupeKey], enqueueing the same key again is a no-op, so callers can
 * enqueue idempotently. Returns the job id, or null when the key already existed.
 */
export async function enqueue(type: string, runAt: Date, payload: Record<string, unknown> = {}, dedupeKey?: string) {
  const [row] = await db
    .insert(job)
    .values({ id: randomUUID(), type, runAt, payload, dedupeKey: dedupeKey ?? null })
    .onConflictDoNothing({ target: job.dedupeKey })
    .returning({ id: job.id });
  return row?.id ?? null;
}

export async function cancelJob(dedupeKey: string) {
  await db
    .update(job)
    .set({ status: "cancelled" })
    .where(and(eq(job.dedupeKey, dedupeKey), eq(job.status, "pending")));
}

async function claim(): Promise<JobRow[]> {
  const result = await db.execute<Record<string, unknown>>(sql`
    update ${job} set status = 'running', locked_at = (now() at time zone 'utc'), attempts = attempts + 1
    where id in (
      select id from ${job}
      where (status = 'pending' and run_at <= (now() at time zone 'utc'))
         or (status = 'running' and locked_at < (now() at time zone 'utc') - make_interval(secs => ${STALE_LOCK_MS / 1000}))
      order by run_at
      limit ${BATCH_SIZE}
      for update skip locked
    )
    returning id, type, payload, attempts, run_at, dedupe_key
  `);
  return result.rows.map((r) => ({
    id: r.id as string,
    type: r.type as string,
    payload: (r.payload ?? {}) as Record<string, unknown>,
    attempts: Number(r.attempts),
    runAt: new Date(r.run_at as string),
    dedupeKey: (r.dedupe_key as string | null) ?? null,
    status: "running",
    lockedAt: new Date(),
    lastError: null,
    createdAt: new Date(),
  }));
}

async function run(row: JobRow) {
  const handler = handlers.get(row.type);
  try {
    if (!handler) throw new Error(`No handler for job type "${row.type}"`);
    await handler(row.payload, row);
    await db.update(job).set({ status: "done", lockedAt: null, lastError: null }).where(eq(job.id, row.id));
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error(`Job ${row.type} ${row.id} failed (attempt ${row.attempts}):`, message);
    const giveUp = row.attempts >= MAX_ATTEMPTS || !handler;
    // Exponential backoff: 1, 2, 4, 8 minutes.
    const retryAt = new Date(Date.now() + 60_000 * 2 ** (row.attempts - 1));
    await db
      .update(job)
      .set({ status: giveUp ? "failed" : "pending", lockedAt: null, lastError: message.slice(0, 1000), runAt: retryAt })
      .where(eq(job.id, row.id));
  }
}

let polling = false;

async function poll() {
  if (polling) return;
  polling = true;
  try {
    for (const sweep of sweeps) {
      await sweep().catch((err) => console.error("Job sweep failed:", err));
    }
    for (;;) {
      const batch = await claim();
      for (const row of batch) await run(row);
      if (batch.length < BATCH_SIZE) break;
    }
  } catch (err) {
    console.error("Job poll failed:", err);
  } finally {
    polling = false;
  }
}

export function startJobRunner() {
  void poll();
  setInterval(() => void poll(), POLL_INTERVAL_MS).unref();
}

/** Lets request handlers trigger an immediate poll (e.g. a reminder due in under a minute). */
export function pollSoon() {
  setTimeout(() => void poll(), 1000).unref();
}
