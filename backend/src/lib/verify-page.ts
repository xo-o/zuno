import { escapeHtml } from "./email.js";

const STYLE = `
    body { margin: 0; padding: 24px; background: #0b0b0c; color: #fff; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; display: flex; justify-content: center; align-items: center; min-height: 90vh; }
    .card { background: #161618; border: 1px solid #2a2a2d; border-radius: 24px; padding: 40px 32px; max-width: 440px; width: 100%; text-align: center; box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5); }
    h1 { margin: 0 0 12px; font-size: 24px; font-weight: 700; letter-spacing: -0.02em; }
    p { color: #a1a1aa; font-size: 15px; line-height: 1.6; margin: 0 0 24px; }
    .highlight { color: #fff; font-weight: 600; }
    .actions { display: flex; flex-direction: column; gap: 12px; align-items: center; }
    .button { display: inline-block; border: 0; cursor: pointer; font: inherit; font-weight: 600; font-size: 15px; padding: 14px 28px; border-radius: 9999px; text-decoration: none; }
    .primary { background: #fff; color: #000; }
    .secondary { background: transparent; color: #fff; border: 1px solid #3f3f46; }
    .hint { font-size: 12px; color: #71717a; margin-top: 24px; margin-bottom: 0; }`;

function page(title: string, body: string, script = "") {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="robots" content="noindex">
  <title>${escapeHtml(title)}</title>
  <style>${STYLE}
  </style>
</head>
<body>
  <div class="card">
${body}
  </div>${script}
</body>
</html>`;
}

/** Deep link that hands the (still unused) token to the app, which redeems it itself. */
export function appVerifyUrl(token: string) {
  return `zuno://auth/verify?token=${encodeURIComponent(token)}`;
}

/**
 * Landing page for a magic link opened in a browser. Opening it never redeems the token, so
 * link scanners and prefetchers can't spend it or sign anyone in. On a phone it forwards to the
 * app; signing in a waiting phone from another device takes an explicit confirmation (POST).
 */
export function renderVerifyLandingPage(options: { token: string; flowId?: string; email?: string }) {
  const appUrl = escapeHtml(appVerifyUrl(options.token));
  const confirm = options.flowId
    ? `
    <form method="post" action="/auth/verify">
      <input type="hidden" name="token" value="${escapeHtml(options.token)}">
      <input type="hidden" name="flowId" value="${escapeHtml(options.flowId)}">
      <button class="button primary" type="submit">Confirm sign-in on my phone</button>
    </form>`
    : "";
  const account = options.email ? ` as <span class="highlight">${escapeHtml(options.email)}</span>` : "";
  return page(
    "Sign in to Zuno",
    `    <h1>Sign in to Zuno</h1>
    <p>${options.flowId ? `Confirm only if you just asked to sign in to Zuno on your phone${account}. If you didn't, ignore this link.` : "Open this link on the phone where Zuno is installed to finish signing in."}</p>
    <div class="actions">${confirm}
      <a class="button ${options.flowId ? "secondary" : "primary"}" href="${appUrl}">Open Zuno on this device</a>
    </div>`,
    `
  <script>
    if (/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) {
      window.location.href = ${JSON.stringify(appVerifyUrl(options.token))};
    }
  </script>`,
  );
}

export function renderVerifyResultPage(heading: string, body: string) {
  return page(`${heading} · Zuno`, `    <h1>${escapeHtml(heading)}</h1>
    <p>${escapeHtml(body)}</p>
    <p class="hint">You can close this window.</p>`);
}
