import { escapeHtml } from "./email.js";

const ZUNO_MARK_SVG = `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
  <circle cx="9.5" cy="9.5" r="4.5" stroke="currentColor" stroke-width="1.8"/>
  <circle cx="14.5" cy="9.5" r="4.5" stroke="currentColor" stroke-width="1.8"/>
  <circle cx="9.5" cy="14.5" r="4.5" stroke="currentColor" stroke-width="1.8"/>
  <circle cx="14.5" cy="14.5" r="4.5" stroke="currentColor" stroke-width="1.8"/>
</svg>`;

const ICON_MAIL = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect width="20" height="16" x="2" y="4" rx="2"/><path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"/></svg>`;
const ICON_CHECK = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>`;
const ICON_ALERT = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;

const STYLE = `
    * { box-sizing: border-box; }
    body {
      margin: 0;
      padding: 20px;
      background: #121212;
      color: rgba(255, 255, 255, 0.92);
      font-family: 'Lexend', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      align-items: center;
    }
    .shell { width: 100%; max-width: 420px; flex: 1; display: flex; flex-direction: column; justify-content: space-between; gap: 20px; }
    .header { display: flex; align-items: center; justify-content: space-between; padding-top: 4px; }
    .brand { display: inline-flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 500; letter-spacing: -0.01em; color: rgba(255, 255, 255, 0.92); }
    .chip { display: inline-flex; align-items: center; padding: 5px 12px; border-radius: 9999px; font-size: 12px; font-weight: 500; color: rgba(255, 255, 255, 0.64); background: rgba(255, 255, 255, 0.04); border: 1px solid rgba(255, 255, 255, 0.08); }
    .chip.filled { background: rgba(255, 255, 255, 0.10); color: rgba(255, 255, 255, 0.92); }
    .card {
      background: #171717;
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 18px;
      padding: 24px;
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }
    .badge {
      width: 56px; height: 56px; border-radius: 9999px;
      display: inline-flex; align-items: center; justify-content: center;
      background: rgba(255, 255, 255, 0.06);
      border: 1px solid rgba(255, 255, 255, 0.08);
      color: rgba(255, 255, 255, 0.92);
    }
    .badge.danger { color: #ff8b8b; }
    h1 { margin: 0; font-size: 24px; line-height: 30px; font-weight: 600; letter-spacing: -0.03em; color: rgba(255, 255, 255, 0.92); }
    .sub { margin: 8px 0 0; color: rgba(255, 255, 255, 0.64); font-size: 14px; line-height: 20px; }
    .strip {
      display: flex; align-items: center; justify-content: space-between; gap: 10px;
      background: rgba(255, 255, 255, 0.04);
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 14px;
      padding: 12px 14px;
    }
    .strip-label { font-size: 11px; line-height: 14px; font-weight: 500; letter-spacing: 0.04em; color: rgba(255, 255, 255, 0.44); }
    .strip-email { font-size: 15px; line-height: 22px; font-weight: 500; color: rgba(255, 255, 255, 0.92); word-break: break-all; }
    .choice { display: flex; flex-direction: column; gap: 6px; width: 100%; }
    .choice form { margin: 0; width: 100%; }
    .button {
      display: flex; align-items: center; justify-content: center; width: 100%; height: 52px;
      border: 0; cursor: pointer; font: inherit; font-weight: 600; font-size: 15px;
      border-radius: 9999px; text-decoration: none;
    }
    .primary { background: rgba(255, 255, 255, 0.92); color: #121212; }
    .secondary { background: rgba(255, 255, 255, 0.06); color: rgba(255, 255, 255, 0.92); border: 1px solid rgba(255, 255, 255, 0.08); }
    .hint { font-size: 13px; line-height: 18px; color: rgba(255, 255, 255, 0.44); text-align: center; margin: 0; }
    .divider { height: 1px; background: rgba(255, 255, 255, 0.06); width: 100%; }
    .footer { width: 100%; display: flex; justify-content: space-between; font-size: 12px; color: rgba(255, 255, 255, 0.44); padding-bottom: 4px; }`;

function page(title: string, badgeLabel: string, cardBody: string, script = "") {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="robots" content="noindex">
  <title>${escapeHtml(title)}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Lexend:wght@400;500;600&display=swap" rel="stylesheet">
  <style>${STYLE}
  </style>
</head>
<body>
  <div class="shell">
    <div class="header">
      <div class="brand">${ZUNO_MARK_SVG}<span>Zuno</span></div>
      <span class="chip">${escapeHtml(badgeLabel)}</span>
    </div>
    <div class="card">
${cardBody}
    </div>
    <div class="footer">
      <span>&copy; 2026 Zuno AI</span>
      <span>Single-use &middot; 15 min</span>
    </div>
  </div>${script}
</body>
</html>`;
}

/** Deep link that hands the (still unused) token to the app, which redeems it itself. */
export function appVerifyUrl(token: string) {
  return `zuno://auth/verify?token=${encodeURIComponent(token)}`;
}

/**
 * Landing page for a magic link opened in a browser (`4e`). Opening it never redeems the token, so
 * link scanners and prefetchers can't spend it or sign anyone in. On a phone it forwards to the
 * app; signing in a waiting phone from another device takes an explicit confirmation (POST).
 */
export function renderVerifyLandingPage(options: { token: string; flowId?: string; email?: string }) {
  const appUrl = escapeHtml(appVerifyUrl(options.token));
  const accountStrip = options.email
    ? `
      <div class="strip">
        <div>
          <div class="strip-label">ACCOUNT</div>
          <div class="strip-email">${escapeHtml(options.email)}</div>
        </div>
        <span class="chip filled">${options.flowId ? "Waiting on phone" : "Ready"}</span>
      </div>`
    : "";
  const confirmChoice = options.flowId
    ? `
      <div class="choice">
        <form method="post" action="/auth/verify">
          <input type="hidden" name="token" value="${escapeHtml(options.token)}">
          <input type="hidden" name="flowId" value="${escapeHtml(options.flowId)}">
          <button class="button primary" type="submit">Confirm sign-in on my phone</button>
        </form>
        <p class="hint">Authorizes Zuno waiting on your phone</p>
      </div>`
    : "";
  const sameDeviceChoice = `
      <div class="choice">
        <a class="button ${options.flowId ? "secondary" : "primary"}" href="${appUrl}">Open Zuno on this device</a>
        <p class="hint">Signs in directly if Zuno is installed here</p>
      </div>`;

  return page(
    "Sign in to Zuno",
    "Secure sign-in",
    `      <div class="badge">${ICON_MAIL}</div>
      <div>
        <h1>${options.flowId ? "Choose where to sign in" : "Open Zuno to sign in"}</h1>
        <p class="sub">${options.flowId ? "Confirm only if you just requested this sign-in link. Opening this page never signs you in automatically." : "Open this link on the device where Zuno is installed to finish signing in."}</p>
      </div>${accountStrip}${confirmChoice}${sameDeviceChoice}`,
    `
  <script>
    if (/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) {
      window.location.href = ${JSON.stringify(appVerifyUrl(options.token))};
    }
  </script>`,
  );
}

export function renderVerifyResultPage(
  heading: string,
  body: string,
  options?: {
    variant?: "success" | "error";
    badge?: string;
    email?: string;
    actionLabel?: string;
    actionUrl?: string;
  },
) {
  const isError = options?.variant === "error";
  const badgeLabel = options?.badge ?? (isError ? "Expired" : "Verified");
  const icon = isError ? ICON_ALERT : ICON_CHECK;
  const accountStrip = options?.email
    ? `
      <div class="strip">
        <div>
          <div class="strip-label">ACCOUNT</div>
          <div class="strip-email">${escapeHtml(options.email)}</div>
        </div>
        <span class="chip filled">Authorized</span>
      </div>`
    : "";
  const actionBlock = options?.actionLabel && options?.actionUrl
    ? `
      <a class="button secondary" href="${escapeHtml(options.actionUrl)}">${escapeHtml(options.actionLabel)}</a>`
    : `
      <div class="divider"></div>
      <p class="hint" style="text-align:left;">You can close this window.</p>`;

  return page(
    `${heading} · Zuno`,
    badgeLabel,
    `      <div class="badge${isError ? " danger" : ""}">${icon}</div>
      <div>
        <h1>${escapeHtml(heading)}</h1>
        <p class="sub">${escapeHtml(body)}</p>
      </div>${accountStrip}${actionBlock}`,
  );
}
