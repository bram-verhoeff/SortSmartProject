import { existsSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

/** The repository root (Sortsmart-main/). */
export const ROOT_DIR = fileURLToPath(new URL("../../../", import.meta.url));

let loaded = false;

/** Reads Sortsmart-main/.env once, if it exists. Real environment variables win. */
export function loadEnv(): void {
  if (loaded) return;
  loaded = true;
  const file = join(ROOT_DIR, ".env");
  if (existsSync(file)) process.loadEnvFile(file);
}

export interface DbConfig {
  host: string;
  port: number;
  user: string;
  password: string;
  database: string;
}

/** Defaults match a fresh XAMPP install: root without a password on 127.0.0.1:3306. */
export function dbConfig(): DbConfig {
  loadEnv();
  return {
    host: process.env.DB_HOST || "127.0.0.1",
    port: Number(process.env.DB_PORT) || 3306,
    user: process.env.DB_USER || "root",
    password: process.env.DB_PASSWORD ?? "",
    database: process.env.DB_NAME || "sortsmart",
  };
}
