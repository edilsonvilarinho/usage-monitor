import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  TEST_ADMIN_TOKEN,
  TEST_TEAM_KEY,
  createHarness,
  createKeyViaAdmin,
  makePayload,
  type Harness,
} from '../support/harness.js';

const CORPORATE = 'helio.sales@empresa.com';
const PERSONAL = 'pessoal@gmail.com';

function ingest(harness: Harness, key: string, accountEmail?: string) {
  const payload = makePayload({ accountKey: ACCOUNT_A });
  return request(harness.app)
    .post('/api/v1/ingest')
    .set('x-team-key', key)
    .send(accountEmail === undefined ? payload : { ...payload, accountEmail });
}

function presence(harness: Harness, key: string, accountEmail?: string) {
  const body: Record<string, unknown> = {
    accountKey: ACCOUNT_A,
    member: { deviceId: 'device-1', alias: 'romero', hostName: 'NOTE-LAT-015' },
  };
  if (accountEmail !== undefined) {
    body.accountEmail = accountEmail;
  }
  return request(harness.app).post('/api/v1/presence').set('x-team-key', key).send(body);
}

function readTeam(harness: Harness, key: string) {
  return request(harness.app)
    .get('/api/v1/team')
    .set('x-team-key', key)
    .query({ accountKey: ACCOUNT_A });
}

function verify(harness: Harness, key: string, accountEmail?: string) {
  const query: Record<string, string> = { accountKey: ACCOUNT_A };
  if (accountEmail !== undefined) {
    query.accountEmail = accountEmail;
  }
  return request(harness.app).get('/api/v1/verify').set('x-team-key', key).query(query);
}

function claim(harness: Harness, key: string, accountEmail?: string) {
  const body: Record<string, unknown> = { accountKey: ACCOUNT_A };
  if (accountEmail !== undefined) {
    body.accountEmail = accountEmail;
  }
  return request(harness.app).post('/api/v1/claim').set('x-team-key', key).send(body);
}

function renameKey(harness: Harness, keyId: string, label: string) {
  return request(harness.app)
    .patch(`/api/admin/v1/keys/${keyId}`)
    .set('x-admin-token', TEST_ADMIN_TOKEN)
    .send({ label });
}

describe('portao do rotulo', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('aceita a conta cujo e-mail esta no rotulo', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE);

    const response = await ingest(harness, key.key, CORPORATE);

    expect(response.status).toBe(200);
  });

  it('aceita a segunda conta de um rotulo com dois e-mails', async () => {
    const key = await createKeyViaAdmin(harness, `outra@empresa.com, ${CORPORATE}`, 2);

    const response = await ingest(harness, key.key, CORPORATE);

    expect(response.status).toBe(200);
  });

  // O defeito da issue #179: a conta pessoal ocupava um slot da chave de outra
  // pessoa e o consumo dela entrava nos totais da empresa.
  it('recusa a conta que nao esta no rotulo, nomeando as duas pontas', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    const response = await ingest(harness, key.key, PERSONAL);

    expect(response.status).toBe(403);
    expect(response.body.code).toBe('forbidden_account');
    expect(response.body.error).toContain(CORPORATE);
    expect(response.body.error).toContain(PERSONAL);
  });

  it('recusa tambem a batida de presenca', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    const response = await presence(harness, key.key, PERSONAL);

    expect(response.status).toBe(403);
  });

  // A decisao que torna o portao util: a conta intrusa ja estava vinculada
  // quando ele foi escrito, e uma verificacao so no caminho do `claim` a
  // deixaria sincronizando para sempre.
  it('recusa vinculo que ja existia', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    expect((await ingest(harness, key.key, PERSONAL)).status).toBe(200);

    await renameKey(harness, key.id, CORPORATE);

    const response = await ingest(harness, key.key, PERSONAL);
    expect(response.status).toBe(403);
  });

  // As leituras nao carregam e-mail: quem responde por elas e o gravado, que a
  // escrita anterior deixou e que `upsertAccountEmail` nunca apaga.
  it('recusa a leitura da conta divergente pelo e-mail gravado', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await renameKey(harness, key.id, CORPORATE);

    const response = await readTeam(harness, key.key);

    expect(response.status).toBe(403);
    expect(response.body.error).toContain(PERSONAL);
  });

  it('aceita qualquer conta quando o rotulo nao declara e-mail', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor comercial', 10);

    expect((await ingest(harness, key.key, PERSONAL)).status).toBe(200);
    expect((await readTeam(harness, key.key)).status).toBe(200);
  });

  // Buraco assumido: cliente anterior ao campo nao reporta e-mail, e recusar
  // derrubaria maquina que a mudanca nao pretende atingir.
  it('aceita cliente que nao reporta e-mail nenhum', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    expect((await ingest(harness, key.key)).status).toBe(200);
  });

  // Chave legada em modo aberto e token de admin nao representam conta: nao ha
  // rotulo do qual derivar relacao nenhuma.
  it('nao alcanca a chave legada', async () => {
    const response = await ingest(harness, TEST_TEAM_KEY, PERSONAL);

    expect(response.status).toBe(200);
  });
});

