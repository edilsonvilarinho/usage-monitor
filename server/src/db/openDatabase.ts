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
