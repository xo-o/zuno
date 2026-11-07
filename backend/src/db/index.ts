import { drizzle } from "drizzle-orm/node-postgres";
import pg from "pg";
import * as appSchema from "./app-schema.js";
import * as authSchema from "./auth-schema.js";

const { Pool } = pg;
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

export const db = drizzle(pool, { schema: { ...authSchema, ...appSchema } });
