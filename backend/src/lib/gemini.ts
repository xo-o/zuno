import { z } from "zod";

const API_BASE = "https://generativelanguage.googleapis.com/v1beta/models";
const DEFAULT_MODEL = "gemini-3.8-flash";
const REQUEST_TIMEOUT_MS = 30_000;

export class GeminiError extends Error {
  constructor(message: string, readonly status?: number) {
    super(message);
    this.name = "GeminiError";
  }
}

export type GeminiTurn = { role: "user" | "model"; text: string };

type GenerateOptions<T extends z.ZodType> = {
  system: string;
  /** Either a single prompt or a full conversation history. */
  contents: string | GeminiTurn[];
  schema: T;
  thinking?: "low" | "medium" | "high";
  temperature?: number;
};

/**
 * Calls Gemini with a JSON schema and validates the reply with the matching zod schema,
 * so callers always get typed, well-formed data or a GeminiError.
 */
export async function generateJson<T extends z.ZodType>(options: GenerateOptions<T>): Promise<z.infer<T>> {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) throw new GeminiError("GEMINI_API_KEY is not configured");

  const model = process.env.GEMINI_MODEL || DEFAULT_MODEL;
  const contents =
    typeof options.contents === "string"
      ? [{ role: "user", parts: [{ text: options.contents }] }]
      : options.contents.map((turn) => ({ role: turn.role, parts: [{ text: turn.text }] }));

  const body = {
    systemInstruction: { parts: [{ text: options.system }] },
    contents,
    generationConfig: {
      temperature: options.temperature ?? 0.7,
      thinkingConfig: { thinkingLevel: options.thinking ?? "low" },
      responseMimeType: "application/json",
      responseJsonSchema: z.toJSONSchema(options.schema, { target: "draft-7" }),
    },
  };

  const response = await fetch(`${API_BASE}/${model}:generateContent`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "x-goog-api-key": apiKey },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  }).catch((err: unknown) => {
    throw new GeminiError(`Gemini request failed: ${err instanceof Error ? err.message : String(err)}`);
  });

  if (!response.ok) {
    const detail = await response.text().catch(() => "");
    throw new GeminiError(`Gemini returned ${response.status}: ${detail.slice(0, 300)}`, response.status);
  }

  const payload = (await response.json()) as {
    candidates?: { content?: { parts?: { text?: string; thought?: boolean }[] }; finishReason?: string }[];
  };
  const text = payload.candidates?.[0]?.content?.parts
    ?.filter((part) => !part.thought && part.text)
    .map((part) => part.text)
    .join("");
  if (!text) {
    throw new GeminiError(`Gemini returned no content (${payload.candidates?.[0]?.finishReason ?? "unknown"})`);
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new GeminiError("Gemini returned malformed JSON");
  }
  const result = options.schema.safeParse(parsed);
  if (!result.success) throw new GeminiError(`Gemini reply failed validation: ${result.error.message}`);
  return result.data;
}
