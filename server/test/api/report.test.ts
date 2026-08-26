import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';
import {
  ACCOUNT_A,
  ACCOUNT_B,
  createHarness,
  createKeyViaAdmin,
  makePayload,
  makeSession,
  makeTurn,
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

// --- A05: rotas planas e paginadas ----------------------------------------

const T0 = Date.UTC(2026, 7, 10, 0, 0, 0);
const MINUTE = 60 * 1_000;

/** Duas contas, duas maquinas, tres sessoes: o suficiente para a ordem importar. */
async function seedReport(app: Harness['app']): Promise<void> {
  const plans = [
    { accountKey: ACCOUNT_A, deviceId: 'device-1', sessionId: 'session-a' },
    { accountKey: ACCOUNT_A, deviceId: 'device-2', sessionId: 'session-b' },
    { accountKey: ACCOUNT_B, deviceId: 'device-1', sessionId: 'session-c' },
  ];

  for (const plan of plans) {
    const response = await request(app)
      .post('/api/v1/ingest')
      .set('x-team-key', TEST_TEAM_KEY)
      .send(
        makePayload({
          accountKey: plan.accountKey,
          member: {
            deviceId: plan.deviceId,
            alias: plan.deviceId,
            hostName: null,
            organizationUuid: null,
            organizationName: null,
          },
          sessions: [makeSession({ sessionId: plan.sessionId, firstTs: T0, lastTs: T0 + 2 * MINUTE })],
          turns: [0, 1, 2].map((index) =>
            makeTurn({
              sessionId: plan.sessionId,
              messageId: `msg-${index}`,
              ts: T0 + index * MINUTE,
              model: index === 2 ? 'claude-sonnet-5' : 'claude-opus-5',
            }),
          ),
        }),
      );

    if (response.status !== 200) {
      throw new Error(`ingest falhou: ${response.status} ${response.text}`);
    }
  }
}

function reportGet(app: Harness['app'], path: string, query: Record<string, string | number> = {}) {
  return request(app).get(path).query(query).set('x-report-key', TEST_REPORT_TOKEN);
}

/** Percorre a rota inteira com o `limit` pedido e devolve todas as linhas. */
async function walk(
  app: Harness['app'],
  path: string,
  limit: number,
  query: Record<string, string | number> = {},
): Promise<unknown[]> {
  const collected: unknown[] = [];
  let cursor: string | null = null;
  // Teto de seguranca: um cursor que nao avanca viraria laco infinito no teste, e
  // o sintoma seria a suite travando em vez de falhando.
  for (let page = 0; page < 50; page += 1) {
    const response: import('supertest').Response = await reportGet(app, path, {
      ...query,
      limit,
      ...(cursor === null ? {} : { cursor }),
    });
    expect(response.status).toBe(200);
    collected.push(...response.body.rows);
    cursor = response.body.nextCursor;
    if (cursor === null) {
      return collected;
    }
  }
  throw new Error('a paginacao nao terminou em 50 paginas');
}

describe('GET /api/v1/report/usage', () => {
  it('paginado de um em um devolve exatamente o mesmo conjunto', async () => {
    const { app } = start();
    await seedReport(app);

    const whole = await reportGet(app, '/api/v1/report/usage');
    const walked = await walk(app, '/api/v1/report/usage', 1);

    expect(whole.body.nextCursor).toBeNull();
    expect(whole.body.rows.length).toBe(6);
    expect(walked).toEqual(whole.body.rows);
  });

  // A ultima pagina cheia nao pode devolver cursor: ele abriria uma pagina vazia.
  it('não devolve cursor quando a página fecha o conjunto', async () => {
    const { app } = start();
    await seedReport(app);

    const response = await reportGet(app, '/api/v1/report/usage', { limit: 6 });

    expect(response.body.rows.length).toBe(6);
    expect(response.body.nextCursor).toBeNull();
  });

  it('ordena por conta, máquina, sessão e modelo', async () => {
    const { app } = start();
    await seedReport(app);

    const response = await reportGet(app, '/api/v1/report/usage');
    const keys = response.body.rows.map(
      (row: { accountKey: string; deviceId: string; sessionId: string; model: string | null }) =>
        [row.accountKey, row.deviceId, row.sessionId, row.model ?? ''].join('|'),
    );

    expect(keys).toEqual([...keys].sort());
  });

  it('recorta pela janela semiaberta', async () => {
    const { app } = start();
    await seedReport(app);

    const response = await reportGet(app, '/api/v1/report/usage', {
      since: T0,
      until: T0 + 2 * MINUTE,
    });
    const turns = response.body.rows.reduce(
      (total: number, row: { turnCount: number }) => total + row.turnCount,
      0,
    );

    // Tres sessoes x dois turnos dentro da janela; o terceiro de cada uma cai
    // exatamente em `until` e fica de fora.
    expect(turns).toBe(6);
  });

  it('responde 400 para cursor ilegível', async () => {
    const { app } = start();

    const response = await reportGet(app, '/api/v1/report/usage', { cursor: 'nao-e-base64-de-json' });

    expect(response.status).toBe(400);
  });

  it('recusa a chave de time', async () => {
    const { app } = start();

    const response = await request(app).get('/api/v1/report/usage').set('x-team-key', TEST_TEAM_KEY);

    expect(response.status).toBe(401);
  });
});

describe('GET /api/v1/report/activity', () => {
  it('paginado de um em um devolve exatamente o mesmo conjunto', async () => {
    const { app } = start();
    await seedReport(app);

    const whole = await reportGet(app, '/api/v1/report/activity');
    const walked = await walk(app, '/api/v1/report/activity', 1);

    expect(whole.body.rows.length).toBe(3);
    expect(walked).toEqual(whole.body.rows);
  });

  // O tempo de uma sessao nao pode depender da pagina em que ela caiu: o cursor
  // filtra sessoes inteiras, nunca turnos dentro de uma.
  it('mede o mesmo tempo em qualquer tamanho de página', async () => {
    const { app } = start();
    await seedReport(app);

    const whole = await reportGet(app, '/api/v1/report/activity');
    const walked = (await walk(app, '/api/v1/report/activity', 1)) as Array<{ activeMillis: number }>;

    const sum = (rows: Array<{ activeMillis: number }>): number =>
      rows.reduce((total, row) => total + row.activeMillis, 0);
    expect(sum(walked)).toBe(sum(whole.body.rows));
    expect(sum(whole.body.rows)).toBe(3 * 2 * MINUTE);
  });

  it('responde 400 para cursor ilegível', async () => {
    const { app } = start();

    expect((await reportGet(app, '/api/v1/report/activity', { cursor: '%%%' })).status).toBe(400);
  });
});

describe('GET /api/v1/report/members', () => {
  it('lista as contas com integrantes, rótulo e origem do e-mail', async () => {
    const { app } = start();
    await seedReport(app);

    const response = await reportGet(app, '/api/v1/report/members');

    expect(response.status).toBe(200);
    const account = response.body.accounts.find(
      (candidate: { accountKey: string }) => candidate.accountKey === ACCOUNT_A,
    );
    expect(account.members.length).toBe(2);
    // Sem chave emitida e sem e-mail reportado os tres campos sao nulos, e
    // `emailSource` nulo diz exatamente isso em vez de sugerir identidade.
    expect(account.label).toBeNull();
    expect(account.accountEmail).toBeNull();
    expect(account.emailSource).toBeNull();
  });

  it('marca como label o e-mail que veio do rótulo administrativo', async () => {
    const local = start();
    await seedReport(local.app);
    const key = await createKeyViaAdmin(local, 'pessoa@empresa.com');
    await request(local.app)
      .post('/api/v1/claim')
      .set('x-team-key', key.key)
      .send({ accountKey: ACCOUNT_A });

    const response = await reportGet(local.app, '/api/v1/report/members');
    const account = response.body.accounts.find(
      (candidate: { accountKey: string }) => candidate.accountKey === ACCOUNT_A,
    );

    expect(account.label).toBe('pessoa@empresa.com');
    expect(account.accountEmail).toBe('pessoa@empresa.com');
    expect(account.emailSource).toBe('label');
  });
});
