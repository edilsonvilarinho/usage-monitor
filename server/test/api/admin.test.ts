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

describe('rotas administrativas', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('valida o token pelo ping', async () => {
    const response = await request(harness.app)
      .get('/api/admin/v1/ping')
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ status: 'ok' });
  });

  it('recusa ping sem token', async () => {
    const response = await request(harness.app).get('/api/admin/v1/ping');

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('unauthorized');
  });

  it('recusa ping com token errado', async () => {
    const response = await request(harness.app)
      .get('/api/admin/v1/ping')
      .set('x-admin-token', 'token-errado-mas-do-mesmo-tamanho');

    expect(response.status).toBe(401);
  });

  it('nao expoe as rotas quando nao ha token configurado', async () => {
    const semAdmin = createHarness({ adminToken: null, keySecret: null });

    try {
      const response = await request(semAdmin.app)
        .get('/api/admin/v1/ping')
        .set('x-admin-token', TEST_ADMIN_TOKEN);

      // Nao e 401: a rota simplesmente nao existe, e um deploy que nunca pediu
      // administracao nao ganha superficie nova.
      expect(response.status).toBe(404);
      expect(response.body.code).toBe('not_found');
    } finally {
      semAdmin.cleanup();
    }
  });

  it('cria a chave e a devolve crua', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    expect(created.label).toBe('fulano@empresa.com');
    expect(created.maxAccounts).toBe(1);
    expect(created.accounts).toEqual([]);
    expect(created.key).toBeTruthy();
  });

  it('lista as chaves com a chave crua legivel', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .get('/api/admin/v1/keys')
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    // A lista e a fonte de "quem tem qual chave": sem a chave crua aqui o admin
    // teria de guarda-la fora do sistema ou regerar a cada consulta.
    expect(response.body.keys).toHaveLength(1);
    expect(response.body.keys[0].key).toBe(created.key);
  });

  it('recusa criar chave sem rotulo', async () => {
    const response = await request(harness.app)
      .post('/api/admin/v1/keys')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ label: '   ' });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('altera rotulo e limite', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .patch(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ label: 'sicrano@empresa.com', maxAccounts: 2 });

    expect(response.status).toBe(200);
    expect(response.body.label).toBe('sicrano@empresa.com');
    expect(response.body.maxAccounts).toBe(2);
  });

  it('recusa patch vazio', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .patch(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({});

    expect(response.status).toBe(400);
  });

  it('recusa reduzir o limite abaixo do que ja foi vinculado', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com', 2);
    await ingestWith(harness, created.key, ACCOUNT_A);
    await ingestWith(harness, created.key, ACCOUNT_B, 'session-b', 'msg-b');

    const response = await request(harness.app)
      .patch(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ maxAccounts: 1 });

    expect(response.status).toBe(400);
    expect(response.body.error).toMatch(/2 conta/);
  });

  it('regera a chave mantendo o vinculo', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const response = await request(harness.app)
      .post(`/api/admin/v1/keys/${created.id}/regenerate`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body.key).not.toBe(created.key);
    expect(response.body.accounts).toEqual([ACCOUNT_A]);

    const comChaveAntiga = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });
    expect(comChaveAntiga.status).toBe(401);
  });

  it('revoga sem apagar os dados enviados', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const revoked = await request(harness.app)
      .delete(`/api/admin/v1/keys/${created.id}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    expect(revoked.status).toBe(200);
    expect(revoked.body.revokedAt).not.toBeNull();

    const comChaveRevogada = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });
    expect(comChaveRevogada.status).toBe(401);

    // Tirar o acesso e apagar historico sao decisoes diferentes: o admin
    // continua vendo o que aquela maquina ja enviou.
    const comAdmin = await request(harness.app)
      .get('/api/v1/team')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .query({ accountKey: ACCOUNT_A });
    expect(comAdmin.body.rows).toHaveLength(1);
  });

  it('desfaz um vinculo errado e libera a conta para outra chave', async () => {
    const errada = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const certa = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await ingestWith(harness, errada.key, ACCOUNT_A);

    const removed = await request(harness.app)
      .delete(`/api/admin/v1/keys/${errada.id}/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(removed.status).toBe(200);
    expect(removed.body.accounts).toEqual([]);

    const reivindicada = await ingestWith(harness, certa.key, ACCOUNT_A, 'session-c', 'msg-c');
    expect(reivindicada.status).toBe(200);
  });

  it('devolve 404 ao desfazer vinculo inexistente', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const response = await request(harness.app)
      .delete(`/api/admin/v1/keys/${created.id}/accounts/${ACCOUNT_B}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(404);
  });

  it('devolve 404 para chave desconhecida', async () => {
    const response = await request(harness.app)
      .patch('/api/admin/v1/keys/nao-existe')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ label: 'qualquer' });

    expect(response.status).toBe(404);
  });
});

describe('visao global do admin', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('agrupa por conta e rotula pela chave dona', async () => {
    const primeira = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const segunda = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await ingestWith(harness, primeira.key, ACCOUNT_A);
    await ingestWith(harness, segunda.key, ACCOUNT_B, 'session-b', 'msg-b');

    const response = await request(harness.app)
      .get('/api/admin/v1/overview')
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    const contas = response.body.accounts as Array<{
      accountKey: string;
      label: string | null;
      members: unknown[];
      rows: unknown[];
    }>;

    expect(contas).toHaveLength(2);
    const contaA = contas.find((conta) => conta.accountKey === ACCOUNT_A);
    expect(contaA?.label).toBe('fulano@empresa.com');
    expect(contaA?.members).toHaveLength(1);
    expect(contaA?.rows).toHaveLength(1);
  });

  it('mostra conta sem chave emitida, so com o uuid', async () => {
    // Conta que entrou pela chave legada: existe nos dados e nao tem dona.
    await ingestWith(harness, TEST_TEAM_KEY, ACCOUNT_A);

    const response = await request(harness.app)
      .get('/api/admin/v1/overview')
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.body.accounts).toHaveLength(1);
    expect(response.body.accounts[0].accountKey).toBe(ACCOUNT_A);
    expect(response.body.accounts[0].label).toBeNull();
  });

  it('recorta pelo since sem esconder a conta', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const response = await request(harness.app)
      .get('/api/admin/v1/overview')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .query({ since: Date.UTC(2030, 0, 1) });

    // Quem nao consumiu na janela e informacao, nao ruido: a conta permanece com
    // o integrante e sem linhas de uso.
    expect(response.body.accounts).toHaveLength(1);
    expect(response.body.accounts[0].members).toHaveLength(1);
    expect(response.body.accounts[0].rows).toEqual([]);
  });

  it('exige o token de admin', async () => {
    const response = await request(harness.app).get('/api/admin/v1/overview');

    expect(response.status).toBe(401);
  });
});

describe('remocao de conta inteira', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('apaga integrantes, sessoes e turnos e tira a conta da visao global', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const removed = await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(removed.status).toBe(200);
    expect(removed.body).toEqual({
      deletedTurns: 1,
      deletedSessions: 1,
      deletedMembers: 1,
      unlinkedKeys: 1,
    });

    // A visao global e derivada de team_members e team_turns: sem eles a conta
    // deixa de existir na lista, e nao vira uma linha sem rotulo.
    const overview = await request(harness.app)
      .get('/api/admin/v1/overview')
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    expect(overview.body.accounts).toEqual([]);
  });

  it('nao encosta na outra conta da mesma maquina', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com', 2);
    await ingestWith(harness, created.key, ACCOUNT_A);
    await ingestWith(harness, created.key, ACCOUNT_B, 'session-b', 'msg-b');

    await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    const overview = await request(harness.app)
      .get('/api/admin/v1/overview')
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(overview.body.accounts).toHaveLength(1);
    expect(overview.body.accounts[0].accountKey).toBe(ACCOUNT_B);
    expect(overview.body.accounts[0].rows).toHaveLength(1);
  });

  it('solta o vinculo e libera a conta para outra chave', async () => {
    const antiga = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    const nova = await createKeyViaAdmin(harness, 'sicrano@empresa.com');
    await ingestWith(harness, antiga.key, ACCOUNT_A);

    await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    const chaves = await request(harness.app)
      .get('/api/admin/v1/keys')
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    const antigaDepois = chaves.body.keys.find(
      (key: { id: string }) => key.id === antiga.id,
    );
    expect(antigaDepois.accounts).toEqual([]);

    // O slot volta a ficar livre: sem isso a conta ficaria presa a uma chave que
    // nao a usa mais, e nenhuma outra poderia adota-la.
    const reivindicada = await ingestWith(harness, nova.key, ACCOUNT_A, 'session-c', 'msg-c');
    expect(reivindicada.status).toBe(200);
  });

  it('e idempotente: conta desconhecida responde 200 com zeros', async () => {
    const response = await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_B}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body).toEqual({
      deletedTurns: 0,
      deletedSessions: 0,
      deletedMembers: 0,
      unlinkedKeys: 0,
    });
  });

  it('recusa sem o token de admin', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const response = await request(harness.app).delete(`/api/admin/v1/accounts/${ACCOUNT_A}`);

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('unauthorized');
  });

  it('recusa a chave de time, mesmo a legada em modo aberto', async () => {
    await ingestWith(harness, TEST_TEAM_KEY, ACCOUNT_A);

    // Chave legada le tudo, mas apagar conta e ato de administracao: a rota vive
    // sob /admin e so o x-admin-token a monta.
    const response = await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-team-key', TEST_TEAM_KEY);

    expect(response.status).toBe(401);
  });

  it('nao existe num deploy sem token de admin', async () => {
    const semAdmin = createHarness({ adminToken: null, keySecret: null });

    try {
      const response = await request(semAdmin.app)
        .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
        .set('x-admin-token', TEST_ADMIN_TOKEN);

      expect(response.status).toBe(404);
      expect(response.body.code).toBe('not_found');
    } finally {
      semAdmin.cleanup();
    }
  });
});

describe('remocao administrativa de sessao', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  const route = (accountKey: string, deviceId: string, sessionId: string) =>
    `/api/admin/v1/accounts/${accountKey}/members/${deviceId}/sessions/${sessionId}`;

  it('apaga somente a sessao escolhida e preserva membro, outras sessoes e contas', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com', 2);
    await ingestWith(harness, created.key, ACCOUNT_A, 'session-1', 'msg-1');
    await ingestWith(harness, created.key, ACCOUNT_A, 'session-2', 'msg-2');
    await ingestWith(harness, created.key, ACCOUNT_B, 'session-1', 'msg-b');

    const removed = await request(harness.app)
      .delete(route(ACCOUNT_A, 'device-1', 'session-1'))
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(removed.status).toBe(200);
    expect(removed.body).toEqual({ deletedTurns: 1, deletedSessions: 1 });

    const accountA = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });
    expect(accountA.body.members).toHaveLength(1);
    expect(accountA.body.rows.map((row: { sessionId: string }) => row.sessionId)).toEqual([
      'session-2',
    ]);

    const accountB = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_B });
    expect(accountB.body.rows.map((row: { sessionId: string }) => row.sessionId)).toEqual([
      'session-1',
    ]);
  });

  it('e idempotente e nao apaga com deviceId divergente', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);

    const wrongDevice = await request(harness.app)
      .delete(route(ACCOUNT_A, 'device-errado', 'session-1'))
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    expect(wrongDevice.body).toEqual({ deletedTurns: 0, deletedSessions: 0 });

    await request(harness.app)
      .delete(route(ACCOUNT_A, 'device-1', 'session-1'))
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    const repeated = await request(harness.app)
      .delete(route(ACCOUNT_A, 'device-1', 'session-1'))
      .set('x-admin-token', TEST_ADMIN_TOKEN);
    expect(repeated.body).toEqual({ deletedTurns: 0, deletedSessions: 0 });
  });

  it('permite que atividade futura recrie a sessao sem recuperar turnos antigos', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A, 'session-1', 'msg-antiga');
    await request(harness.app)
      .delete(route(ACCOUNT_A, 'device-1', 'session-1'))
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    await ingestWith(harness, created.key, ACCOUNT_A, 'session-1', 'msg-nova');
    const detail = await request(harness.app)
      .get('/api/v1/session')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1', sessionId: 'session-1' });

    expect(detail.status).toBe(200);
    expect(detail.body.turns.map((turn: { messageId: string }) => turn.messageId)).toEqual([
      'msg-nova',
    ]);
  });

  it('recusa ausencia de token e a chave dona da conta', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    await ingestWith(harness, created.key, ACCOUNT_A);
    const target = route(ACCOUNT_A, 'device-1', 'session-1');

    const missing = await request(harness.app).delete(target);
    const owner = await request(harness.app).delete(target).set('x-team-key', created.key);

    expect(missing.status).toBe(401);
    expect(owner.status).toBe(401);
    const remaining = await request(harness.app)
      .get('/api/v1/session')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A, deviceId: 'device-1', sessionId: 'session-1' });
    expect(remaining.status).toBe(200);
  });
});

function ingestWith(
  harness: Harness,
  key: string,
  accountKey: string,
  sessionId = 'session-1',
  messageId = 'msg-1',
) {
  const payload = makePayload({ accountKey });
  return request(harness.app)
    .post('/api/v1/ingest')
    .set('x-team-key', key)
    .send({
      ...payload,
      sessions: payload.sessions.map((session) => ({ ...session, sessionId })),
      turns: payload.turns.map((turn) => ({ ...turn, sessionId, messageId })),
    });
}
