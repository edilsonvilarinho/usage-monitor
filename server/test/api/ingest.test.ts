import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import request from 'supertest';
import {
  ACCOUNT_A,
  TEST_TEAM_KEY,
  createHarness,
  makePayload,
  makeSession,
  makeTurn,
  type Harness,
} from '../support/harness.js';

describe('POST /api/v1/ingest', () => {
  let harness: Harness;

  beforeEach(() => {
    harness = createHarness();
  });

  afterEach(() => {
    harness.cleanup();
  });

  function post(body: unknown) {
    return request(harness.app).post('/api/v1/ingest').set('x-team-key', TEST_TEAM_KEY).send(body);
  }

  it('grava membro, sessao e turnos', async () => {
    const response = await post(makePayload());

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ acceptedTurns: 1, ignoredTurns: 0, acceptedSessions: 1 });

    const turnCount = harness.db.prepare('SELECT COUNT(*) AS total FROM team_turns').get() as {
      total: number;
    };
    expect(turnCount.total).toBe(1);
  });

  it('aceita cliente antigo sem accountEmail', async () => {
    const response = await post(makePayload());

    expect(response.status).toBe(200);
    const metadata = harness.db.prepare('SELECT * FROM team_accounts').all();
    expect(metadata).toEqual([]);
  });

  it('normaliza e persiste o e-mail reportado', async () => {
    const response = await post(makePayload({ accountEmail: '  Helio.Sales@Empresa.COM  ' }));

    expect(response.status).toBe(200);
    const metadata = harness.db
      .prepare('SELECT account_email AS accountEmail FROM team_accounts WHERE account_key = ?')
      .get(ACCOUNT_A) as { accountEmail: string };
    expect(metadata.accountEmail).toBe('helio.sales@empresa.com');
  });

  it('recusa accountEmail invalido', async () => {
    const response = await post({ ...makePayload(), accountEmail: 'nao-e-email' });

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('e idempotente: reenviar o mesmo lote nao duplica turnos', async () => {
    await post(makePayload());
    const second = await post(makePayload());

    expect(second.body).toEqual({ acceptedTurns: 0, ignoredTurns: 1, acceptedSessions: 1 });

    const turnCount = harness.db.prepare('SELECT COUNT(*) AS total FROM team_turns').get() as {
      total: number;
    };
    expect(turnCount.total).toBe(1);
  });

  it('alarga a janela da sessao em pushes parciais em vez de encurta-la', async () => {
    await post(
      makePayload({
        sessions: [makeSession({ firstTs: 3_000, lastTs: 5_000 })],
        turns: [makeTurn({ messageId: 'msg-a', ts: 4_000 })],
      }),
    );
    await post(
      makePayload({
        sessions: [
          makeSession({
            firstTs: 1_000,
            lastTs: 2_000,
            liveContextTokens: 999,
            liveContextModel: 'claude-haiku-4-5-20251001',
          }),
        ],
        turns: [makeTurn({ messageId: 'msg-b', ts: 1_500 })],
      }),
    );

    const session = harness.db
      .prepare(
        'SELECT first_ts AS firstTs, last_ts AS lastTs, live_context_tokens AS liveContextTokens FROM team_sessions',
      )
      .get() as { firstTs: number; lastTs: number; liveContextTokens: number };

    expect(session.firstTs).toBe(1_000);
    expect(session.lastTs).toBe(5_000);
    // O push mais antigo nao pode sobrescrever o contexto vivo do mais recente.
    expect(session.liveContextTokens).toBe(120_000);
  });

  it('rejeita turno cuja sessao nao veio no lote', async () => {
    const response = await post(
      makePayload({
        sessions: [makeSession({ sessionId: 'session-1' })],
        turns: [makeTurn({ sessionId: 'session-orfa' })],
      }),
    );

    expect(response.status).toBe(400);
    expect(response.body.error).toContain('session-orfa');
  });

  it('rejeita payload sem accountKey', async () => {
    const payload = makePayload() as Record<string, unknown>;
    delete payload.accountKey;

    const response = await post(payload);

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('rejeita lote acima do limite configurado', async () => {
    const small = createHarness({ maxTurnsPerRequest: 2 });
    try {
      const response = await request(small.app)
        .post('/api/v1/ingest')
        .set('x-team-key', TEST_TEAM_KEY)
        .send(
          makePayload({
            turns: [
              makeTurn({ messageId: 'a' }),
              makeTurn({ messageId: 'b' }),
              makeTurn({ messageId: 'c' }),
            ],
          }),
        );

      expect(response.status).toBe(400);
    } finally {
      small.cleanup();
    }
  });

  it('aceita lote so com o membro e atualiza o apelido', async () => {
    await post(makePayload());

    // Contrato de que o cliente depende para propagar um apelido novo sem
    // atividade nova: o alias so e gravado dentro de um ingest, e sem aceitar
    // `sessions`/`turns` vazios a tela do time ficaria com o nome velho ate a
    // maquina voltar a gerar turnos.
    const response = await post({
      accountKey: ACCOUNT_A,
      member: {
        deviceId: 'device-1',
        alias: 'SUETONIO',
        hostName: 'DESKTOP-A1',
        organizationUuid: null,
        organizationName: null,
      },
    });

    expect(response.status).toBe(200);
    expect(response.body).toEqual({ acceptedTurns: 0, ignoredTurns: 0, acceptedSessions: 0 });

    const members = harness.db
      .prepare('SELECT device_id AS deviceId, alias FROM team_members')
      .all() as Array<{ deviceId: string; alias: string }>;

    // Renomear nunca cria linha: a chave e (account_key, device_id).
    expect(members).toEqual([{ deviceId: 'device-1', alias: 'SUETONIO' }]);

    const turns = harness.db.prepare('SELECT COUNT(*) AS total FROM team_turns').get() as {
      total: number;
    };
    expect(turns.total).toBe(1);
  });

  it('recusa apelido em branco', async () => {
    const response = await post(
      makePayload({
        member: {
          deviceId: 'device-1',
          alias: '   ',
          hostName: null,
          organizationUuid: null,
          organizationName: null,
        },
      }),
    );

    expect(response.status).toBe(400);
    expect(response.body.code).toBe('validation_error');
  });

  it('carimba last_seen_at com o relogio injetado', async () => {
    harness.setNow(1_700_000_000_000);
    await post(makePayload());

    const member = harness.db
      .prepare('SELECT last_seen_at AS lastSeenAt FROM team_members')
      .get() as { lastSeenAt: number };

    expect(member.lastSeenAt).toBe(1_700_000_000_000);
  });
});
