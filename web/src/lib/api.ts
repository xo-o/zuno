import { API_URL } from "./site";

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers },
    });
  } catch {
    throw new ApiError(
      "We couldn't reach Zuno. Check your connection and try again.",
      0,
    );
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const message =
      typeof data?.error === "string"
        ? data.error
        : "Something went wrong. Please try again.";
    throw new ApiError(message, response.status);
  }
  return data as T;
}

export type ContactTopic = "account" | "billing" | "voice" | "bug" | "other";

export function sendContactMessage(body: {
  name: string;
  email: string;
  topic: ContactTopic;
  message: string;
}) {
  return request<{ ok: true }>("/api/contact", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function requestAccountDeletion(email: string) {
  return request<{ ok: true }>("/api/account/deletion/request", {
    method: "POST",
    body: JSON.stringify({ email }),
  });
}

export type DeletionPreview = {
  email: string;
  counts: { chats: number; topics: number; roleplays: number };
};

export function getDeletionPreview(token: string) {
  return request<DeletionPreview>(
    `/api/account/deletion/${encodeURIComponent(token)}`,
  );
}

export function confirmAccountDeletion(token: string) {
  return request<{ email: string }>(
    `/api/account/deletion/${encodeURIComponent(token)}`,
    { method: "POST", body: JSON.stringify({ confirm: "DELETE" }) },
  );
}
