import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  ACCOUNT_B,
  TEST_TEAM_KEY,
  createHarness,
  makePayload,
  makeSession,
  makeTurn,
  type Harness,
} from '../support/harness.js';

const HOUR = 60 * 60 * 1_000;
const NOW = Date.UTC(2026, 7, 11, 12, 0, 0);

describe('GET /api/v1/team', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  function post(body: unknown) {
    return request(harness.app).post('/api/v1/ingest').set('x-team-key', TEST_TEAM_KEY).send(body);
  }

  function get(query: Record<string, unknown>) {
    return request(harness.app).get('/api/v1/team').set('x-team-key', TEST_TEAM_KEY).query(query);
  }

  it('agrega por sessao e modelo', async () => {
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', model: 'claude-opus-4-20250514', inputTokens: 10 }),
          makeTurn({ messageId: 'b', model: 'claude-opus-4-20250514', inputTokens: 20 }),
          makeTurn({ messageId: 'c', model: 'claude-haiku-4-5-20251001', inputTokens: 5 }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(200);
    expect(response.body.rows).toHaveLength(2);

    const opus = response.body.rows.find(
      (row: { model: string }) => row.model === 'claude-opus-4-20250514',
    );
    expect(opus.turnCount).toBe(2);
    expect(opus.inputTokens).toBe(30);

    const haiku = response.body.rows.find(
      (row: { model: string }) => row.model === 'claude-haiku-4-5-20251001',
    );
    expect(haiku.turnCount).toBe(1);
    expect(haiku.inputTokens).toBe(5);
  });

  it('recorta pelos turnos, nao pelas sessoes', async () => {
    // Sessao antiga com um unico turno recente: dentro da janela ela tem de
    // aparecer somente com os tokens desse turno.
    await post(
      makePayload({
        sessions: [makeSession({ firstTs: NOW - 240 * HOUR, lastTs: NOW - HOUR })],
        turns: [
          makeTurn({ messageId: 'antigo', ts: NOW - 240 * HOUR, inputTokens: 1_000 }),
          makeTurn({ messageId: 'recente', ts: NOW - HOUR, inputTokens: 7 }),
        ],
      }),
    );

    const inWindow = await get({ accountKey: ACCOUNT_A, since: NOW - 5 * HOUR });
    expect(inWindow.body.rows).toHaveLength(1);
    expect(inWindow.body.rows[0].turnCount).toBe(1);
    expect(inWindow.body.rows[0].inputTokens).toBe(7);

    const all = await get({ accountKey: ACCOUNT_A });
    expect(all.body.rows[0].turnCount).toBe(2);
    expect(all.body.rows[0].inputTokens).toBe(1_007);
  });

  it('omite sessao sem nenhum turno na janela', async () => {
    await post(
      makePayload({
        turns: [makeTurn({ messageId: 'antigo', ts: NOW - 240 * HOUR })],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A, since: NOW - 5 * HOUR });

    expect(response.body.rows).toEqual([]);
  });

  it('nunca devolve dados de outra conta', async () => {
    await post(makePayload({ accountKey: ACCOUNT_A }));
    await post(
      makePayload({
        accountKey: ACCOUNT_B,
        member: {
          deviceId: 'device-2',
          alias: 'maria',
          hostName: 'NOTE-B2',
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession({ sessionId: 'session-2' })],
        turns: [makeTurn({ sessionId: 'session-2', messageId: 'msg-2' })],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.members).toHaveLength(1);
    expect(response.body.members[0].alias).toBe('edilson');
    expect(response.body.rows).toHaveLength(1);
    expect(response.body.rows[0].sessionId).toBe('session-1');
  });

  it('separa maquinas diferentes da mesma conta', async () => {
    await post(makePayload());
    await post(
      makePayload({
        member: {
          deviceId: 'device-2',
          alias: 'maria',
          hostName: 'NOTE-B2',
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession({ sessionId: 'session-2' })],
        turns: [makeTurn({ sessionId: 'session-2', messageId: 'msg-2' })],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.members.map((member: { alias: string }) => member.alias)).toEqual([
      'edilson',
      'maria',
    ]);
    const devices = response.body.rows.map((row: { deviceId: string }) => row.deviceId);
    expect(new Set(devices)).toEqual(new Set(['device-1', 'device-2']));
  });

  it('lista membro mesmo sem atividade na janela', async () => {
    await post(
      makePayload({
        turns: [makeTurn({ messageId: 'antigo', ts: NOW - 240 * HOUR })],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A, since: NOW - 5 * HOUR });

    expect(response.body.members).toHaveLength(1);
    expect(response.body.rows).toEqual([]);
  });

  it('rejeita query sem accountKey', async () => {
    const response = await get({});

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });
});
