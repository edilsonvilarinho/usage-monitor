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

describe('verificacao de vinculo', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness({ legacyKeyMode: 'off' });
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('autoriza chave nova ainda sem vinculo', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await verify(harness, created.key, ACCOUNT_A);

    expect(response.status).toBe(200);
    expect(response.body.authorized).toBe(true);
    expect(response.body.claimed).toBe(false);
    expect(response.body.label).toBe('fulano@empresa.com');
  });

  it('nao cria vinculo ao verificar', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    await verify(harness, created.key, ACCOUNT_A);

    // O vinculo so nasce no ingest: uma leitura que amarrasse conta permitiria
    // adotar contas alheias varrendo uuid, sem nunca provar uso.
    expect(harness.keyRepository.findById(created.id)?.accounts).toEqual([]);
  });

  it('reconhece o vinculo depois do primeiro ingest', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const response = await verify(harness, created.key, ACCOUNT_A);

    expect(response.body.claimed).toBe(true);
    expect(response.body.claimedAccounts).toBe(1);
  });

  it('recusa conta que ja pertence a outra chave', async () => {
    const dona = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const outra = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await ingestWith(harness, dona.key, ACCOUNT_A);

    const response = await verify(harness, outra.key, ACCOUNT_A);

    expect(response.status).toBe(403);
    expect(response.body.code).toBe('forbidden_account');
    expect(response.body.error).toMatch(/outra chave/);
  });

  it('recusa segunda conta quando o limite ja foi atingido', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const response = await verify(harness, created.key, ACCOUNT_B);

    expect(response.status).toBe(403);
    expect(response.body.error).toMatch(/limite/);
  });

  it('autoriza a segunda conta depois de subir o limite', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    await request(harness.app)
      .patch(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ maxAccounts: 2 });

    const response = await verify(harness, created.key, ACCOUNT_B);

    expect(response.status).toBe(200);
    expect(response.body.authorized).toBe(true);
  });

  it('recusa chave desconhecida', async () => {
    const response = await verify(harness, 'chave-inexistente-do-mesmo-tamanho', ACCOUNT_A);

    expect(response.status).toBe(401);
  });

  it('recusa chave revogada', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await request(harness.app)
      .delete(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    const response = await verify(harness, created.key, ACCOUNT_A);

    expect(response.status).toBe(401);
  });

  it('recusa sem credencial', async () => {
    const response = await request(harness.app)
      .get('/api/v1/verify')
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(401);
  });

  it('exige a conta na query', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .get('/api/v1/verify')
      .set('x-team-key', created.key);

    expect(response.status).toBe(400);
  });

  it('autoriza o token de admin para qualquer conta', async () => {
    const response = await request(harness.app)
      .get('/api/v1/verify')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .query({ accountKey: ACCOUNT_A });

    expect(response.status).toBe(200);
    expect(response.body.authorized).toBe(true);
  });
});

describe('verificacao com a chave legada aberta', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('responde autorizada, porque ela de fato le tudo', async () => {
    const response = await verify(harness, TEST_TEAM_KEY, ACCOUNT_A);

    // Informar o contrario faria o app avisar de um problema que nao existe
    // naquele deploy: o modo aberto e o comportamento anterior, preservado.
    expect(response.status).toBe(200);
    expect(response.body.authorized).toBe(true);
    expect(response.body.claimed).toBe(true);
  });
});

function verify(harness: Harness, key: string, accountKey: string) {
  return request(harness.app).get('/api/v1/verify').set('x-team-key', key).query({ accountKey });
}

function ingestWith(harness: Harness, key: string, accountKey: string) {
  return request(harness.app)
    .post('/api/v1/ingest')
    .set('x-team-key', key)
    .send(makePayload({ accountKey }));
}
