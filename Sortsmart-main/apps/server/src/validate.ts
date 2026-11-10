/**
 * Input validation shared by every route. The browser is treated as
 * untrusted: everything it sends is checked here before use.
 */

export type ErrorKind = "config" | "network" | "timeout" | "aborted";

/** Mirrors ProviderError kinds on the client, plus an HTTP status for the API response. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly kind: ErrorKind,
    message: string,
  ) {
    super(message);
  }
}

export const configError = (message: string) => new ApiError(400, "config", message);

export function providerId(id: unknown): string {
  if (typeof id === "string" && id.length > 0 && id.length <= 64 && /^[a-z0-9-]+$/.test(id)) return id;
  throw configError("Invalid provider id.");
}

export function isLoopback(host: string): boolean {
  return ["localhost", "127.0.0.1", "::1", "[::1]"].includes(host) || host.endsWith(".localhost");
}

/** HTTPS anywhere, plain HTTP only on loopback. No embedded credentials. */
export function endpoint(raw: unknown): URL {
  let url: URL;
  try {
    url = new URL(String(raw));
  } catch {
    throw configError("Invalid URL.");
  }
  if (url.username || url.password) throw configError("Credentials must not be embedded in the URL.");
  if (!url.hostname) throw configError("URL has no host.");
  if (url.protocol === "https:") return url;
  if (url.protocol === "http:") {
    if (isLoopback(url.hostname)) return url;
    throw configError("Plain HTTP is only allowed for localhost.");
  }
  throw configError("Only http(s) URLs are supported.");
}

/** Host + port identity a secret is bound to. */
export function originKey(url: URL): string {
  const port = url.port || (url.protocol === "https:" ? "443" : "80");
  return `${url.protocol.slice(0, -1)}://${url.hostname}:${port}`;
}

/** Headers the browser may set. Authentication headers are never accepted from the client. */
export function allowedHeader(name: string): boolean {
  return ["content-type", "accept", "anthropic-version"].includes(name.toLowerCase());
}

const MAX_SECRET_LEN = 4096;

export function apiKey(raw: unknown): string {
  const key = typeof raw === "string" ? raw.trim() : "";
  // Rejects C0 and C1 control characters, like Rust's char::is_control.
  if (!key || key.length > MAX_SECRET_LEN || /[\u0000-\u001f\u007f-\u009f]/.test(key)) throw configError("That doesn't look like a valid API key.");
  return key;
}
