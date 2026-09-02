import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';
import {
  ACCOUNT_A,
  createHarness,
  makePayload,
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

/** O relógio do harness. Os turnos precisam cair dentro da janela de 24h. */
const NOW = Date.UTC(2026, 7, 11, 12, 0, 0);
const RECENT = Date.UTC(2026, 7, 11, 11, 0, 0);

async function ingest(harness: Harness, payload: ReturnType<typeof makePayload>): Promise<void> {
  const response = await request(harness.app)
    .post('/api/v1/ingest')
    .set('x-team-key', TEST_TEAM_KEY)
    .send(payload);

  if (response.status !== 200 && response.status !== 201) {
    throw new Error(`ingest falhou: ${response.status} ${response.text}`);
  }
}

/** Uma amostra da exposição, pelo nome e por um trecho dos rótulos. */
function sample(body: string, metric: string, labelFragment = ''): string | null {
  const line = body
    .split('\n')
    .find((row) => row.startsWith(`${metric}{`) || row.startsWith(`${metric} `))
    ?.trim();
  if (line === undefined) {
    return null;
  }
  if (labelFragment !== '' && !line.includes(labelFragment)) {
    return (
      body
        .split('\n')
        .find((row) => row.startsWith(metric) && row.includes(labelFragment))
        ?.trim() ?? null
    );
  }
  return line;
}

describe('GET /metrics — credencial', () => {
  it('recusa sem credencial', async () => {
    const { app } = start();

    const response = await request(app).get('/metrics');

    expect(response.status).toBe(401);
  });

  it('aceita o token de relatório no header próprio', async () => {
    const { app } = start();

    const response = await request(app).get('/metrics').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(200);
  });

  it('aceita o token de admin', async () => {
    const { app } = start();

    const response = await request(app).get('/metrics').set('x-admin-token', TEST_ADMIN_TOKEN);

    expect(response.status).toBe(200);
  });

  /**
   * O `scrape_config` do Prometheus sabe mandar `authorization` nativamente;
   * header de nome próprio não é garantido em todo agente de coleta.
   */
  it('aceita Authorization: Bearer com qualquer um dos dois tokens', async () => {
    const { app } = start();

    const report = await request(app)
      .get('/metrics')
      .set('authorization', `Bearer ${TEST_REPORT_TOKEN}`);
    const admin = await request(app)
      .get('/metrics')
      .set('authorization', `bearer ${TEST_ADMIN_TOKEN}`);

    expect(report.status).toBe(200);
    expect(admin.status).toBe(200);
  });

  it('recusa bearer com segredo errado', async () => {
    const { app } = start();

    const response = await request(app)
      .get('/metrics')
      .set('authorization', 'Bearer nao-e-o-token-0123456789abcdefgh');

    expect(response.status).toBe(401);
  });

  /** A chave de time é **por conta**, e esta leitura é global. */
  it('recusa a chave de time', async () => {
    const { app } = start();

    const response = await request(app).get('/metrics').set('x-team-key', TEST_TEAM_KEY);

    expect(response.status).toBe(401);
  });

  /**
   * Sem `TEAM_REPORT_TOKEN` a rota **existe** e responde 401: rota ausente faria
   * "credencial errada" e "variável não definida" chegarem como o mesmo 404.
   */
  it('sem token de relatório configurado a rota continua existindo', async () => {
    const { app } = start({ reportToken: null });

    const response = await request(app).get('/metrics').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.status).toBe(401);
    expect(response.body.code).not.toBe('not_found');
  });
});

describe('GET /metrics — formato', () => {
  it('negocia OpenMetrics quando o Accept pede', async () => {
    const { app } = start();

    const response = await request(app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN)
      .set('accept', 'application/openmetrics-text;version=1.0.0,text/plain;version=0.0.4;q=0.5');

    expect(response.headers['content-type']).toContain('application/openmetrics-text');
    expect(response.text.endsWith('# EOF\n')).toBe(true);
  });

  it('cai no formato Prometheus sem o Accept de OpenMetrics', async () => {
    const { app } = start();

    const response = await request(app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN)
      .set('accept', 'text/plain;version=0.0.4');

    expect(response.headers['content-type']).toContain('text/plain');
    expect(response.text).not.toContain('# EOF');
  });

  /** Resposta sem série nenhuma é indistinguível de endpoint quebrado. */
  it('com banco vazio ainda publica build_info', async () => {
    const { app } = start();

    const response = await request(app).get('/metrics').set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.text).toContain('# TYPE usage_monitor_build_info gauge');
    expect(response.text).toMatch(/usage_monitor_build_info\{[^}]*version="\d+\.\d+\.\d+"/);
    expect(response.text).toContain('pricing_version=');
  });
});

