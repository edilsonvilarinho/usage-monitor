import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { purgeExpiredData } from '../../src/db/retention.js';
import { ACCOUNT_A, createHarness, makeSession, makeTurn, type Harness } from '../support/harness.js';

const DAY = 24 * 60 * 60 * 1_000;
const NOW = Date.UTC(2026, 7, 11, 12, 0, 0);

describe('retencao', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('apaga turnos fora do horizonte e preserva os de dentro', () => {
    harness.repository.ingest(
      {
        accountKey: ACCOUNT_A,
        member: {
          deviceId: 'device-1',
          alias: 'edilson',
          hostName: 'DESKTOP-A1',
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession({ sessionId: 'antiga' }), makeSession({ sessionId: 'nova' })],
        turns: [
          makeTurn({ sessionId: 'antiga', messageId: 'a', ts: NOW - 60 * DAY }),
          makeTurn({ sessionId: 'nova', messageId: 'b', ts: NOW - 2 * DAY }),
        ],
      },
      NOW,
    );

    const report = purgeExpiredData(harness.db, 45, NOW);

    expect(report.deletedTurns).toBe(1);
    expect(report.deletedSessions).toBe(1);

    const remaining = harness.db
      .prepare('SELECT session_id AS sessionId FROM team_sessions')
      .all() as Array<{ sessionId: string }>;
    expect(remaining.map((row) => row.sessionId)).toEqual(['nova']);
  });

  it('preserva membro que ainda tem sessao viva', () => {
    harness.repository.ingest(
      {
        accountKey: ACCOUNT_A,
        member: {
          deviceId: 'device-1',
          alias: 'edilson',
          hostName: null,
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession()],
        turns: [makeTurn({ ts: NOW - DAY })],
      },
      NOW - 100 * DAY,
    );

    const report = purgeExpiredData(harness.db, 45, NOW);

    expect(report.deletedMembers).toBe(0);
  });

  it('apaga membro antigo que ficou sem sessao', () => {
    harness.repository.ingest(
      {
        accountKey: ACCOUNT_A,
        member: {
          deviceId: 'device-1',
          alias: 'edilson',
          hostName: null,
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession()],
        turns: [makeTurn({ ts: NOW - 90 * DAY })],
      },
      NOW - 90 * DAY,
    );

    const report = purgeExpiredData(harness.db, 45, NOW);

    expect(report.deletedTurns).toBe(1);
    expect(report.deletedSessions).toBe(1);
    expect(report.deletedMembers).toBe(1);
  });

  it('nao apaga nada quando tudo esta dentro do horizonte', () => {
    harness.repository.ingest(
      {
        accountKey: ACCOUNT_A,
        member: {
          deviceId: 'device-1',
          alias: 'edilson',
          hostName: null,
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession()],
        turns: [makeTurn({ ts: NOW - DAY })],
      },
      NOW,
    );

    expect(purgeExpiredData(harness.db, 45, NOW)).toEqual({
      deletedTurns: 0,
      deletedSessions: 0,
      deletedMembers: 0,
    });
  });
});
