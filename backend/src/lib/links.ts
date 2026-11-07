import { lookup } from "node:dns/promises";
import { isIP } from "node:net";
import { HTTPException } from "hono/http-exception";

const MAX_PAGE_BYTES = 3 * 1024 * 1024;
const MAX_REDIRECTS = 4;
const FETCH_TIMEOUT_MS = 12_000;
const USER_AGENT = "Mozilla/5.0 (compatible; ZunoBot/1.0; +https://usezuno.rest)";

/** Validates a pasted link; only public http(s) URLs are accepted. */
export function parseLink(raw: string): URL {
  let url: URL;
  try {
    url = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(raw.trim()) ? raw.trim() : `https://${raw.trim()}`);
  } catch {
    throw new HTTPException(400, { message: "That doesn't look like a link." });
  }
  if (url.protocol !== "https:" && url.protocol !== "http:") {
    throw new HTTPException(400, { message: "Only web links (http or https) are supported." });
  }
  if (url.username || url.password) throw new HTTPException(400, { message: "Links with passwords aren't supported." });
  url.hash = "";
  return url;
}

const YOUTUBE_HOSTS = new Set(["youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be"]);

/** Canonical watch URL for YouTube links (which Gemini can watch directly), else null. */
export function youtubeUrl(url: URL): string | null {
  if (!YOUTUBE_HOSTS.has(url.hostname)) return null;
  let id: string | null = null;
  if (url.hostname === "youtu.be") id = url.pathname.slice(1).split("/")[0] || null;
  else if (url.pathname === "/watch") id = url.searchParams.get("v");
  else {
    const match = url.pathname.match(/^\/(?:shorts|embed|live)\/([\w-]{6,})/);
    id = match?.[1] ?? null;
  }
  return id && /^[\w-]{6,20}$/.test(id) ? `https://www.youtube.com/watch?v=${id}` : null;
}

export async function youtubeTitle(watchUrl: string): Promise<string | null> {
  try {
    const response = await fetch(`https://www.youtube.com/oembed?format=json&url=${encodeURIComponent(watchUrl)}`, {
      signal: AbortSignal.timeout(8_000),
    });
    if (!response.ok) return null;
    const body = (await response.json()) as { title?: string };
    return body.title?.trim() || null;
  } catch {
    return null;
  }
}

function isPrivateAddress(address: string): boolean {
  if (isIP(address) === 4) {
    const [a, b] = address.split(".").map(Number);
    return (
      a === 0 ||
      a === 10 ||
      a === 127 ||
      (a === 100 && b >= 64 && b <= 127) ||
      (a === 169 && b === 254) ||
      (a === 172 && b >= 16 && b <= 31) ||
      (a === 192 && b === 168) ||
      (a === 198 && (b === 18 || b === 19)) ||
      a >= 224
    );
  }
  const lower = address.toLowerCase();
  if (lower.startsWith("::ffff:")) return isPrivateAddress(lower.slice(7));
  return lower === "::" || lower === "::1" || lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80");
}

/** Refuses hosts that resolve to loopback, private or link-local addresses (SSRF guard). */
async function assertPublicHost(url: URL) {
  const host = url.hostname.replace(/^\[|\]$/g, "");
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".internal")) throw new UnreadableLinkError();
  const addresses = isIP(host) ? [{ address: host }] : await lookup(host, { all: true }).catch(() => []);
  if (addresses.length === 0 || addresses.some(({ address }) => isPrivateAddress(address))) throw new UnreadableLinkError();
}

export class UnreadableLinkError extends Error {}

/** Fetches a public web page (following redirects safely) and returns its title and readable text. */
export async function fetchPage(url: URL): Promise<{ title: string | null; text: string }> {
  let current = url;
  for (let hop = 0; ; hop++) {
    await assertPublicHost(current);
    const response = await fetch(current, {
      redirect: "manual",
      headers: { "User-Agent": USER_AGENT, Accept: "text/html,text/plain;q=0.9,*/*;q=0.1" },
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
    }).catch(() => {
      throw new UnreadableLinkError();
    });
    const location = response.headers.get("location");
    if (response.status >= 300 && response.status < 400 && location) {
      if (hop >= MAX_REDIRECTS) throw new UnreadableLinkError();
      current = new URL(location, current);
      if (current.protocol !== "https:" && current.protocol !== "http:") throw new UnreadableLinkError();
      continue;
    }
    if (!response.ok || !response.body) throw new UnreadableLinkError();
    const type = response.headers.get("content-type") ?? "";
    if (!/text\/html|application\/xhtml|text\/plain/i.test(type)) throw new UnreadableLinkError();
    const raw = await readCapped(response.body, MAX_PAGE_BYTES);
    if (/text\/plain/i.test(type)) return { title: null, text: raw.trim() };
    return htmlToText(raw);
  }
}

async function readCapped(body: ReadableStream<Uint8Array>, limit: number) {
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    total += value.byteLength;
    if (total >= limit) {
      await reader.cancel();
      break;
    }
  }
  return Buffer.concat(chunks).toString("utf8");
}

const ENTITIES: Record<string, string> = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'", nbsp: " " };

function decodeEntities(text: string) {
  return text.replace(/&(#x?[0-9a-f]+|[a-z]+);/gi, (match, code: string) => {
    if (code[0] === "#") {
      const value = code[1].toLowerCase() === "x" ? parseInt(code.slice(2), 16) : parseInt(code.slice(1), 10);
      return Number.isFinite(value) && value > 0 && value < 0x110000 ? String.fromCodePoint(value) : match;
    }
    return ENTITIES[code.toLowerCase()] ?? match;
  });
}

/** Rough readable-text extraction: prefers <article>/<main>, drops scripts, navigation and markup. */
export function htmlToText(html: string): { title: string | null; text: string } {
  const title =
    html.match(/<meta[^>]+property=["']og:title["'][^>]*content=["']([^"']+)["']/i)?.[1] ??
    html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] ??
    null;
  const body = html.match(/<article[\s\S]*?<\/article>/i)?.[0] ?? html.match(/<main[\s\S]*?<\/main>/i)?.[0] ?? html;
  const text = body
    .replace(/<(script|style|noscript|svg|nav|header|footer|aside|form|template)[\s\S]*?<\/\1>/gi, " ")
    .replace(/<!--[\s\S]*?-->/g, " ")
    .replace(/<(br|\/p|\/div|\/li|\/h[1-6]|\/tr|\/section)[^>]*>/gi, "\n")
    .replace(/<[^>]+>/g, " ");
  return {
    title: title ? decodeEntities(title).replace(/\s+/g, " ").trim().slice(0, 200) || null : null,
    text: decodeEntities(text)
      .replace(/[ \t\f\v]+/g, " ")
      .replace(/\s*\n\s*/g, "\n")
      .replace(/\n{3,}/g, "\n\n")
      .trim(),
  };
}
