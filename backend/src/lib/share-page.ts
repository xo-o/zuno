import { escapeHtml } from "./email.js";
import { languageName } from "./languages.js";
import type { MessageWithAttachments } from "./conversations.js";

const KIND_LABELS = { freetalk: "Free Talk", roleplay: "Roleplay", lesson: "Lesson" } as const;

function attachmentNote(message: MessageWithAttachments) {
  if (!message.attachments.length) return "";
  const names = message.attachments.map((a) => escapeHtml(a.kind === "link" ? a.fileName : a.kind === "image" ? "Photo" : a.fileName));
  return `<div class="files">📎 ${names.join(" · ")}</div>`;
}

/** Read-only page for a chat shared with "Public access". Attachments are listed by name only. */
export function renderSharePage(options: {
  title: string;
  kind: keyof typeof KIND_LABELS;
  language: string;
  messages: MessageWithAttachments[];
  url: string;
}) {
  const title = escapeHtml(options.title);
  const subtitle = `${KIND_LABELS[options.kind]} · ${escapeHtml(languageName(options.language))}`;
  const first = options.messages.find((m) => m.role === "assistant")?.text ?? options.messages[0]?.text ?? "";
  const description = escapeHtml(first.length > 160 ? `${first.slice(0, 157)}…` : first || "A conversation practised with Zuno.");
  const turns = options.messages
    .map((m) => {
      if (m.role === "user") {
        return `<div class="turn you"><div class="who">Learner</div>${attachmentNote(m)}${m.text ? `<div class="bubble you">${escapeHtml(m.text)}</div>` : ""}${
          m.feedback ? `<div class="feedback">✓ ${escapeHtml(m.feedback)}</div>` : ""
        }</div>`;
      }
      return `<div class="turn zuno"><div class="who">ZunoAI</div><div class="bubble zuno"><div class="reply">${escapeHtml(m.text)}</div>${
        m.translation ? `<div class="rule"></div><div class="translation">${escapeHtml(m.translation)}</div>` : ""
      }</div></div>`;
    })
    .join("\n");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title} · Zuno</title>
  <meta name="description" content="${description}">
  <meta property="og:type" content="article">
  <meta property="og:site_name" content="Zuno">
  <meta property="og:title" content="${title}">
  <meta property="og:description" content="${description}">
  <meta property="og:url" content="${escapeHtml(options.url)}">
  <meta name="twitter:card" content="summary">
  <meta name="robots" content="noindex">
  <style>
    * { box-sizing: border-box; }
    body { margin: 0; background: #121212; color: rgba(255,255,255,.92); font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; }
    main { max-width: 640px; margin: 0 auto; padding: 32px 20px 64px; }
    header { margin-bottom: 28px; }
    h1 { font-size: 24px; letter-spacing: -.02em; margin: 0 0 6px; }
    .sub { color: rgba(255,255,255,.44); font-size: 13px; }
    .turn { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; }
    .turn.you { align-items: flex-end; }
    .who { font-size: 11px; color: rgba(255,255,255,.44); letter-spacing: .02em; }
    .bubble { padding: 12px 16px; font-size: 15px; line-height: 22px; white-space: pre-wrap; }
    .bubble.you { background: rgba(255,255,255,.1); border: 1px solid rgba(255,255,255,.06); border-radius: 20px 20px 6px 20px; max-width: 85%; }
    .bubble.zuno { background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.08); border-radius: 6px 20px 20px 20px; width: 100%; }
    .reply { font-weight: 500; }
    .rule { height: 1px; background: rgba(255,255,255,.06); margin: 12px 0; }
    .translation { color: rgba(255,255,255,.64); font-size: 14px; line-height: 20px; }
    .feedback { font-size: 12px; color: rgba(255,255,255,.64); background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.06); border-radius: 12px; padding: 8px 12px; }
    .files { font-size: 12px; color: rgba(255,255,255,.44); }
    footer { margin-top: 40px; text-align: center; color: rgba(255,255,255,.44); font-size: 13px; }
    footer a { color: rgba(255,255,255,.92); }
  </style>
</head>
<body>
  <main>
    <header>
      <h1>${title}</h1>
      <div class="sub">${subtitle}</div>
    </header>
    ${turns || '<p class="sub">This conversation has no messages yet.</p>'}
    <footer>Practised with <a href="https://heyzuno.app">Zuno</a>, your AI language tutor.</footer>
  </main>
</body>
</html>`;
}

export function renderNotFoundPage() {
  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Not found · Zuno</title><meta name="robots" content="noindex">
<style>body{margin:0;background:#121212;color:rgba(255,255,255,.92);font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center;padding:24px}p{color:rgba(255,255,255,.64)}</style></head>
<body><div><h1>This chat isn’t available</h1><p>The link is wrong, or its owner stopped sharing it.</p></div></body></html>`;
}

export function renderMessagePage(heading: string, body: string) {
  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Zuno</title>
<style>body{margin:0;background:#121212;color:rgba(255,255,255,.92);font-family:-apple-system,BlinkMacSystemFont,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh;text-align:center;padding:24px}p{color:rgba(255,255,255,.64)}</style></head>
<body><div><h1>${escapeHtml(heading)}</h1><p>${escapeHtml(body)}</p></div></body></html>`;
}
