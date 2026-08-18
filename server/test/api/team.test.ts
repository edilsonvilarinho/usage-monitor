import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  ACCOUNT_B,
  TEST_ADMIN_TOKEN,
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

  it('soma o tempo ativo descartando as pausas longas', async () => {
    const start = Date.UTC(2026, 7, 11, 10, 0, 0);
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', ts: start }),
          // 3 min: dentro do corte, entra.
          makeTurn({ messageId: 'b', ts: start + 3 * 60_000 }),
          // 57 min: o usuario fora do teclado, nao tempo de sessao.
          makeTurn({ messageId: 'c', ts: start + 60 * 60_000 }),
          makeTurn({ messageId: 'd', ts: start + 62 * 60_000 }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.activity).toEqual([
      { deviceId: 'device-1', sessionId: 'session-1', activeMillis: 5 * 60_000 },
    ]);
  });

  it('nao conta o subagente no tempo ativo', async () => {
    const start = Date.UTC(2026, 7, 11, 10, 0, 0);
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', ts: start }),
          makeTurn({ messageId: 'b', ts: start + 4 * 60_000 }),
          // Roda em paralelo com a conversa principal: somar contaria duas vezes.
          makeTurn({ messageId: 'c', ts: start + 20 * 60_000, isSidechain: true }),
          makeTurn({ messageId: 'd', ts: start + 22 * 60_000, isSidechain: true }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.activity).toEqual([
      { deviceId: 'device-1', sessionId: 'session-1', activeMillis: 4 * 60_000 },
    ]);
  });

  it('aplica o corte que o cliente pediu', async () => {
    const start = Date.UTC(2026, 7, 11, 10, 0, 0);
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', ts: start }),
          makeTurn({ messageId: 'b', ts: start + 8 * 60_000 }),
        ],
      }),
    );

    // Com o corte padrao de 5 min o intervalo de 8 min e pausa; com 10 min ele
    // vira trabalho. O valor e do cliente, e o servidor so o aplica.
    const padrao = await get({ accountKey: ACCOUNT_A });
    expect(padrao.body.activity).toEqual([]);

    const ampliado = await get({ accountKey: ACCOUNT_A, gapCutoffMs: 10 * 60_000 });
    expect(ampliado.body.activity).toEqual([
      { deviceId: 'device-1', sessionId: 'session-1', activeMillis: 8 * 60_000 },
    ]);
  });

  it('sessao de um turno nao aparece no tempo ativo', async () => {
    await post(makePayload({ turns: [makeTurn({ messageId: 'unico' })] }));

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.activity).toEqual([]);
  });

  it('recorta o tempo ativo pela janela', async () => {
    const start = NOW - 6 * HOUR;
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'a', ts: start }),
          makeTurn({ messageId: 'b', ts: start + 2 * 60_000 }),
          makeTurn({ messageId: 'c', ts: NOW - 2 * HOUR }),
          makeTurn({ messageId: 'd', ts: NOW - 2 * HOUR + 3 * 60_000 }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A, since: NOW - 5 * HOUR });

    expect(response.body.activity).toEqual([
      { deviceId: 'device-1', sessionId: 'session-1', activeMillis: 3 * 60_000 },
    ]);
  });

  it('nao mistura o tempo ativo de outra conta', async () => {
    const start = Date.UTC(2026, 7, 11, 10, 0, 0);
    await post(
      makePayload({
        accountKey: ACCOUNT_B,
        turns: [
          makeTurn({ messageId: 'a', ts: start }),
          makeTurn({ messageId: 'b', ts: start + 60_000 }),
        ],
      }),
    );

    const response = await get({ accountKey: ACCOUNT_A });

    expect(response.body.activity).toEqual([]);
  });

  it('rejeita corte fora do intervalo aceito', async () => {
    const response = await get({ accountKey: ACCOUNT_A, gapCutoffMs: 0 });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });
});

describe('DELETE /api/v1/member administrativo', () => {
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

  function del(query: Record<string, unknown>) {
    return request(harness.app)
      .delete('/api/v1/member')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .query(query);
  }

  it('remove o integrante com as sessoes e os turnos dele', async () => {
    await post(makePayload());
    await post(
      makePayload({
        member: {
          deviceId: 'device-2',
          alias: 'fantasma',
          hostName: null,
          organizationUuid: null,
          organizationName: null,
        },
        sessions: [makeSession({ sessionId: 'session-2' })],
        turns: [makeTurn({ sessionId: 'session-2', messageId: 'msg-2' })],
      }),
    );

    const response = await del({ accountKey: ACCOUNT_A, deviceId: 'device-2' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ deletedTurns: 1, deletedSessions: 1, deletedMembers: 1 });

    const remaining = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_A });

    expect(remaining.body.members.map((member: { alias: string }) => member.alias)).toEqual([
      'edilson',
    ]);
    expect(remaining.body.rows.map((row: { deviceId: string }) => row.deviceId)).toEqual([
      'device-1',
    ]);
  });

  it('nao toca em outra conta com o mesmo deviceId', async () => {
    await post(makePayload());
    await post(makePayload({ accountKey: ACCOUNT_B }));

    await del({ accountKey: ACCOUNT_A, deviceId: 'device-1' });

    const other = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_B });

    expect(other.body.members).toHaveLength(1);
    expect(other.body.rows).toHaveLength(1);
  });

  it('e idempotente para device desconhecido', async () => {
    const response = await del({ accountKey: ACCOUNT_A, deviceId: 'nao-existe' });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ deletedTurns: 0, deletedSessions: 0, deletedMembers: 0 });
  });

  it('rejeita query sem deviceId', async () => {
    const response = await del({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('exige o token de administracao', async () => {
    const response = await request(harness.app)
      .delete('/api/v1/member')
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1' });

    expect(response.status).toBe(401);
  });

  it('recusa a chave do time mesmo quando ela e dona da conta', async () => {
    await post(makePayload());

    const response = await request(harness.app)
      .delete('/api/v1/member')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1' });

    expect(response.status).toBe(401);
    const remaining = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_A });
    expect(remaining.body.members).toHaveLength(1);
  });
});
