import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import { ACCOUNT_A, TEST_TEAM_KEY, createHarness, type Harness } from '../support/harness.js';

describe('banco indisponivel', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
    // Banco fechado imita o volume desmontado: a conexao existe, mas nao responde.
    harness.db.close();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('o healthcheck responde 503 com a causa', async () => {
    const response = await request(harness.app).get('/api/health');

    expect(response.status).toBe(503);
    expect(response.body.code).toBe('service_unavailable');
    expect(response.body.error).toMatch(/^Banco indisponivel: /);
  });

  it('erro nao previsto vira 500 sem vazar a mensagem interna', async () => {
    const response = await request(harness.app)
      .get('/api/v1/team')
      .query({ accountKey: ACCOUNT_A })
      .set('x-team-key', TEST_TEAM_KEY);

    expect(response.status).toBe(500);
    expect(response.body).toEqual({ error: 'Erro interno do servidor.', code: 'internal_error' });
  });
});
