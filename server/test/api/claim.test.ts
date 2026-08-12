import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  ACCOUNT_B,
  TEST_ADMIN_TOKEN,
  TEST_TEAM_KEY,
  createHarness,
  createKeyViaAdmin,
  type Harness,
} from '../support/harness.js';

describe('vinculo explicito da chave com a conta', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness({ legacyKeyMode: 'off' });
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('vincula a conta livre', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await claim(harness, created.key, ACCOUNT_A);

    expect(response.status).toBe(200);
    expect(response.body.claimed).toBe(true);
    expect(harness.keyRepository.findById(created.id)?.accounts).toEqual([ACCOUNT_A]);
  });

  it('libera a leitura logo apos o vinculo', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await claim(harness, created.key, ACCOUNT_A);

    const leitura = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });

    // E o ponto da rota existir: o usuario cola a chave, clica em testar e ja le,
    // sem depender de haver turno novo para o ingest enviar.
    expect(leitura.status).toBe(200);
  });

  it('e idempotente', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    await claim(harness, created.key, ACCOUNT_A);
    const segunda = await claim(harness, created.key, ACCOUNT_A);

    expect(segunda.status).toBe(200);
    expect(segunda.body.claimed).toBe(true);
    expect(harness.keyRepository.findById(created.id)?.accounts).toEqual([ACCOUNT_A]);
  });

  it('recusa conta que ja pertence a outra chave', async () => {
    const dona = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const outra = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await claim(harness, dona.key, ACCOUNT_A);

    const response = await claim(harness, outra.key, ACCOUNT_A);

    expect(response.status).toBe(403);
    expect(response.body.code).toBe('forbidden_account');
    expect(response.body.error).toMatch(/outra chave/);
    expect(harness.keyRepository.findById(outra.id)?.accounts).toEqual([]);
  });

  it('recusa acima do limite de contas', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await claim(harness, created.key, ACCOUNT_A);

    const response = await claim(harness, created.key, ACCOUNT_B);

    expect(response.status).toBe(403);
    expect(response.body.error).toMatch(/limite/);
  });

  it('vincula a segunda conta depois de subir o limite', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await claim(harness, created.key, ACCOUNT_A);

    await request(harness.app)
      .patch(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ maxAccounts: 2 });

    const response = await claim(harness, created.key, ACCOUNT_B);

    expect(response.status).toBe(200);
    expect(harness.keyRepository.findById(created.id)?.accounts).toEqual([ACCOUNT_A, ACCOUNT_B]);
  });

  it('recusa chave revogada', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .delete(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    const response = await claim(harness, created.key, ACCOUNT_A);

    expect(response.status).toBe(401);
  });

  it('recusa sem credencial', async () => {
    const response = await request(harness.app)
      .post('/api/v1/claim')
      .send({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(401);
  });

  it('exige a conta no corpo', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .post('/api/v1/claim')
      .set('x-team-key', created.key)
      .send({});

    expect(response.status).toBe(400);
  });

  it('token de admin nao cria vinculo', async () => {
    const response = await request(harness.app)
      .post('/api/v1/claim')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ accountKey: ACCOUNT_A });

    // Admin le todas as contas e nao representa nenhuma: nao ha vinculo a criar
    // em nome de ninguem.
    expect(response.status).toBe(200);
    expect(harness.keyRepository.ownerOf(ACCOUNT_A)).toBeNull();
  });
});

describe('vinculo com a chave legada aberta', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('responde sem criar vinculo', async () => {
    const response = await claim(harness, TEST_TEAM_KEY, ACCOUNT_A);

    expect(response.status).toBe(200);
    expect(response.body.authorized).toBe(true);
    expect(harness.keyRepository.ownerOf(ACCOUNT_A)).toBeNull();
  });
});

function claim(harness: Harness, key: string, accountKey: string) {
  return request(harness.app).post('/api/v1/claim').set('x-team-key', key).send({ accountKey });
}
