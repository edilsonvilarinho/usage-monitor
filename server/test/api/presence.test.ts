import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  ACCOUNT_B,
  createHarness,
  createKeyViaAdmin,
  makePayload,
  TEST_ADMIN_TOKEN,
  TEST_TEAM_KEY,
  type Harness,
} from '../support/harness.js';

interface PresenceMember {
  deviceId: string;
  alias: string;
  hostName?: string | null;
  organizationUuid?: string | null;
  organizationName?: string | null;
}

function makeMember(overrides: Partial<PresenceMember> = {}): PresenceMember {
  return {
    deviceId: 'device-1',
    alias: 'edilson',
    hostName: 'DESKTOP-A1',
    organizationUuid: 'org-1',
    organizationName: 'Empresa',
    ...overrides,
  };
}

describe('POST /api/v1/presence', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  function post(body: unknown, key = TEST_TEAM_KEY) {
    return request(harness.app).post('/api/v1/presence').set('x-team-key', key).send(body);
  }

  function readMember(accountKey = ACCOUNT_A, deviceId = 'device-1') {
    return harness.db
      .prepare(
        `SELECT alias, host_name AS hostName, organization_uuid AS organizationUuid,
                organization_name AS organizationName, last_seen_at AS lastSeenAt
           FROM team_members WHERE account_key = ? AND device_id = ?`,
      )
      .get(accountKey, deviceId) as
      | {
          alias: string;
          hostName: string | null;
          organizationUuid: string | null;
          organizationName: string | null;
          lastSeenAt: number;
        }
      | undefined;
  }

  it('carimba last_seen_at com o relogio do servidor', async () => {
    harness.setNow(1_700_000_000_000);

    const response = await post({ accountKey: ACCOUNT_A, member: makeMember() });

    expect(response.status).toBe(200);
    expect(readMember()?.lastSeenAt).toBe(1_700_000_000_000);
  });

  it('devolve o relogio do servidor no corpo', async () => {
    harness.setNow(1_700_000_500_000);

    const response = await post({ accountKey: ACCOUNT_A, member: makeMember() });

    // E dele que o cliente deduz o proprio desvio de relogio.
    expect(response.body).toEqual({ lastSeenAt: 1_700_000_500_000 });
  });

  it('cria o integrante quando ele ainda nao existe', async () => {
    const response = await post({
      accountKey: ACCOUNT_A,
      member: makeMember({ deviceId: 'device-novo', alias: 'fulano', hostName: 'NOTE-9' }),
    });

    expect(response.status).toBe(200);
    const member = readMember(ACCOUNT_A, 'device-novo');
    expect(member?.alias).toBe('fulano');
    expect(member?.hostName).toBe('NOTE-9');
  });

  it('nunca recua last_seen_at', async () => {
    harness.setNow(1_700_000_900_000);
    await post({ accountKey: ACCOUNT_A, member: makeMember() });

    // Relogio do servidor volta atras: o MAX() do upsert tem de segurar o maior.
    harness.setNow(1_700_000_100_000);
    await post({ accountKey: ACCOUNT_A, member: makeMember() });

    expect(readMember()?.lastSeenAt).toBe(1_700_000_900_000);
  });

  it('preserva host_name e organizacao quando o corpo os traz nulos', async () => {
    await post({ accountKey: ACCOUNT_A, member: makeMember() });

    await post({
      accountKey: ACCOUNT_A,
      member: {
        deviceId: 'device-1',
        alias: 'edilson',
        hostName: null,
        organizationUuid: null,
        organizationName: null,
      },
    });

    const member = readMember();
    expect(member?.hostName).toBe('DESKTOP-A1');
    expect(member?.organizationUuid).toBe('org-1');
    expect(member?.organizationName).toBe('Empresa');
  });

  it('atualiza o apelido, como o ingest faz', async () => {
    await post({ accountKey: ACCOUNT_A, member: makeMember() });
    await post({ accountKey: ACCOUNT_A, member: makeMember({ alias: 'edilson-novo' }) });

    expect(readMember()?.alias).toBe('edilson-novo');
  });

  it('nao cria sessao nem turno', async () => {
    await post({ accountKey: ACCOUNT_A, member: makeMember() });

    const sessions = harness.db.prepare('SELECT COUNT(*) AS total FROM team_sessions').get() as {
      total: number;
    };
    const turns = harness.db.prepare('SELECT COUNT(*) AS total FROM team_turns').get() as {
      total: number;
    };

    expect(sessions.total).toBe(0);
    expect(turns.total).toBe(0);
  });

  it('recusa o token de admin', async () => {
    // Admin le, mas nao declara presenca em nome de ninguem: `allowClaim` desliga
    // o ramo do admin em `authorize`, entao a resposta e 401 e nao 403.
    const response = await request(harness.app)
      .post('/api/v1/presence')
      .set('x-admin-token', TEST_ADMIN_TOKEN)
      .send({ accountKey: ACCOUNT_A, member: makeMember() });

    expect(response.status).toBe(401);
    expect(response.body.code).toBe('unauthorized');
  });

  it('recusa requisicao sem credencial', async () => {
    const response = await request(harness.app)
      .post('/api/v1/presence')
      .send({ accountKey: ACCOUNT_A, member: makeMember() });

    expect(response.status).toBe(401);
  });

  it('vincula a conta a chave na primeira batida', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    const batida = await post({ accountKey: ACCOUNT_A, member: makeMember() }, created.key);
    expect(batida.status).toBe(200);

    // A leitura so passa se o vinculo nasceu na batida.
    const leitura = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', created.key)
      .query({ accountKey: ACCOUNT_A });

    expect(leitura.status).toBe(200);
  });

  it('recusa chave de outra conta com 403 forbidden_account', async () => {
    const dona = await createKeyViaAdmin(harness, 'dona@empresa.com');
    await post({ accountKey: ACCOUNT_A, member: makeMember() }, dona.key);

    const intrusa = await createKeyViaAdmin(harness, 'intrusa@empresa.com');
    const response = await post({ accountKey: ACCOUNT_A, member: makeMember() }, intrusa.key);

    expect(response.status).toBe(403);
    expect(response.body.code).toBe('forbidden_account');
  });

  it('recusa apelido em branco com 400 validation_error', async () => {
    const response = await post({
      accountKey: ACCOUNT_A,
      member: makeMember({ alias: '   ' }),
    });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
    expect(response.body.error).toContain('Corpo de presenca invalido');
  });

  it('recusa corpo sem conta com 400 na chave legada', async () => {
    // A chave legada e resolvida antes de o `authorize` ler a conta, entao quem
    // barra aqui e o zod.
    const response = await post({ member: makeMember() });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('recusa corpo sem conta com 403 na chave emitida', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');

    // Com chave por conta a autorizacao precisa do escopo e barra antes do zod:
    // um pedido sem conta nao pode passar por um caminho que decide acesso.
    const response = await post({ member: makeMember() }, created.key);

    expect(response.status).toBe(403);
  });

  it('marca last_used_at da chave', async () => {
    const created = await createKeyViaAdmin(harness, 'fulano@empresa.com');
    harness.setNow(1_700_000_777_000);

    await post({ accountKey: ACCOUNT_A, member: makeMember() }, created.key);

    const row = harness.db
      .prepare('SELECT last_used_at AS lastUsedAt FROM team_keys WHERE id = ?')
      .get(created.id) as { lastUsedAt: number | null };

    expect(row.lastUsedAt).toBe(1_700_000_777_000);
  });

  it('a chave legada em modo open marca presenca', async () => {
    const response = await post({ accountKey: ACCOUNT_B, member: makeMember() });

    expect(response.status).toBe(200);
    expect(readMember(ACCOUNT_B)).toBeDefined();
  });

  it('legacy off recusa a chave legada', async () => {
    harness.cleanup();
    harness = createHarness({ legacyKeyMode: 'off' });

    const response = await post({ accountKey: ACCOUNT_A, member: makeMember() });

    expect(response.status).toBe(401);
  });

  it('a presenca aparece em lastSeenAt de GET /v1/team', async () => {
    // Ingest primeiro, para haver linha de consumo; depois a batida avanca so o
    // carimbo — e a leitura que a tela de presenca usa.
    harness.setNow(1_700_000_000_000);
    await request(harness.app)
      .post('/api/v1/ingest')
      .set('x-team-key', TEST_TEAM_KEY)
      .send(makePayload());

    harness.setNow(1_700_000_600_000);
    await post({ accountKey: ACCOUNT_A, member: makeMember() });

    const leitura = await request(harness.app)
      .get('/api/v1/team')
      .set('x-team-key', TEST_TEAM_KEY)
      .query({ accountKey: ACCOUNT_A });

    expect(leitura.body.members[0].lastSeenAt).toBe(1_700_000_600_000);
  });
});
