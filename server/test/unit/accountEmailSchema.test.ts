import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import Database from 'better-sqlite3';
import { afterEach, describe, expect, it } from 'vitest';
import { DATABASE_FILE_NAME, openDatabase } from '../../src/db/openDatabase.js';

describe('migração aditiva de team_accounts', () => {
  let dataDir: string | null = null;

  afterEach(() => {
    if (dataDir !== null) {
      rmSync(dataDir, { recursive: true, force: true });
    }
  });

  it('reabre banco existente, preserva dados e cria a nova tabela', () => {
    dataDir = mkdtempSync(join(tmpdir(), 'usage-monitor-old-db-'));
    const legacy = new Database(join(dataDir, DATABASE_FILE_NAME));
    legacy.exec(`
      CREATE TABLE team_members (
        account_key TEXT NOT NULL,
        device_id TEXT NOT NULL,
        alias TEXT NOT NULL,
        host_name TEXT,
        organization_uuid TEXT,
        organization_name TEXT,
        last_seen_at INTEGER NOT NULL,
        PRIMARY KEY (account_key, device_id)
      );
      INSERT INTO team_members VALUES ('account-old', 'device-old', 'Pessoa', NULL, NULL, NULL, 1);
    `);
    legacy.close();

    const reopened = openDatabase(dataDir);
    try {
      const member = reopened.prepare('SELECT alias FROM team_members').get() as { alias: string };
      const table = reopened
        .prepare("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'team_accounts'")
        .get() as { name: string };
      expect(member.alias).toBe('Pessoa');
      expect(table.name).toBe('team_accounts');
    } finally {
      reopened.close();
    }
  });
});
