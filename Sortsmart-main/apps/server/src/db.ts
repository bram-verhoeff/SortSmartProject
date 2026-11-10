import mysql, { type Pool } from "mysql2/promise";
import { dbConfig } from "./env";
import { ApiError } from "./validate";

let pool: Pool | null = null;
let schemaReady: Promise<void> | null = null;

/**
 * The app's own tables. Also in database/schema.sql; created here too so an
 * existing database keeps working without re-importing (which wipes data).
 */
const APP_TABLES = [
  `CREATE TABLE IF NOT EXISTS app_opslag (
    naam           VARCHAR(20)  NOT NULL,
    inhoud         LONGBLOB     NOT NULL,
    bijgewerkt_op  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (naam)
  ) ENGINE = InnoDB`,
  `CREATE TABLE IF NOT EXISTS api_sleutel (
    provider_id    VARCHAR(64)  NOT NULL,
    sleutel        TEXT         NOT NULL,
    origin         VARCHAR(255) NOT NULL,
    opgeslagen_op  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (provider_id)
  ) ENGINE = InnoDB`,
  // What fast mode learned from corrections: an image embedding (float32), never the photo.
  `CREATE TABLE IF NOT EXISTS leervoorbeeld (
    id             INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    model          VARCHAR(100)  NOT NULL,
    categorie      VARCHAR(20)   NOT NULL,
    embedding      BLOB          NOT NULL,
    aangemaakt_op  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_leervoorbeeld_model (model, id)
  ) ENGINE = InnoDB`,
];

/** A pool whose app tables exist. Retries the table check after a failure (e.g. MySQL started later). */
export async function db(): Promise<Pool> {
  pool ??= mysql.createPool({ ...dbConfig(), connectionLimit: 5, charset: "utf8mb4" });
  const p = pool;
  schemaReady ??= (async () => {
    for (const sql of APP_TABLES) await p.query(sql);
  })().catch((e) => {
    schemaReady = null;
    throw e;
  });
  await schemaReady;
  return p;
}

/** Turns a MySQL driver error into something the user can act on. Never includes query data. */
export function dbError(err: unknown): ApiError {
  const code = (err as { code?: string })?.code ?? "";
  const { host, port, database, user } = dbConfig();
  switch (code) {
    case "ECONNREFUSED":
    case "ETIMEDOUT":
    case "ENOTFOUND":
    case "PROTOCOL_CONNECTION_LOST":
      return new ApiError(503, "network", `Can't reach MySQL at ${host}:${port}. Is MySQL running in XAMPP?`);
    case "ER_BAD_DB_ERROR":
      return new ApiError(503, "config", `Database "${database}" doesn't exist yet. Import database/schema.sql first.`);
    case "ER_ACCESS_DENIED_ERROR":
      return new ApiError(503, "config", `MySQL refused user "${user}". Check DB_USER and DB_PASSWORD in .env.`);
    case "ER_NET_PACKET_TOO_LARGE":
      return new ApiError(413, "config", "Too much data for MySQL's max_allowed_packet. Clear some history.");
    default:
      console.error("[sorte] database error", code || (err as Error)?.message);
      return new ApiError(500, "config", "Database error.");
  }
}
