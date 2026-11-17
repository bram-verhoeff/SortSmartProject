# SORTÉ

Intelligent recycling: a website and a web app for a smart sorting bin, with an AI classification core. Everything runs in the browser; a small Node server and MySQL do the rest.

```
packages/core     Classification engine, provider adapters, device contract, schemas (TypeScript, Zod) + tests
packages/ui       Design tokens, bundled fonts, shared primitives
apps/web          Website (/) and the web app (/app) — liquid glass hero, smoke settling into five streams (src/gl)
apps/web/src/app  The app: live camera, AI providers, history, settings
apps/server       Node + Express API: AI request proxy, API keys, settings and history in MySQL
database/         MySQL schema (schema.sql), sample data (seed.sql) and the design: class diagram,
                  relational model and implementation model (open ontwerp.html in a browser)
```

## Run

Requires Node 20+ and MySQL (XAMPP works out of the box).

1. Start **MySQL** in the XAMPP Control Panel.
2. Import the database once. This recreates the domain tables (campus to melding) and wipes their data; the app's own tables (settings, history, API keys, learned examples) are kept:
   ```bash
   C:\xampp\mysql\bin\mysql.exe -u root < database\schema.sql
   C:\xampp\mysql\bin\mysql.exe -u root < database\seed.sql      # optional sample data
   ```
3. Only if your MySQL isn't XAMPP's default (`root` without a password on port 3306): copy `.env.example` to `.env` and adjust it.
4. Start SORTÉ:
   ```bash
   npm install
   npm run dev              # website + app + API → http://localhost:5173/app/
   ```

For a production build: `npm run build && npm start` → http://localhost:3000/app/.

```bash
npm test                 # core and server tests (mock providers, no network)
npm run typecheck
```

## Architecture decisions

- **Providers** (`packages/core/src/providers`): one `AIProvider` interface; adapters for Ollama, OpenAI-compatible servers, OpenAI, Anthropic, Google Gemini (native API), DeepSeek (OpenAI-compatible; its API is text-only today, so photo classification stays off), and a deterministic demo. Capabilities are probed (Ollama `/api/show`) or explicitly declared — never assumed.
- **Fast mode** (`packages/core/src/providers/clip.ts`, `apps/web/src/app/platform/clip.ts`): free, offline sorting with CLIP running in the browser (transformers.js, WASM, multi-threaded via cross-origin isolation). It compares the photo with prompt-ensembled descriptions of each stream plus "no item", so it picks a bin in well under a second but doesn't name the exact item. The server downloads the ~150 MB model from Hugging Face once into `.cache/models` and serves it and the ONNX runtime from `/api/models` and `/api/ort` (allowlisted, read-only).
- **Learning from you**: every bin the user taps in Live stores that photo's image embedding (512 floats, never the photo) with the chosen stream in the `leervoorbeeld` table. A new photo that is ≥0.8 similar to a learned example gets a strong boost towards that stream (same scene without the item scores ~0.7, so the background alone doesn't trigger it). Near-duplicates aren't stored twice; Providers → Fast shows the count and can forget everything.
- **Engine** (`packages/core/src/engine.ts`): validate → infer → schema-check → confidence/safety → local rules → result or review. Content-hash cache, in-flight de-duplication, bounded retry for transient errors only, per-session cloud budget. Hybrid escalates to cloud only when enabled **and** disclosed; hazardous items never escalate.
- **Devices** (`packages/core/src/device`): transport-independent `SortingDevice`. Only a labelled simulator exists; commands require a review-free result or a person's decision. Accepted, acknowledged and physically verified are reported separately.
- **Server** (`apps/server`): the browser never calls AI providers directly. Every request goes through `/api/http`, which validates URLs and headers, follows no redirects, caps sizes, and attaches API keys from MySQL only to the origin they were saved for. Keys are write-only from the browser; the UI only ever sees the last 4 characters. In development the same API runs inside the Vite dev server.
- **No login, so local only**: the server listens on `127.0.0.1` only, and refuses requests with a foreign `Host` or `Origin` or without the `x-sorte-client` header (blocks other machines, DNS rebinding and cross-site requests). Don't expose it to a network without adding authentication first.
- **Storage**: settings and history live in the `app_opslag` table (gzipped JSON, because XAMPP's MySQL allows only 1 MB per query by default); keys in `api_sleutel`. The server creates both tables if they're missing. If the database can't be read at launch, the app still opens but saves nothing that session, so saved data is never replaced by defaults.

## Live camera and liquid glass

The app opens on **Live** (`apps/web/src/app/screens/Live.tsx`): the camera fills the window and glass controls float on top. Browsers only allow the camera on `localhost` or HTTPS.

- **When a model runs:** `packages/core/src/live.ts` compares small grayscale frames in the browser. It learns the empty scene, waits for an item to hold still, and triggers one classification per item. Nothing is sent while the picture moves, and the shutter always works manually. Auto with a cloud provider asks for permission first, and the session budget still applies.
- **Privacy:** the camera only runs on the Live screen. Frames aren't stored; history keeps results and a hash.
- **Glass:** `packages/ui/src/glass-core.ts` refracts a scene through every `[data-glass]` element using a signed distance field, so panels really bend the picture and melt together. The app feeds it the camera (`apps/web/src/app/gl`); the website feeds it smoke that settles into five streams (`apps/web/src/gl`). Without WebGL2 both fall back to a CSS blur. Quality drops automatically on slow GPUs.

## Known limitations

- No physical device adapter yet — waiting for the hardware protocol.
- App history is stored as one JSON document; it isn't linked to the `scan` table of the domain model yet.
- Model confidence is self-reported, not calibrated; corrections in History are the data to calibrate with.
- No login, so the app must stay on one computer (see above).
- No formal security audit.
