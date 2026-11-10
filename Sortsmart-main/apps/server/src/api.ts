import express, { type NextFunction, type Request, type Response } from "express";
import type { RowDataPacket } from "mysql2/promise";
import { gunzipSync, gzipSync } from "node:zlib";
import { createAssets } from "./assets";
import { db, dbError } from "./db";
import { dbConfig } from "./env";
import { ApiError, allowedHeader, apiKey, configError, endpoint, isLoopback, originKey, providerId } from "./validate";

/**
 * The web app's backend, mounted at /api. It replaces the desktop app's Rust
 * layer: it is the only path that attaches API keys (which never go back to
 * the browser), and it keeps settings and history in MySQL.
 *
 * There is no login, so the server only listens on 127.0.0.1 and refuses
 * requests that don't come from a page on this computer.
 */

const MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
const MAX_REQUEST_BYTES = 12 * 1024 * 1024;
const MAX_STORE_BYTES = 20 * 1024 * 1024;
const STORES = new Set(["settings", "history"]);
const MAX_EXAMPLES = 2000;

type Handler = (req: Request, res: Response) => Promise<void>;
const route = (fn: Handler) => (req: Request, res: Response, next: NextFunction) => void fn(req, res).catch(next);

function hostname(hostHeader: string | undefined): string | null {
  try {
    return hostHeader ? new URL(`http://${hostHeader}`).hostname : null;
  } catch {
    return null;
  }
}

/** Blocks other machines, DNS rebinding and cross-site requests from other pages in the browser. */
function localOnly(req: Request, _res: Response, next: NextFunction) {
  const host = hostname(req.headers.host);
  if (!host || !isLoopback(host)) return next(new ApiError(403, "config", "Only local access is allowed."));
  const origin = req.headers.origin;
  if (origin && !isLoopback(hostname(origin.replace(/^https?:\/\//, "")) ?? "")) return next(new ApiError(403, "config", "Cross-site requests are not allowed."));
  next();
}

/** A custom header can't be sent cross-site without a CORS preflight, which this server never approves. */
function clientHeader(req: Request, _res: Response, next: NextFunction) {
  if (req.headers["x-sorte-client"] !== "1") return next(new ApiError(403, "config", "Missing client header."));
  next();
}

async function query<T extends RowDataPacket>(sql: string, params: unknown[]): Promise<T[]> {
  try {
    const [rows] = await (await db()).query<T[]>(sql, params);
    return rows;
  } catch (e) {
    throw dbError(e);
  }
}

async function exec(sql: string, params: unknown[]): Promise<void> {
  try {
    await (await db()).query(sql, params);
  } catch (e) {
    throw dbError(e);
  }
}

// ───────── HTTP proxy to AI providers ─────────

interface ProxyRequest {
  method: "GET" | "POST";
  url: URL;
  headers: Record<string, string>;
  body: string | undefined;
  timeoutMs: number;
  auth: { providerId: string; header: string; scheme: string } | null;
}

function parseProxyRequest(raw: unknown): ProxyRequest {
  const r = (raw ?? {}) as Record<string, unknown>;
  if (r.method !== "GET" && r.method !== "POST") throw configError("Unsupported method.");
  const url = endpoint(r.url);
  const headers: Record<string, string> = {};
  for (const [name, value] of Object.entries((r.headers ?? {}) as Record<string, unknown>)) {
    if (!allowedHeader(name)) throw configError(`Header "${name}" is not allowed.`);
    headers[name] = String(value);
  }
  const body = r.body == null ? undefined : String(r.body);
  if (body && Buffer.byteLength(body) > MAX_REQUEST_BYTES) throw configError("Request body too large.");
  const timeoutMs = Math.min(300_000, Math.max(1_000, Number(r.timeoutMs) || 60_000));
  const a = r.auth as Record<string, unknown> | null | undefined;
  const auth = a ? { providerId: providerId(a.providerId), header: String(a.header), scheme: String(a.scheme) } : null;
  return { method: r.method, url, headers, body, timeoutMs, auth };
}

async function attachAuth(req: ProxyRequest): Promise<void> {
  if (!req.auth) return;
  const rows = await query<RowDataPacket & { sleutel: string; origin: string }>("SELECT sleutel, origin FROM api_sleutel WHERE provider_id = ?", [req.auth.providerId]);
  const stored = rows[0];
  if (!stored) throw configError("No API key saved for this provider.");
  if (stored.origin !== originKey(req.url)) throw configError("This key is bound to a different host. Save it again for the current endpoint.");
  const { header, scheme } = req.auth;
  if (header === "authorization" && scheme === "bearer") req.headers[header] = `Bearer ${stored.sleutel}`;
  else if ((header === "x-api-key" || header === "x-goog-api-key") && scheme === "raw") req.headers[header] = stored.sleutel;
  else throw configError("Unsupported auth scheme.");
}

async function readCapped(res: globalThis.Response): Promise<string> {
  const length = Number(res.headers.get("content-length"));
  if (length > MAX_RESPONSE_BYTES) throw new ApiError(502, "network", "Response too large.");
  if (!res.body) return "";
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_RESPONSE_BYTES) {
      await reader.cancel();
      throw new ApiError(502, "network", "Response too large.");
    }
    chunks.push(value);
  }
  return Buffer.concat(chunks).toString("utf8");
}

async function proxy(req: Request, res: Response): Promise<void> {
  const r = parseProxyRequest(req.body);
  await attachAuth(r);

  const ctrl = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    ctrl.abort();
  }, r.timeoutMs);
  // The browser cancelled (or the tab closed): stop the upstream request too.
  const onClose = () => !res.writableFinished && ctrl.abort();
  res.on("close", onClose);

  try {
    let upstream: globalThis.Response;
    try {
      upstream = await fetch(r.url, {
        method: r.method,
        headers: { ...r.headers, "user-agent": "SORTE-Web/0.1" },
        body: r.method === "POST" ? r.body : undefined,
        redirect: "manual",
        signal: ctrl.signal,
      });
    } catch {
      if (timedOut) throw new ApiError(504, "timeout", `No response within ${r.timeoutMs} ms.`);
      if (ctrl.signal.aborted) throw new ApiError(499, "aborted", "Request cancelled.");
      throw new ApiError(502, "network", "Could not connect. Is the server running and reachable?");
    }
    let body: string;
    try {
      body = await readCapped(upstream);
    } catch (e) {
      if (e instanceof ApiError) throw e;
      if (timedOut) throw new ApiError(504, "timeout", "Timed out reading the response.");
      if (ctrl.signal.aborted) throw new ApiError(499, "aborted", "Request cancelled.");
      throw new ApiError(502, "network", "Connection dropped while reading the response.");
    }
    res.json({ status: upstream.status, body });
  } finally {
    clearTimeout(timer);
    res.off("close", onClose);
  }
}

