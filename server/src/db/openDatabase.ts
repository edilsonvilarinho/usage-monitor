import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import Database from 'better-sqlite3';
import { SCHEMA } from './schema.js';

export type Db = Database.Database;

export const DATABASE_FILE_NAME = 'team-usage.sqlite';

/**
 * Abre (e cria, se preciso) o banco do time.
 *
 * `busy_timeout` existe porque a limpeza de retencao roda num timer e pode
 * cruzar com um ingest: sem timeout a escrita concorrente falharia na hora com
 * SQLITE_BUSY em vez de esperar.
 */
export function openDatabase(dataDir: string): Db {
  mkdirSync(dataDir, { recursive: true });

  const fileName = join(dataDir, DATABASE_FILE_NAME);
  mkdirSync(dirname(fileName), { recursive: true });

  const db = new Database(fileName);
  db.pragma('journal_mode = WAL');
  db.pragma('foreign_keys = ON');
  db.pragma('busy_timeout = 5000');
  db.exec(SCHEMA);

  return db;
}

/**
 * Micro-migracao aditiva: adiciona a coluna se ela ainda nao existir.
 *
 * SQLite nao tem `ADD COLUMN IF NOT EXISTS`, e recriar a tabela para cada
 * evolucao de schema nao se justifica aqui.
 */
export function ensureColumn(db: Db, table: string, column: string, definition: string): void {
  const columns = db.prepare(`PRAGMA table_info(${table})`).all() as Array<{ name: string }>;
  const exists = columns.some((entry) => entry.name === column);
  if (exists) {
    return;
  }
  db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
}
