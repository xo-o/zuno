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
  });
  if (error) throw new Error(`Failed to send email: ${error.message}`);
}

function cardHtml(options: { heading: string; paragraphs: string[]; button?: { label: string; url: string }; footerHtml?: string }) {
  const paragraphs = options.paragraphs
    .map((p) => `<tr><td style="font-size:15px;line-height:22px;color:#a1a1aa;padding-bottom:16px;">${escapeHtml(p)}</td></tr>`)
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
    text: `Tap the link below to sign in to Zuno. It expires in 15 minutes.\n\n${url}\n\nIf you didn't request this, you can ignore this email.`,
    html: magicLinkHtml(url),
  });

  if (error) {
    throw new Error(`Failed to send magic link email: ${error.message}`);
  }
}

function magicLinkHtml(url: string) {
  return `<!DOCTYPE html>
<html>
  <body style="margin:0;padding:40px 16px;background:#0b0b0c;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#ffffff;">
    <table role="presentation" width="100%" cellspacing="0" cellpadding="0">
      <tr>
        <td align="center">
          <table role="presentation" width="100%" style="max-width:440px;background:#161618;border:1px solid #2a2a2d;border-radius:20px;padding:32px;">
            <tr><td style="font-size:22px;font-weight:600;padding-bottom:12px;">Sign in to Zuno</td></tr>
            <tr><td style="font-size:15px;line-height:22px;color:#a1a1aa;padding-bottom:28px;">Tap the button below on your phone to sign in. This link expires in 15 minutes and can only be used once.</td></tr>
            <tr>
              <td align="center">
                <a href="${url}" style="display:block;background:#ffffff;color:#000000;font-weight:600;font-size:15px;padding:14px 24px;border-radius:9999px;text-decoration:none;">Sign in</a>
              </td>
            </tr>
            <tr><td style="font-size:12px;color:#71717a;padding-top:28px;text-align:center;">If you didn't request this email, you can safely ignore it.</td></tr>
          </table>
        </td>
      </tr>
    </table>
  </body>
</html>`;
}