// ───────── App ─────────

export function createApi() {
  const app = express();
  app.disable("x-powered-by");
  app.use(localOnly);
  // Read-only model and runtime files for fast mode; fetched by the ML library, which can't add our header.
  app.use(createAssets());
  app.use(clientHeader);
  app.use(express.json({ limit: "25mb" }));
  app.use((_req, res, next) => {
    res.setHeader("cache-control", "no-store");
    next();
  });

  app.get("/info", (_req, res) => {
    const { database, host, port } = dbConfig();
    res.json({ location: `MySQL · ${database} @ ${host}:${port}` });
  });

  app.post("/http", route(proxy));

  // API keys: write-only from the browser. Only the last 4 characters come back.
  app.get(
    "/secrets/:id",
    route(async (req, res) => {
      const rows = await query("SELECT 1 FROM api_sleutel WHERE provider_id = ?", [providerId(req.params.id)]);
      res.json({ exists: rows.length > 0 });
    }),
  );
  app.put(
    "/secrets/:id",
    route(async (req, res) => {
      const id = providerId(req.params.id);
      const key = apiKey(req.body?.secret);
      const origin = originKey(endpoint(req.body?.endpoint));
      await exec("INSERT INTO api_sleutel (provider_id, sleutel, origin) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE sleutel = VALUES(sleutel), origin = VALUES(origin)", [id, key, origin]);
      res.json({ hint: key.slice(-4) });
    }),
  );
  app.delete(
    "/secrets/:id",
    route(async (req, res) => {
      await exec("DELETE FROM api_sleutel WHERE provider_id = ?", [providerId(req.params.id)]);
      res.json({ ok: true });
    }),
  );

  // Settings and history, stored gzipped: XAMPP's MySQL accepts only 1 MB per query by default.
  const storeName = (raw: unknown) => {
    if (typeof raw !== "string" || !STORES.has(raw)) throw configError("Unknown store.");
    return raw;
  };
  app.get(
    "/store/:name",
    route(async (req, res) => {
      const rows = await query<RowDataPacket & { inhoud: Buffer }>("SELECT inhoud FROM app_opslag WHERE naam = ?", [storeName(req.params.name)]);
      res.json({ contents: rows[0] ? gunzipSync(rows[0].inhoud).toString("utf8") : null });
    }),
  );
  app.put(
    "/store/:name",
    route(async (req, res) => {
      const name = storeName(req.params.name);
      const contents = req.body?.contents;
      if (typeof contents !== "string") throw configError("Expected text contents.");
      if (Buffer.byteLength(contents) > MAX_STORE_BYTES) throw configError("Data too large.");
      await exec("INSERT INTO app_opslag (naam, inhoud) VALUES (?, ?) ON DUPLICATE KEY UPDATE inhoud = VALUES(inhoud)", [name, gzipSync(contents)]);
      res.json({ ok: true });
    }),
  );

  // Fast mode's learned examples: image embeddings as base64 float32, never photos.
  const modelName = (raw: unknown) => {
    if (typeof raw !== "string" || !/^[\w./-]{1,100}$/.test(raw)) throw configError("Invalid model.");
    return raw;
  };
  app.get(
    "/examples",
    route(async (req, res) => {
      const rows = await query<RowDataPacket & { categorie: string; embedding: Buffer }>(
        "SELECT categorie, embedding FROM leervoorbeeld WHERE model = ? ORDER BY id DESC LIMIT ?",
        [modelName(req.query.model), MAX_EXAMPLES],
      );
      res.json({ examples: rows.reverse().map((r) => ({ category: r.categorie, embedding: r.embedding.toString("base64") })) });
    }),
  );
  app.post(
    "/examples",
    route(async (req, res) => {
      const model = modelName(req.body?.model);
      const category = req.body?.category;
      if (typeof category !== "string" || !/^[a-z-]{1,20}$/.test(category)) throw configError("Invalid category.");
      const bytes = Buffer.from(String(req.body?.embedding ?? ""), "base64");
      if (bytes.length < 128 * 4 || bytes.length > 4096 * 4 || bytes.length % 4 !== 0) throw configError("Invalid embedding.");
      await exec("INSERT INTO leervoorbeeld (model, categorie, embedding) VALUES (?, ?, ?)", [model, category, bytes]);
      // Keep the newest MAX_EXAMPLES per model.
      await exec(
        "DELETE FROM leervoorbeeld WHERE model = ? AND id < (SELECT id FROM (SELECT id FROM leervoorbeeld WHERE model = ? ORDER BY id DESC LIMIT 1 OFFSET ?) AS keep)",
        [model, model, MAX_EXAMPLES - 1],
      );
      res.json({ ok: true });
    }),
  );
  app.delete(
    "/examples",
    route(async (req, res) => {
      await exec("DELETE FROM leervoorbeeld WHERE model = ?", [modelName(req.query.model)]);
      res.json({ ok: true });
    }),
  );

  app.use((_req, _res, next) => next(new ApiError(404, "config", "Not found.")));
  app.use((err: unknown, _req: Request, res: Response, _next: NextFunction) => {
    const e =
      err instanceof ApiError
        ? err
        : (err as { type?: string })?.type === "entity.too.large"
          ? new ApiError(413, "config", "Request too large.")
          : (err as { type?: string })?.type === "entity.parse.failed"
            ? configError("Invalid JSON.")
            : new ApiError(500, "config", "Server error.");
    if (e.status === 500) console.error("[sorte]", err);
    if (!res.headersSent && !res.writableEnded) res.status(e.status).json({ kind: e.kind, message: e.message });
  });

  return app;
}
