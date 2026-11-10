import { ProviderError, prepareImage, type CategoryId, type ExampleStore, type HttpRequest, type HttpResponse, type HttpTransport, type ImageInput } from "@sorte/core";

/**
 * Platform boundary. Privileged work (API keys, network requests that need
 * them, storage) runs on the SORTÉ server (apps/server), which stores
 * everything in MySQL. Keys go in once and never come back to the browser.
 */

type ErrorKind = "config" | "timeout" | "network" | "aborted";

async function api<T>(path: string, init: { method?: string; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method: init.method ?? "GET",
      headers: { "content-type": "application/json", "x-sorte-client": "1" },
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      signal: init.signal,
    });
  } catch (err) {
    if ((err as Error)?.name === "AbortError") throw new ProviderError("aborted", "Request cancelled.");
    throw new ProviderError("network", "Can't reach the SORTÉ server. Is it still running?");
  }
  const data = (await res.json().catch(() => null)) as (T & { kind?: ErrorKind; message?: string }) | null;
  if (!res.ok) throw new ProviderError(data?.kind ?? "network", data?.message ?? `Server error (${res.status}).`);
  return data as T;
}

// ───────── HTTP ─────────

export const transport: HttpTransport = {
  name: "server",
  supportsSecrets: true,
  request(req: HttpRequest, signal?: AbortSignal): Promise<HttpResponse> {
    if (signal?.aborted) return Promise.reject(new ProviderError("aborted", "Request cancelled."));
    return api<HttpResponse>("/http", {
      method: "POST",
      body: { method: req.method, url: req.url, headers: req.headers ?? {}, body: req.body ?? null, timeoutMs: req.timeoutMs, auth: req.auth ?? null },
      signal,
    });
  },
};

// ───────── Secrets (MySQL, via the server) ─────────

export function openExternal(url: string): void {
  window.open(url, "_blank", "noopener");
}

/** Store a key bound to the endpoint's host. Returns only the last 4 characters for display. */
export async function saveSecret(providerId: string, secret: string, endpoint: string): Promise<string> {
  const { hint } = await api<{ hint: string }>(`/secrets/${encodeURIComponent(providerId)}`, { method: "PUT", body: { secret, endpoint } });
  return hint;
}

export async function deleteSecret(providerId: string): Promise<void> {
  await api(`/secrets/${encodeURIComponent(providerId)}`, { method: "DELETE" });
}

export async function hasSecret(providerId: string): Promise<boolean> {
  const { exists } = await api<{ exists: boolean }>(`/secrets/${encodeURIComponent(providerId)}`);
  return exists;
}

// ───────── Storage ─────────

export type StoreName = "settings" | "history";

export async function readStore(name: StoreName): Promise<string | null> {
  const { contents } = await api<{ contents: string | null }>(`/store/${name}`);
  return contents;
}

export async function writeStore(name: StoreName, contents: string): Promise<void> {
  await api(`/store/${name}`, { method: "PUT", body: { contents } });
}

export async function storeLocation(): Promise<string> {
  try {
    return (await api<{ location: string }>("/info")).location;
  } catch {
    return "Server unreachable";
  }
}

// ───────── Images ─────────

/** Downscale in the browser to the configured max edge and re-encode as JPEG before hashing/sending. A video gives its current frame. */
export async function prepareImageSource(source: Blob | HTMLVideoElement, maxEdge: number): Promise<{ input: ImageInput; previewUrl: string }> {
  const isVideo = source instanceof HTMLVideoElement;
  const bitmap = isVideo ? null : await createImageBitmap(source);
  const fullW = isVideo ? source.videoWidth : bitmap!.width;
  const fullH = isVideo ? source.videoHeight : bitmap!.height;
  const scale = Math.min(1, maxEdge / Math.max(fullW, fullH));
  const w = Math.max(1, Math.round(fullW * scale));
  const h = Math.max(1, Math.round(fullH * scale));
  const canvas = document.createElement("canvas");
  canvas.width = w;
  canvas.height = h;
  canvas.getContext("2d")!.drawImage(isVideo ? source : bitmap!, 0, 0, w, h);
  bitmap?.close();
  const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Could not encode image."))), "image/jpeg", 0.88));
  const bytes = new Uint8Array(await blob.arrayBuffer());
  return { input: await prepareImage(bytes, "image/jpeg"), previewUrl: URL.createObjectURL(blob) };
}

// ───────── Learned examples (fast mode) ─────────

function toBase64(values: number[]): string {
  const bytes = new Uint8Array(new Float32Array(values).buffer);
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]!);
  return btoa(bin);
}

function fromBase64(b64: string): number[] {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return Array.from(new Float32Array(bytes.buffer));
}

/** Image embeddings of items the user sorted themselves, kept in MySQL. Never photos. */
export const exampleStore: ExampleStore = {
  async list(model) {
    const { examples } = await api<{ examples: { category: CategoryId; embedding: string }[] }>(`/examples?model=${encodeURIComponent(model)}`);
    return examples.map((e) => ({ category: e.category, embedding: fromBase64(e.embedding) }));
  },
  async add(model, example) {
    await api("/examples", { method: "POST", body: { model, category: example.category, embedding: toBase64(example.embedding) } });
  },
  async clear(model) {
    await api(`/examples?model=${encodeURIComponent(model)}`, { method: "DELETE" });
  },
};
