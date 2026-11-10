import express from "express";
import { existsSync } from "node:fs";
import { join } from "node:path";
import { createApi } from "./api";
import { ROOT_DIR, loadEnv } from "./env";

/**
 * Serves the built website + web app and the API on one port.
 * Run `npm run build` first; for development use `npm run dev` instead.
 */
loadEnv();

// No login, so only this computer may connect.
const HOST = "127.0.0.1";
const PORT = Number(process.env.PORT) || 3000;
const DIST = join(ROOT_DIR, "apps/web/dist");

if (!existsSync(join(DIST, "index.html")) || !existsSync(join(DIST, "app/index.html"))) {
  console.error("No build found. Run `npm run build` first.");
  process.exit(1);
}

const CSP = [
  "default-src 'self'",
  // WebAssembly for fast mode (the in-browser model).
  "script-src 'self' 'wasm-unsafe-eval'",
  "worker-src 'self' blob:",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' blob: data:",
  "media-src 'self' blob: mediastream:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
].join("; ");

const app = express();
app.disable("x-powered-by");
app.use((_req, res, next) => {
  res.setHeader("content-security-policy", CSP);
  res.setHeader("x-content-type-options", "nosniff");
  res.setHeader("referrer-policy", "no-referrer");
  res.setHeader("permissions-policy", "camera=(self), microphone=(), geolocation=()");
  // Cross-origin isolation lets the in-browser model use several CPU cores. Everything is same-origin.
  res.setHeader("cross-origin-opener-policy", "same-origin");
  res.setHeader("cross-origin-embedder-policy", "require-corp");
  next();
});

app.use("/api", createApi());
// Also redirects /app to /app/, so the app's relative URLs resolve.
app.use(express.static(DIST, { index: "index.html" }));
// Client-side routes of the website (/docs, …) all load the site's index.html.
app.get("*", (req, res) => res.sendFile(join(DIST, req.path.startsWith("/app/") ? "app/index.html" : "index.html")));

app.listen(PORT, HOST, () => {
  console.log(`SORTÉ is running:\n  Website  http://localhost:${PORT}/\n  App      http://localhost:${PORT}/app/`);
});