describe('GET /metrics — números', () => {
  it('publica tokens, turnos e sessões da janela', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({
        turns: [makeTurn({ ts: RECENT, model: 'claude-sonnet-5', inputTokens: 1_000 })],
      }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_tokens_window', 'kind="input"')).toContain(' 1000');
    expect(sample(response.text, 'usage_monitor_turns_window', 'window="24h"')).toContain(' 1');
    expect(sample(response.text, 'usage_monitor_sessions_window', 'window="24h"')).toContain(' 1');
    expect(response.text).toContain(`account="${ACCOUNT_A}"`);
    expect(response.text).toContain('member="device-1"');
  });

  /**
   * O custo é a conta do `ModelPricing.kt` aplicada no servidor. Sonnet 5 a
   * 2 USD/M de input: 1M de input = 2,000000 USD, exato.
   */
  it('publica o custo em dólares, pela tabela publicada', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({
        turns: [
          makeTurn({
            ts: RECENT,
            model: 'claude-sonnet-5',
            inputTokens: 1_000_000,
            outputTokens: 0,
            cacheReadTokens: 0,
            cacheWrite5mTokens: 0,
            cacheWrite1hTokens: 0,
          }),
        ],
      }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_cost_usd_window', 'window="24h"')).toContain(
      ' 2.000000',
    );
  });

  /**
   * Modelo sem tarifa **não vira custo zero**: um painel mostraria queda de gasto
   * onde houve modelo novo. A série de turnos sem preço é o aviso.
   */
  it('modelo desconhecido não vira custo zero', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({ turns: [makeTurn({ ts: RECENT, model: 'gpt-5' })] }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_unpriced_turns_window', 'window="24h"')).toContain(
      ' 1',
    );
    expect(sample(response.text, 'usage_monitor_cost_usd_window', 'model="gpt-5"')).toBeNull();
  });

  /** Turno fora da janela de 24h não pode aparecer nela. */
  it('a janela de 24h recorta os turnos', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({
        turns: [
          makeTurn({
            sessionId: 'session-1',
            messageId: 'msg-antigo',
            ts: NOW - 3 * 24 * 60 * 60 * 1_000,
            model: 'claude-sonnet-5',
          }),
        ],
      }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    const lines = response.text.split('\n').filter((row) => row.startsWith('usage_monitor_turns_window'));
    expect(lines.some((row) => row.includes('window="7d"'))).toBe(true);
    expect(lines.some((row) => row.includes('window="24h"'))).toBe(false);
  });

  /**
   * Identidade fora das séries de valor: `alias` é texto digitado e mutável, e
   * como rótulo de valor renomear a máquina quebraria o gráfico no meio.
   */
  it('o apelido vive em member_info, nunca nas séries de valor', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({ turns: [makeTurn({ ts: RECENT, model: 'claude-sonnet-5' })] }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_member_info')).toContain('alias="edilson"');
    expect(sample(response.text, 'usage_monitor_tokens_window')).not.toContain('alias=');
    expect(sample(response.text, 'usage_monitor_member_last_seen_timestamp_seconds')).not.toBeNull();
  });

  /**
   * `session_id` é ilimitado: uma máquina produz sessões novas todo dia, e como
   * rótulo a cardinalidade cresceria para sempre.
   */
  it('não expõe sessão, cwd nem branch como rótulo', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({ turns: [makeTurn({ ts: RECENT, model: 'claude-sonnet-5' })] }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(response.text).not.toContain('session=');
    expect(response.text).not.toContain('cwd=');
    expect(response.text).not.toContain('branch=');
    expect(response.text).not.toContain('/home/dev/api-gateway');
  });

  /** Uma aspa num apelido invalidaria o documento **inteiro**, não a linha. */
  it('escapa aspas e barras do apelido', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({
        member: {
          deviceId: 'device-1',
          alias: 'ana "a\\dev"',
          hostName: 'DESKTOP-A1',
          organizationUuid: null,
          organizationName: null,
        },
        turns: [makeTurn({ ts: RECENT, model: 'claude-sonnet-5' })],
      }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_member_info')).toContain(
      'alias="ana \\"a\\\\dev\\""',
    );
  });

  /**
   * Acima do teto o rótulo `model` é agregado fora e a série de sinal vai a 1 —
   * degradar com sinal, e não truncar em silêncio nem responder 500.
   */
  it('degrada o rótulo de modelo acima do teto de séries', async () => {
    const started = start({ metricsMaxSeries: 1 });
    await ingest(
      started,
      makePayload({ turns: [makeTurn({ ts: RECENT, model: 'claude-sonnet-5' })] }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_metrics_model_label_dropped')).toContain(' 1');
    expect(response.text).not.toContain('model="claude-sonnet-5"');
    // Os totais continuam corretos: o que saiu foi a dimensão, não o dado.
    expect(sample(response.text, 'usage_monitor_turns_window', 'window="24h"')).toContain(' 1');
  });

  it('com folga de séries o modelo continua como rótulo', async () => {
    const started = start();
    await ingest(
      started,
      makePayload({ turns: [makeTurn({ ts: RECENT, model: 'claude-sonnet-5' })] }),
    );

    const response = await request(started.app)
      .get('/metrics')
      .set('x-report-key', TEST_REPORT_TOKEN);

    expect(sample(response.text, 'usage_monitor_metrics_model_label_dropped')).toContain(' 0');
    expect(response.text).toContain('model="claude-sonnet-5"');
  });
});
