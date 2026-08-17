import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  ACCOUNT_B,
  TEST_ADMIN_TOKEN,
  TEST_TEAM_KEY,
  createHarness,
  createKeyViaAdmin,
  makePayload,
  type Harness,
} from '../support/harness.js';

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
    expect(response.body).toEqual({ members: [], rows: [], activity: [] });
  });

  it('devolve 404 com corpo JSON em rota desconhecida', async () => {
    const response = await request(harness.app).get('/api/nao-existe');

    expect(response.status).toBe(404);
    expect(response.body.code).toBe('not_found');
  });
});

describe('isolamento por chave de time', () => {
  let harness: Harness;

  beforeEach(() => {
    // Sem o modo aberto a chave legada nao vale mais: e este o estado em que o
    // isolamento entre times passa a existir de fato.
    harness = createHarness({ legacyKeyMode: 'off' });
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('rejeita a chave legada com o modo desligado', async () => {
    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(401);
  });

  it('vincula a conta no primeiro ingest e libera a leitura dela', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const ingest = await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));
    expect(ingest.status).toBe(200);

    const leitura = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });
    expect(leitura.status).toBe(200);
  });

  it('recusa leitura de conta que a chave nao cobre', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_B });

    // 403 e nao 401: a chave vale, o conserto e outro — pedir o vinculo certo ao
    // administrador, e nao conferir se copiou a chave errada.
    expect(response.status).toBe(403);
    expect(response.body.code).toBe('forbidden_account');
  });

  it('diz que falta vincular quando a conta esta livre', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });

    // Quem colou a chave certa e ainda nao sincronizou precisa de instrucao, nao
    // da mesma recusa generica de quem colou a chave de outra pessoa.
    expect(response.status).toBe(403);
    expect(response.body.error).toMatch(/ainda nao foi vinculada/);
  });

  it('diz que a conta e de outra chave', async () => {
    const dona = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const outra = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await request(harness.app)
      .post('/api/v1/claim')
      .set('x-team-key', dona.key)
      .send({ accountKey: ACCOUNT_A });

    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', outra.key)
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(403);
    expect(response.body.error).toMatch(/outra chave/);
  });

  it('diz que o limite de contas foi atingido', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .post('/api/v1/claim')
      .set('x-team-key', created.key)
      .send({ accountKey: ACCOUNT_A });

    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_B });

    expect(response.status).toBe(403);
    expect(response.body.error).toMatch(/limite/);
  });

  it('nao vincula conta nova pela leitura', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const leitura = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });

    expect(leitura.status).toBe(403);
    expect(harness.keyRepository.findById(created.id)?.accounts).toEqual([]);
  });

  it('recusa ingest de conta que ja pertence a outra chave', async () => {
    const dona = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const outra = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', dona.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    const response = await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', outra.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    expect(response.status).toBe(403);
  });

  it('recusa segunda conta acima do limite e aceita depois de subi-lo', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    const recusado = await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_B }));
    expect(recusado.status).toBe(403);

    await request(harness.app)
      .patch(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ maxAccounts: 2 });

    const aceito = await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_B }));
    expect(aceito.status).toBe(200);
  });

  it('marca o ultimo uso da chave no ingest', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    harness.setNow(Date.UTC(2026, 7, 12, 9, 0, 0));

    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    expect(harness.keyRepository.findById(created.id)?.lastUsedAt).toBe(
      Date.UTC(2026, 7, 12, 9, 0, 0),
    );
  });
});

describe('token de admin como credencial de leitura', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness({ legacyKeyMode: 'off' });
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('le qualquer conta sem chave de time', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    const response = await request(harness.app)
      .get('/api/v1/team')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(200);
    expect(response.body.members).toHaveLength(1);
  });

  it('le o detalhe de sessao de outra conta', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', created.key)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    const response = await request(harness.app)
      .get('/api/v1/session')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1', sessionId: 'session-1' });

    expect(response.status).toBe(200);
    expect(response.body.turns).toHaveLength(1);
  });

  it('nao serve para ingest', async () => {
    const response = await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send(makePayload({ accountKey: ACCOUNT_A }));

    // Admin le, nao escreve dado de uso em nome de ninguem: sem chave de time a
    // requisicao cai no 401 comum.
    expect(response.status).toBe(401);
  });
});
