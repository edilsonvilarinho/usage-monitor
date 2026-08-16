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

const DAY = 24 * 60 * 60 * 1_000;
const NOW = Date.UTC(2026, 7, 11, 12, 0, 0);

describe('GET /api/v1/team/trend', () => {
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
    return request(harness.app)
      .get('/api/v1/team/trend')
      .set('x-team-key', TEST_TEAM_KEY)
      .query(query);
  }

  it('agrupa por maquina, dia e modelo', async () => {
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', ts: Date.UTC(2026, 7, 10, 9, 0, 0), inputTokens: 10 }),
          makeTurn({ messageId: 'b', ts: Date.UTC(2026, 7, 10, 21, 0, 0), inputTokens: 20 }),
          makeTurn({ messageId: 'c', ts: Date.UTC(2026, 7, 11, 9, 0, 0), inputTokens: 5 }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A, days: 30 });

    expect(response.status).toBe(200);
    expect(response.body.rows).toHaveLength(2);

    const [first, second] = response.body.rows;
    expect(first.dayStartMillis).toBe(Date.UTC(2026, 7, 10));
    expect(first.turnCount).toBe(2);
    expect(first.inputTokens).toBe(30);
    expect(second.dayStartMillis).toBe(Date.UTC(2026, 7, 11));
    expect(second.turnCount).toBe(1);
  });

  it('separa modelos dentro do mesmo dia', async () => {
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', model: 'claude-opus-4-20250514' }),
          makeTurn({ messageId: 'b', model: 'claude-haiku-4-5-20251001' }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.rows).toHaveLength(2);
    expect(new Set(response.body.rows.map((row: { model: string }) => row.model))).toEqual(
      new Set(['claude-opus-4-20250514', 'claude-haiku-4-5-20251001']),
    );
  });

  it('recorta pela janela pedida', async () => {
    await post(
      makePayload({
        sessions: [makeSession({ firstTs: NOW - 40 * DAY, lastTs: NOW })],
        turns: [
          makeTurn({ messageId: 'antigo', ts: NOW - 40 * DAY }),
          makeTurn({ messageId: 'recente', ts: NOW - 2 * DAY }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A, days: 7 });

    expect(response.body.rows).toHaveLength(1);
    expect(response.body.rows[0].dayStartMillis).toBe(Date.UTC(2026, 7, 9));
  });

  /** Uma maquina que existe mas nao consumiu tem de aparecer, com serie vazia. */
  it('devolve os integrantes mesmo sem consumo na janela', async () => {
    await post(makePayload({ turns: [makeTurn({ ts: NOW - 90 * DAY })] }));

    const response = await get({ accountKey: ACCOUNT_A, days: 7 });

    expect(response.body.rows).toHaveLength(0);
    expect(response.body.members).toHaveLength(1);
    expect(response.body.members[0].deviceId).toBe('device-1');
  });

  /** O escopo e sempre a conta pedida: a resposta nunca mistura contas. */
  it('nunca devolve dados de outra conta', async () => {
    await post(makePayload({ accountKey: ACCOUNT_A, turns: [makeTurn({ messageId: 'a' })] }));
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

    expect(response.status).toBe(200);
    expect(response.body.members.map((member: { deviceId: string }) => member.deviceId)).toEqual([
      'device-1',
    ]);
    expect(response.body.rows).toHaveLength(1);
  });

  it('recusa janela fora do teto', async () => {
    const response = await get({ accountKey: ACCOUNT_A, days: 100000 });

    expect(response.status).toBe(400);
  });

  it('exige credencial', async () => {
    const response = await request(harness.app)
      .get('/api/v1/team/trend')
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(401);
  });
});
