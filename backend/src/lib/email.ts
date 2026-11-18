import { Resend } from "resend";

const FROM = process.env.EMAIL_FROM ?? "Zuno <onboarding@resend.dev>";

let resend: Resend | undefined;

function client() {
  resend ??= new Resend(process.env.RESEND_API_KEY);
  return resend;
}

export function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** Plain transactional email in the app's dark card style. [paragraphs] are escaped; [footer] may hold links. */
export async function sendEmail(options: {
  to: string;
  subject: string;
  heading: string;
  paragraphs: string[];
  button?: { label: string; url: string };
  footerHtml?: string;
  headers?: Record<string, string>;
  replyTo?: string;
}) {
  const text = [
    options.heading,
    "",
    ...options.paragraphs,
    ...(options.button ? ["", `${options.button.label}: ${options.button.url}`] : []),
  ].join("\n");
  const { error } = await client().emails.send({
    from: FROM,
    to: options.to,
    subject: options.subject,
    text,
    html: cardHtml(options),
    headers: options.headers,
    replyTo: options.replyTo,
  });
  if (error) throw new Error(`Failed to send email: ${error.message}`);
}

function cardHtml(options: { heading: string; paragraphs: string[]; button?: { label: string; url: string }; footerHtml?: string }) {
  const paragraphs = options.paragraphs
    .map((p) => `<tr><td style="font-size:15px;line-height:22px;color:#a1a1aa;padding-bottom:16px;white-space:pre-wrap;">${escapeHtml(p)}</td></tr>`)
    .join("");
  const button = options.button
    ? `<tr><td align="center" style="padding-top:12px;"><a href="${escapeHtml(options.button.url)}" style="display:block;background:#ffffff;color:#000000;font-weight:600;font-size:15px;padding:14px 24px;border-radius:9999px;text-decoration:none;">${escapeHtml(options.button.label)}</a></td></tr>`
    : "";
  const footer = options.footerHtml
    ? `<tr><td style="font-size:12px;color:#71717a;padding-top:28px;text-align:center;">${options.footerHtml}</td></tr>`
    : "";
  return `<!DOCTYPE html>
<html>
  <body style="margin:0;padding:40px 16px;background:#0b0b0c;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#ffffff;">
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" style="max-width:440px;background:#161618;border:1px solid #2a2a2d;border-radius:20px;padding:32px;">
            <tr><td style="font-size:22px;font-weight:600;padding-bottom:12px;">${escapeHtml(options.heading)}</td></tr>
            ${paragraphs}${button}${footer}
          </table>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}

export async function sendMagicLinkEmail({ to, url }: { to: string; url: string }) {
  const resend = client();
  const { error } = await resend.emails.send({
    from: FROM,
    to,
    subject: "Your Zuno sign-in link",
    text: `Tap the link below to sign in to Zuno as ${to}. This link works once and expires in 15 minutes.\n\n${url}\n\nIf you didn't request this, you can safely ignore this email.`,
    html: magicLinkHtml(url, to),
  });

  if (error) {
    throw new Error(`Failed to send magic link email: ${error.message}`);
  }
}

const ZUNO_MARK_SVG = `<svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="display:block;">
  <circle cx="9.5" cy="9.5" r="4.5" stroke="#ebebeb" stroke-width="1.8"/>
  <circle cx="14.5" cy="9.5" r="4.5" stroke="#ebebeb" stroke-width="1.8"/>
  <circle cx="9.5" cy="14.5" r="4.5" stroke="#ebebeb" stroke-width="1.8"/>
  <circle cx="14.5" cy="14.5" r="4.5" stroke="#ebebeb" stroke-width="1.8"/>
</svg>`;

function magicLinkHtml(url: string, to: string) {
  const safeUrl = escapeHtml(url);
  const safeEmail = escapeHtml(to);
  return `<!DOCTYPE html>
<html lang="en">
  <body style="margin:0;padding:36px 16px;background:#121212;font-family:'Lexend',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#ebebeb;">
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" style="max-width:440px;background:#171717;border:1px solid rgba(255,255,255,0.08);border-radius:18px;padding:24px;">
            <tr>
              <td style="padding-bottom:20px;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
                  <tr>
                    <td style="vertical-align:middle;">
                      <table role="presentation" cellspacing="0" cellpadding="0">
                        <tr>
                          <td style="padding-right:8px;vertical-align:middle;">${ZUNO_MARK_SVG}</td>
                          <td style="font-size:17px;font-weight:500;letter-spacing:-0.01em;color:#ebebeb;vertical-align:middle;">Zuno</td>
                        </tr>
                      </table>
                    </td>
                    <td align="right" style="vertical-align:middle;">
                      <span style="display:inline-block;padding:5px 12px;border-radius:9999px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);font-size:12px;font-weight:500;color:rgba(255,255,255,0.64);">15 min</span>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            <tr><td style="font-size:24px;line-height:30px;font-weight:600;letter-spacing:-0.03em;color:#ebebeb;padding-bottom:8px;">Sign in to Zuno</td></tr>
            <tr><td style="font-size:14px;line-height:20px;color:rgba(255,255,255,0.64);padding-bottom:20px;">Tap the button below to finish signing in. This link works once and expires in 15 minutes.</td></tr>
            <tr>
              <td style="padding-bottom:20px;">
                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.06);border-radius:14px;padding:12px 14px;">
                  <tr>
                    <td style="vertical-align:middle;">
                      <div style="font-size:11px;line-height:14px;font-weight:500;letter-spacing:0.04em;color:rgba(255,255,255,0.44);padding-bottom:2px;">SIGNING IN AS</div>
                      <div style="font-size:15px;line-height:22px;font-weight:500;color:#ebebeb;">${safeEmail}</div>
                    </td>
                    <td align="right" style="vertical-align:middle;">
                      <span style="display:inline-block;padding:5px 12px;border-radius:9999px;background:rgba(255,255,255,0.10);border:1px solid rgba(255,255,255,0.08);font-size:12px;font-weight:500;color:#ebebeb;">Single-use</span>
                    </td>
                  </tr>
                </table>
              </td>
            </tr>
            <tr>
              <td align="center" style="padding-bottom:20px;">
                <a href="${safeUrl}" style="display:block;background:#ebebeb;color:#121212;font-weight:600;font-size:15px;line-height:20px;padding:14px 24px;border-radius:9999px;text-decoration:none;">Sign in to Zuno &rarr;</a>
              </td>
            </tr>
            <tr><td style="border-top:1px solid rgba(255,255,255,0.06);padding-top:16px;font-size:13px;line-height:18px;color:rgba(255,255,255,0.44);">If you didn&rsquo;t request this email, you can safely ignore it.</td></tr>
          </table>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}
