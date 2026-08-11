import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import type { Express } from 'express';
import { buildApp } from '../../src/app.js';
import type { Config } from '../../src/config.js';
import type { Db } from '../../src/db/openDatabase.js';
import type {
  IngestPayload,
  IngestSession,
  IngestTurn,
  TeamRepository,
} from '../../src/repositories/teamRepository.js';

/** 32 caracteres: o minimo que `validateConfig` exige. */
export const TEST_TEAM_KEY = 'test-team-key-0123456789abcdefgh';

export const ACCOUNT_A = 'account-uuid-aaa';
export const ACCOUNT_B = 'account-uuid-bbb';

export interface Harness {
  app: Express;
  db: Db;
  repository: TeamRepository;
  config: Config;
  /** Relogio mutavel: os testes movem `now` sem esperar o tempo real passar. */
  setNow(value: number): void;
  cleanup(): void;
}

export function createHarness(configOverrides: Partial<Config> = {}): Harness {
  const dataDir = mkdtempSync(join(tmpdir(), 'usage-monitor-team-'));

  const config: Config = {
    port: 3000,
    dataDir,
    teamApiKey: TEST_TEAM_KEY,
    retentionDays: 45,
    maxTurnsPerRequest: 5000,
    trustProxyHops: 0,
    ...configOverrides,
  };

  let now = Date.UTC(2026, 7, 11, 12, 0, 0);
  const built = buildApp(config, { now: () => now });

  return {
    app: built.app,
    db: built.db,
    repository: built.repository,
    config,
    setNow(value: number) {
      now = value;
    },
    cleanup() {
      built.db.close();
      rmSync(dataDir, { recursive: true, force: true });
    },
  };
}

export function makeSession(overrides: Partial<IngestSession> = {}): IngestSession {
  return {
    sessionId: 'session-1',
    cwd: '/home/dev/api-gateway',
    gitBranch: 'main',
    firstTs: Date.UTC(2026, 7, 11, 10, 0, 0),
    lastTs: Date.UTC(2026, 7, 11, 11, 0, 0),
    liveContextTokens: 120_000,
    liveContextModel: 'claude-opus-4-20250514',
    ...overrides,
  };
}

export function makeTurn(overrides: Partial<IngestTurn> = {}): IngestTurn {
  return {
    sessionId: 'session-1',
    messageId: 'msg-1',
    ts: Date.UTC(2026, 7, 11, 11, 0, 0),
    model: 'claude-opus-4-20250514',
    isSidechain: false,
    inputTokens: 100,
    outputTokens: 200,
    cacheReadTokens: 300,
    cacheWrite5mTokens: 400,
    cacheWrite1hTokens: 0,
    ...overrides,
  };
}

export function makePayload(overrides: Partial<IngestPayload> = {}): IngestPayload {
  return {
    accountKey: ACCOUNT_A,
    member: {
      deviceId: 'device-1',
      alias: 'edilson',
      hostName: 'DESKTOP-A1',
      organizationUuid: 'org-1',
      organizationName: 'Empresa',
    },
    sessions: [makeSession()],
    turns: [makeTurn()],
    ...overrides,
  };
}
