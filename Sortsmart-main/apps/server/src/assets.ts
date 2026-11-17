import express, { type Request, type Response } from "express";
import { createWriteStream, existsSync } from "node:fs";
import { mkdir, rename, rm } from "node:fs/promises";
import { dirname, join } from "node:path";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { ROOT_DIR } from "./env";

/**
 * Files for fast mode (CLIP in the browser):
 *   /models/<model>/resolve/main/<file>  model files, downloaded from Hugging Face once and kept in .cache/models
 *   /ort/<file>                          ONNX Runtime's WASM files, from node_modules
 * Read-only and limited to an allowlist, so they can't be used to fetch anything else.
 */

const MODELS = new Set(["Xenova/clip-vit-base-patch32"]);
const CACHE_DIR = join(ROOT_DIR, ".cache/models");
const ORT_DIR = join(ROOT_DIR, "node_modules/onnxruntime-web/dist");
const MAX_FILE_BYTES = 500 * 1024 * 1024;

const TYPES: Record<string, string> = {
  json: "application/json",
  onnx: "application/octet-stream",
  wasm: "application/wasm",
  mjs: "text/javascript",
};
const typeOf = (file: string) => TYPES[file.split(".").pop() ?? ""] ?? "application/octet-stream";

/** One download per file at a time, even if the browser asks twice. */
const downloads = new Map<string, Promise<boolean>>();

async function download(model: string, file: string, target: string): Promise<boolean> {
  const res = await fetch(`https://huggingface.co/${model}/resolve/main/${file}`, { redirect: "follow" });
  if (res.status === 404) return false;
  if (!res.ok || !res.body) throw new Error(`Hugging Face answered ${res.status}`);
  if (Number(res.headers.get("content-length")) > MAX_FILE_BYTES) throw new Error("File too large");
  await mkdir(dirname(target), { recursive: true });
  const tmp = `${target}.part`;
  try {
    await pipeline(Readable.fromWeb(res.body as import("node:stream/web").ReadableStream), createWriteStream(tmp));
    await rename(tmp, target);
  } catch (e) {
    await rm(tmp, { force: true });
    throw e;
  }
  return true;
}

async function modelFile(req: Request, res: Response): Promise<void> {
  const match = /^\/models\/([\w-]+\/[\w.-]+)\/resolve\/main\/((?:onnx\/)?[\w.-]+\.(?:json|onnx))$/.exec(req.path);
  const model = match?.[1];
  const file = match?.[2];
  if (!model || !file || !MODELS.has(model) || file.includes("..")) {
    res.status(404).end();
    return;
  }
  const target = join(CACHE_DIR, model, file);
  if (!existsSync(target)) {
    const key = `${model}/${file}`;
    let job = downloads.get(key);
    if (!job) {
      job = download(model, file, target).finally(() => downloads.delete(key));
      downloads.set(key, job);
    }
    try {
      if (!(await job)) {
        res.status(404).end();
        return;
      }
    } catch (e) {
      console.error("[sorte] model download failed:", key, (e as Error).message);
      res.status(502).type("text/plain").send("Couldn't download the model. Is this computer online?");
      return;
    }
  }
  res.setHeader("cache-control", "public, max-age=31536000, immutable");
  res.type(typeOf(file)).sendFile(target);
}

export function createAssets() {
  const router = express.Router();
  // The page is cross-origin isolated (so the model can use several CPU cores). The runtime's
  // worker script must say so too, or the browser blocks it and the model falls back to one core.
  // `npm start` sets these for every response; under `npm run dev` only this router does.
  router.use((_req, res, next) => {
    res.setHeader("cross-origin-embedder-policy", "require-corp");
    res.setHeader("cross-origin-opener-policy", "same-origin");
    res.setHeader("cross-origin-resource-policy", "same-origin");
    next();
  });
  router.get(/^\/models\//, (req, res, next) => void modelFile(req, res).catch(next));
  // /ort/<file> or /ort/<revision>/<file>. The app asks for a revision, so a browser that cached
  // these files before they had the isolation headers fetches them fresh.
  router.get(/^\/ort\/(?:\d+\/)?([\w.-]+)$/, (req, res) => {
    const file = (req.params as Record<string, string>)[0] ?? "";
    if (!/^ort-wasm[\w.-]*\.(?:wasm|mjs)$/.test(file) || !existsSync(join(ORT_DIR, file))) return void res.status(404).end();
    res.setHeader("cache-control", "public, max-age=86400");
    res.type(typeOf(file)).sendFile(join(ORT_DIR, file));
  });
  return router;
}
