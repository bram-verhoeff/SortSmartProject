import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import { defineConfig, type Plugin } from "vite";
import { createApi } from "../server/src/api";

/** In development the API runs inside the Vite server, so `npm run dev` is all you need. */
function sorteApi(): Plugin {
  return {
    name: "sorte-api",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url === "/app" || req.url?.startsWith("/app?")) {
          res.statusCode = 301;
          res.setHeader("location", req.url.replace(/^\/app/, "/app/"));
          return res.end();
        }
        next();
      });
      server.middlewares.use("/api", createApi());
    },
  };
}

export default defineConfig({
  plugins: [react(), sorteApi()],
  // Only VITE_PUBLIC_* variables reach the bundle. Never put provider keys in web env.
  envPrefix: "VITE_PUBLIC_",
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    // Cross-origin isolation lets the in-browser model (fast mode) use several CPU cores.
    headers: { "cross-origin-opener-policy": "same-origin", "cross-origin-embedder-policy": "require-corp" },
  },
  build: {
    target: ["es2022", "safari15"],
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      input: {
        site: fileURLToPath(new URL("index.html", import.meta.url)),
        app: fileURLToPath(new URL("app/index.html", import.meta.url)),
      },
    },
  },
});
