import { Resend } from "resend";

const FROM = process.env.EMAIL_FROM ?? "Zuno <onboarding@resend.dev>";

let resend: Resend | undefined;

export async function sendMagicLinkEmail({ to, url }: { to: string; url: string }) {
  resend ??= new Resend(process.env.RESEND_API_KEY);
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
