import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';
import {
  ACCOUNT_A,
  createHarness,
  makePayload,
  TEST_ADMIN_TOKEN,
  TEST_REPORT_TOKEN,
  TEST_TEAM_KEY,
  type Harness,
} from '../support/harness.js';

let harness: Harness | null = null;

function start(overrides: Parameters<typeof createHarness>[0] = {}): Harness {
  harness = createHarness(overrides);
  return harness;
}

afterEach(() => {
  harness?.cleanup();
  harness = null;
});

describe('GET /api/v1/pricing', () => {
  it('publica a tabela para o token de relatório', async () => {
    const { app } = start();

    const response = await request(app).get('/api/v1/pricing').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body.version).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(response.body.models.length).toBeGreaterThan(0);
    expect(response.body.syntheticModelId).toBe('<synthetic>');
    expect(response.body.matchRule).toContain('indisponivel');
  });

  // A tarifa corrigida na A01 tem de chegar ao consumidor: e a mesma que o app
  // usa, e um numero diferente aqui significaria duas tabelas de novo.
  it('publica o Sonnet 5 a 2/10 por milhão, em micros', async () => {
    const { app } = start();

    const response = await request(app).get('/api/v1/pricing').set('x-report-key', TEST_REPORT_TOKEN);

    const sonnet5 = response.body.models.find(
      (entry: { prefix: string }) => entry.prefix === 'claude-sonnet-5',
    );
    expect(sonnet5).toEqual({
      prefix: 'claude-sonnet-5',
      inputMicrosPerMillion: 2_000_000,
      outputMicrosPerMillion: 10_000_000,
    });
  });

  // Razoes inteiras: publicar 0.1 e 1.25 convidaria o consumidor a introduzir
  // erro de ponto flutuante que a aritmetica em micros do app nao tem.
  it('publica os multiplicadores de cache como razões inteiras', async () => {
    const { app } = start();

    const response = await request(app).get('/api/v1/pricing').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.body.cacheMultipliers).toEqual({
      read: { numerator: 1, denominator: 10 },
      write5m: { numerator: 5, denominator: 4 },
      write1h: { numerator: 2, denominator: 1 },
    });
  });

  it('aceita o token de administração na mesma rota', async () => {
    const { app } = start();

    const response = await request(app).get('/api/v1/pricing').set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
  });

  // Chave de time e por conta; leitura global feita com ela devolveria dado das
  // outras contas. `requireGlobalRead` nao a aceita, e este teste e o portao.
  it('recusa a chave de time', async () => {
    const { app } = start();

    const response = await request(app).get('/api/v1/pricing').set('x-team-key', TEST_TEAM_KEY);

    expect(response.status).toBe(401);
  });

  it('recusa requisição sem credencial', async () => {
    const { app } = start();

    expect((await request(app).get('/api/v1/pricing')).status).toBe(401);
  });

  // A rota existe mesmo sem a variavel definida: rota ausente faria "credencial
  // errada" e "servidor sem TEAM_REPORT_TOKEN" chegarem como o mesmo 404.
  it('responde 401, e não 404, quando o servidor não tem token de relatório', async () => {
    const { app } = start({ reportToken: null });

    const response = await request(app).get('/api/v1/pricing').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('unauthorized');
  });
});

describe('alcance do token de relatório', () => {
  it('lê a conta de qualquer chave em /v1/team, sem escopo', async () => {
    const { app } = start();
    await request(app).post('/api/v1/ingest').set('x-team-key', TEST_TEAM_KEY).send(makePayload());

    const response = await request(app)
      .get('/api/v1/team')
      .query({ accountKey: ACCOUNT_A })
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body.rows.length).toBeGreaterThan(0);
  });

  it('lê a visão global em /admin/v1/overview', async () => {
    const { app } = start();
    await request(app).post('/api/v1/ingest').set('x-team-key', TEST_TEAM_KEY).send(makePayload());

    const response = await request(app)
      .get('/api/admin/v1/overview')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body.accounts.length).toBeGreaterThan(0);
  });

  // A listagem devolve material de credencial. Ela continua so no `x-admin-token`
  // -- ler relatorio nao e administrar chave.
  it('não lê a listagem de chaves', async () => {
    const { app } = start();

    const response = await request(app).get('/api/admin/v1/keys').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(401);
  });

  // A diferenca que a #106 pediu: o consumidor pode LER e nao pode DESTRUIR.
  it('não escreve e não apaga', async () => {
    const { app } = start();
    await request(app).post('/api/v1/ingest').set('x-team-key', TEST_TEAM_KEY).send(makePayload());

    const ingest = await request(app)
      .post('/api/v1/ingest')
      .set('x-report-key', TEST_REPORT_TOKEN)
      .send(makePayload());
    expect(ingest.status).toBe(401);

    const presence = await request(app)
      .post('/api/v1/presence')
      .set('x-report-key', TEST_REPORT_TOKEN)
      .send({ accountKey: ACCOUNT_A, deviceId: 'device-1' });
    expect(presence.status).toBe(401);

    const member = await request(app)
      .delete('/api/v1/member')
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1' })
      .set('x-report-key', TEST_REPORT_TOKEN);
    expect(member.status).toBe(401);

    const account = await request(app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-report-key', TEST_REPORT_TOKEN);
    expect(account.status).toBe(401);

    // Nada foi apagado por nenhuma das tentativas.
    const after = await request(app)
      .get('/api/v1/team')
      .query({ accountKey: ACCOUNT_A })
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    expect(after.body.rows.length).toBeGreaterThan(0);
  });

  it('não vale nada quando o servidor não define TEAM_REPORT_TOKEN', async () => {
    const { app } = start({ reportToken: null });
    await request(app).post('/api/v1/ingest').set('x-team-key', TEST_TEAM_KEY).send(makePayload());

    const team = await request(app)
      .get('/api/v1/team')
      .query({ accountKey: ACCOUNT_A })
      .set('x-report-key', TEST_REPORT_TOKEN);
    expect(team.status).toBe(401);

    const overview = await request(app)
      .get('/api/admin/v1/overview')
      .set('x-report-key', TEST_REPORT_TOKEN);
    expect(overview.status).toBe(401);
  });
});