// "Testar conexao" chama claim e cai em verify contra servidor antigo. Aprovar
// aqui e recusar no envio seguinte deixaria a sincronia parada em silencio, que
// e o defeito que essas duas rotas existem para evitar.
describe('testar conexao contra as duas travas', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('vincula a conta que esta no rotulo', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE);

    const response = await claim(harness, key.key, CORPORATE);

    expect(response.status).toBe(200);
    expect(response.body).toMatchObject({ authorized: true, claimed: true });
  });

  it('recusa o vinculo da conta fora do rotulo, em vez de aprovar e falhar depois', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    const response = await claim(harness, key.key, PERSONAL);

    expect(response.status).toBe(403);
    expect(response.body.error).toContain(PERSONAL);
    // E nao vinculou: aprovar o vinculo e recusar o ingest seria o pior dos dois.
    expect((await ingest(harness, key.key, PERSONAL)).status).toBe(403);
  });

  it('recusa a consulta da conta fora do rotulo', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    expect((await verify(harness, key.key, PERSONAL)).status).toBe(403);
  });

  // Sem o e-mail na requisicao o verify responderia pelo gravado, e numa conta
  // que nunca enviou nada nao ha gravado nenhum: e por isso que o cliente passou
  // a mandar o e-mail tambem aqui.
  it('aprova a conta desconhecida quando o cliente nao manda e-mail', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    expect((await verify(harness, key.key)).status).toBe(200);
  });

  it('recusa a conta ja vinculada que passou a divergir do rotulo', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await renameKey(harness, key.id, CORPORATE);

    expect((await verify(harness, key.key, PERSONAL)).status).toBe(403);
    expect((await claim(harness, key.key, PERSONAL)).status).toBe(403);
  });

  it('recusa a conta que o admin removeu do time', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect((await verify(harness, key.key, PERSONAL)).status).toBe(403);
    expect((await claim(harness, key.key, PERSONAL)).status).toBe(403);
  });
});

describe('portao do rotulo desligado', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness({ keyLabelMatch: 'off' });
  });

  afterEach(() => {
    harness.cleanup();
  });

  it('aceita a conta divergente', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);

    expect((await ingest(harness, key.key, PERSONAL)).status).toBe(200);
  });

  // A valvula de rollback e do portao, nao da decisao do admin: desfazer a
  // segunda por variavel de ambiente seria outra pessoa decidindo.
  it('nao devolve ao time a conta que o admin removeu', async () => {
    const key = await createKeyViaAdmin(harness, CORPORATE, 10);
    await ingest(harness, key.key, PERSONAL);
    await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect((await ingest(harness, key.key, PERSONAL)).status).toBe(403);
  });
});

describe('conta declarada fora do time', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  async function removeAccount(): Promise<void> {
    await request(harness.app)
      .delete(`/api/admin/v1/accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);
  }

  it('recusa o ingest da chave que a tinha', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await removeAccount();

    const response = await ingest(harness, key.key, PERSONAL);

    expect(response.status).toBe(403);
    expect(response.body.error).toContain('removida do time');
  });

  it('recusa a batida de presenca, que era o caminho mais rapido de volta', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await removeAccount();

    expect((await presence(harness, key.key, PERSONAL)).status).toBe(403);
  });

  it('recusa a leitura pela chave de time', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await removeAccount();

    expect((await readTeam(harness, key.key)).status).toBe(403);
  });

  // A chave legada le e escreve tudo, e era por ela que a conta voltaria num
  // deploy que ainda nao migrou para chaves por pessoa.
  it('recusa a escrita da chave legada em modo aberto', async () => {
    await ingest(harness, TEST_TEAM_KEY, PERSONAL);
    await removeAccount();

    expect((await ingest(harness, TEST_TEAM_KEY, PERSONAL)).status).toBe(403);
    expect((await presence(harness, TEST_TEAM_KEY, PERSONAL)).status).toBe(403);
  });

  it('volta a aceitar depois de desbloquear', async () => {
    const key = await createKeyViaAdmin(harness, 'Chave do setor', 10);
    await ingest(harness, key.key, PERSONAL);
    await removeAccount();

    await request(harness.app)
      .delete(`/api/admin/v1/blocked-accounts/${ACCOUNT_A}`)
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect((await ingest(harness, key.key, PERSONAL)).status).toBe(200);
  });
});
