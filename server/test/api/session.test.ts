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

const MINUTE = 60 * 1_000;
const NOW = Date.UTC(2026, 7, 11, 12, 0, 0);

describe('GET /api/v1/session', () => {
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
    return request(harness.app).get('/api/v1/session').set('x-team-key', TEST_TEAM_KEY).query(query);
  }

  it('devolve os turnos crus da sessao com os metadados dela', async () => {
    await post(
      makePayload({
        turns: [
          makeTurn({
            messageId: 'msg-1',
            ts: NOW - 2 * MINUTE,
            inputTokens: 10,
            outputTokens: 20,
            cacheReadTokens: 30,
            cacheWrite5mTokens: 40,
            cacheWrite1hTokens: 50,
          }),
        ],
      }),
    );

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-1',
    });

    expect(response.status).toBe(200);
    expect(response.body.session).toMatchObject({
      deviceId: 'device-1',
      sessionId: 'session-1',
      hostName: 'DESKTOP-A1',
      cwd: '/home/dev/api-gateway',
      gitBranch: 'main',
      liveContextTokens: 120_000,
      liveContextModel: 'claude-opus-4-20250514',
    });
    expect(response.body.turns).toHaveLength(1);
    expect(response.body.turns[0]).toMatchObject({
      messageId: 'msg-1',
      ts: NOW - 2 * MINUTE,
      model: 'claude-opus-4-20250514',
      isSidechain: false,
      inputTokens: 10,
      outputTokens: 20,
      cacheReadTokens: 30,
      cacheWrite5mTokens: 40,
      cacheWrite1hTokens: 50,
    });
  });

  it('ordena os turnos por ts e desempata pelo messageId', async () => {
    // Chegam fora de ordem e dois compartilham o mesmo instante: sem o desempate
    // a serie por turno mudaria de forma a cada leitura.
    await post(
      makePayload({
        turns: [
          makeTurn({ messageId: 'c', ts: NOW }),
          makeTurn({ messageId: 'b', ts: NOW - MINUTE }),
          makeTurn({ messageId: 'a', ts: NOW - MINUTE }),
        ],
      }),
    );

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-1',
    });

    expect(response.body.turns.map((turn: { messageId: string }) => turn.messageId)).toEqual([
      'a',
      'b',
      'c',
    ]);
  });

  it('preserva o marcador de subagente', async () => {
    await post(
      makePayload({
        turns: [makeTurn({ messageId: 'msg-1', isSidechain: true })],
      }),
    );

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-1',
    });

    expect(response.body.turns[0].isSidechain).toBe(true);
  });

  it('nao devolve a sessao de outra conta', async () => {
    await post(makePayload({ accountKey: ACCOUNT_B }));

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-1',
    });

    // Mesma resposta de sessao inexistente: confirmar que ela existe em outra
    // conta ja seria vazamento.
    expect(response.status).toBe(404);
    expect(response.body.code).toBe('not_found');
  });

  it('nao devolve a sessao de outra maquina da mesma conta', async () => {
    await post(makePayload());

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-2',
      sessionId: 'session-1',
    });

    expect(response.status).toBe(404);
  });

  it('responde 404 para sessao inexistente', async () => {
    await post(makePayload());

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-inexistente',
    });

    expect(response.status).toBe(404);
    expect(response.body.code).toBe('not_found');
  });

  it('devolve a sessao sem turnos como lista vazia', async () => {
    await post(makePayload({ turns: [] }));

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-1',
    });

    expect(response.status).toBe(200);
    expect(response.body.turns).toEqual([]);
  });

  it('ignora o recorte temporal da lista e devolve a sessao inteira', async () => {
    // O detalhe e sempre a sessao inteira: recorta-lo pela janela de quota daria
    // graficos que comecam no meio da conversa.
    await post(
      makePayload({
        sessions: [makeSession({ firstTs: NOW - 240 * 60 * MINUTE, lastTs: NOW })],
        turns: [
          makeTurn({ messageId: 'antigo', ts: NOW - 240 * 60 * MINUTE }),
          makeTurn({ messageId: 'recente', ts: NOW }),
        ],
      }),
    );

    const response = await get({
      accountKey: ACCOUNT_A,
      deviceId: 'device-1',
      sessionId: 'session-1',
    });

    expect(response.body.turns).toHaveLength(2);
  });

  it('exige a chave do time', async () => {
    await post(makePayload());

    const response = await request(harness.app)
      .get('/api/v1/session')
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1', sessionId: 'session-1' });

    expect(response.status).toBe(401);
  });

  it('rejeita chave errada antes de olhar a query', async () => {
    const response = await request(harness.app)
      .get('/api/v1/session')
      .set('x-team-key', 'chave-errada-0123456789abcdefgh')
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1', sessionId: 'session-1' });

    expect(response.status).toBe(401);
  });

  it('rejeita query sem deviceId', async () => {
    const response = await get({ accountKey: ACCOUNT_A, sessionId: 'session-1' });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('rejeita query sem sessionId', async () => {
    const response = await get({ accountKey: ACCOUNT_A, deviceId: 'device-1' });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });
});
