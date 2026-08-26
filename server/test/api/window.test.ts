import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';
import {
  ACCOUNT_A,
  createHarness,
  makePayload,
  makeSession,
  makeTurn,
  TEST_ADMIN_TOKEN,
  TEST_TEAM_KEY,
  type Harness,
} from '../support/harness.js';

let harness: Harness | null = null;

const T0 = Date.UTC(2026, 7, 10, 0, 0, 0);
const MINUTE = 60 * 1_000;

function start(): Harness {
  harness = createHarness();
  return harness;
}

afterEach(() => {
  harness?.cleanup();
  harness = null;
});

/** Turnos da mesma sessao, um por instante pedido. */
async function ingestAt(app: Harness['app'], instants: number[]): Promise<void> {
  const response = await request(app)
    .post('/api/v1/ingest')
    .set('x-team-key', TEST_TEAM_KEY)
    .send(
      makePayload({
        sessions: [makeSession({ firstTs: instants[0], lastTs: instants[instants.length - 1] })],
        turns: instants.map((ts, index) =>
          makeTurn({ messageId: `msg-${index}`, ts, inputTokens: 10, outputTokens: 0, cacheReadTokens: 0, cacheWrite5mTokens: 0 }),
        ),
      }),
    );

  if (response.status !== 200) {
    throw new Error(`ingest falhou: ${response.status} ${response.text}`);
  }
}

function readTeam(
  app: Harness['app'],
  query: Record<string, number | string>,
): request.Test {
  return request(app)
    .get('/api/v1/team')
    .query({ accountKey: ACCOUNT_A, ...query })
    .set('x-admin-token', TEST_ADMIN_TOKEN);
}

function turnCountOf(body: { rows: Array<{ turnCount: number }> }): number {
  return body.rows.reduce((total, row) => total + row.turnCount, 0);
}

describe('recorte semiaberto de /v1/team', () => {
  it('inclui o turno em since e exclui o turno em until', async () => {
    const { app } = start();
    await ingestAt(app, [T0, T0 + MINUTE, T0 + 2 * MINUTE]);

    const response = await readTeam(app, { since: T0, until: T0 + 2 * MINUTE });

    expect(response.status).toBe(200);
    // O de T0 entra (>= since), o de T0+2min fica de fora (< until).
    expect(turnCountOf(response.body)).toBe(2);
  });

  // A propriedade que justifica o semiaberto: sem ela, um relatorio mensal
  // montado de janelas adjacentes somaria mais turnos do que existem.
  it('duas janelas adjacentes somam exatamente a janela inteira', async () => {
    const { app } = start();
    const instants = [T0, T0 + MINUTE, T0 + 2 * MINUTE, T0 + 3 * MINUTE];
    await ingestAt(app, instants);

    const border = T0 + 2 * MINUTE;
    const whole = await readTeam(app, { since: T0, until: T0 + 4 * MINUTE });
    const left = await readTeam(app, { since: T0, until: border });
    const right = await readTeam(app, { since: border, until: T0 + 4 * MINUTE });

    expect(turnCountOf(whole.body)).toBe(instants.length);
    expect(turnCountOf(left.body) + turnCountOf(right.body)).toBe(turnCountOf(whole.body));
  });

  it('aceita until sem since', async () => {
    const { app } = start();
    await ingestAt(app, [T0, T0 + MINUTE, T0 + 2 * MINUTE]);

    const response = await readTeam(app, { until: T0 + MINUTE });

    expect(response.status).toBe(200);
    expect(turnCountOf(response.body)).toBe(1);
  });

  it('recusa until menor ou igual a since', async () => {
    const { app } = start();

    expect((await readTeam(app, { since: T0, until: T0 })).status).toBe(400);
    expect((await readTeam(app, { since: T0, until: T0 - MINUTE })).status).toBe(400);
  });

  // O intervalo que cruza a fronteira nao e contado em nenhuma das duas janelas:
  // conta-lo nas duas duplicaria o tempo, e atribui-lo a uma exigiria ler um
  // turno que esta fora dela.
  it('não conta o intervalo que cruza a fronteira em nenhuma das janelas', async () => {
    const { app } = start();
    await ingestAt(app, [T0, T0 + MINUTE, T0 + 2 * MINUTE, T0 + 3 * MINUTE]);

    const border = T0 + 2 * MINUTE;
    const whole = await readTeam(app, { since: T0, until: T0 + 4 * MINUTE });
    const left = await readTeam(app, { since: T0, until: border });
    const right = await readTeam(app, { since: border, until: T0 + 4 * MINUTE });

    const active = (body: { activity: Array<{ activeMillis: number }> }): number =>
      body.activity.reduce((total, row) => total + row.activeMillis, 0);

    expect(active(whole.body)).toBe(3 * MINUTE);
    expect(active(left.body)).toBe(MINUTE);
    expect(active(right.body)).toBe(MINUTE);
  });
});

describe('recorte semiaberto nas outras leituras', () => {
  it('recorta a visão global do admin', async () => {
    const { app } = start();
    await ingestAt(app, [T0, T0 + MINUTE, T0 + 2 * MINUTE]);

    const response = await request(app)
      .get('/api/admin/v1/overview')
      .query({ since: T0, until: T0 + 2 * MINUTE })
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    const rows = response.body.accounts.flatMap(
      (account: { rows: Array<{ turnCount: number }> }) => account.rows,
    );
    expect(rows.reduce((total: number, row: { turnCount: number }) => total + row.turnCount, 0)).toBe(2);
  });

  it('recorta a série diária por since e until absolutos', async () => {
    const { app } = start();
    const day = 24 * 60 * 60 * 1_000;
    await ingestAt(app, [T0, T0 + day, T0 + 2 * day]);

    const response = await request(app)
      .get('/api/v1/team/trend')
      .query({ accountKey: ACCOUNT_A, since: T0, until: T0 + 2 * day })
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body.rows.length).toBe(2);
  });

  it('recusa until menor que since na série diária', async () => {
    const { app } = start();

    const response = await request(app)
      .get('/api/v1/team/trend')
      .query({ accountKey: ACCOUNT_A, since: T0, until: T0 - MINUTE })
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(400);
  });
});

// O desktop nao muda nesta atividade: `until` e opcional e as rotas continuam
// respondendo o mesmo para os parametros que `RemoteTeamDataSource` ja manda.
describe('compatibilidade com o cliente atual', () => {
  it('responde /v1/team com accountKey, since e gapCutoffMs', async () => {
    const { app } = start();
    await ingestAt(app, [T0, T0 + MINUTE]);

    const response = await readTeam(app, { since: T0, gapCutoffMs: 5 * MINUTE });

    expect(response.status).toBe(200);
    expect(turnCountOf(response.body)).toBe(2);
    expect(response.body.activity[0].activeMillis).toBe(MINUTE);
  });

  it('responde /v1/team/trend com accountKey e days', async () => {
    const { app } = start();
    harness?.setNow(T0 + 60 * MINUTE);
    await ingestAt(app, [T0, T0 + MINUTE]);

    const response = await request(app)
      .get('/api/v1/team/trend')
      .query({ accountKey: ACCOUNT_A, days: 30 })
      .set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
    expect(response.body.rows.length).toBeGreaterThan(0);
  });
});
