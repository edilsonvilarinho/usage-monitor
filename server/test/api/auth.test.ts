import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { ACCOUNT_A, TEST_TEAM_KEY, createHarness, makePayload, type Harness } from '../support/harness.js';

describe('autenticacao por chave de time', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('libera o healthcheck sem chave', async () => {
    const response = await request(harness.app).get('/api/health');

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: 'ok' });
  });

  it('recusa ingest sem a chave', async () => {
    const response = await request(harness.app).post('/api/v1/ingest').send(makePayload());

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('unauthorized');
  });

  it('recusa ingest com chave errada', async () => {
    const response = await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', 'chave-errada-mas-do-mesmo-tamanho1')
      .send(makePayload());

    expect(response.status).toBe(401);
  });

  it('recusa leitura sem a chave', async () => {
    const response = await request(harness.app)
      .get('/api/v1/team')
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(401);
  });

  it('aceita leitura com a chave correta', async () => {
    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ members: [], rows: [] });
  });

  it('devolve 404 com corpo JSON em rota desconhecida', async () => {
    const response = await request(harness.app).get('/api/nao-existe');

    expect(response.status).toBe(404);
    expect(response.body.code).toBe('not_found');
  });
});
