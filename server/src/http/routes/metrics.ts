import { Router } from 'express';
import type { Config } from '../../config.js';
import {
  OPENMETRICS_CONTENT_TYPE,
  PROMETHEUS_CONTENT_TYPE,
  prefersOpenMetrics,
  renderExposition,
  type MetricFamily,
  type MetricSample,
} from '../../domain/openMetrics.js';
import { PRICING_VERSION } from '../../domain/modelPricing.js';
import { costMicros, microsToUsdString, resolvePricing } from '../../domain/usageCost.js';
import { normalizeAccountEmail } from '../../domain/accountEmail.js';
import type { TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type {
  MetricsActivityRow,
  MetricsUsageRow,
  TeamRepository,
} from '../../repositories/teamRepository.js';
import { requireGlobalRead } from '../access.js';
import { DEFAULT_GAP_CUTOFF_MS } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface MetricsRouterDeps {
  config: Config;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
  now: () => number;
  /** Versao publicada em `usage_monitor_build_info`. */
  version: string;
}

/**
 * As janelas expostas.
 *
 * **Gauges de janela fixa, e nao counters cumulativos.** O counter seria o idioma
 * do Prometheus, mas a poda de retencao (`TEAM_RETENTION_DAYS`, 45 por padrao)
 * faz o total **cair**, e uma queda parcial e lida como reset de contador: a taxa
 * sairia inflacionada num dia qualquer, sem nada quebrar. A janela deslizante nao
 * tem esse problema, ao custo de `rate()` nao se aplicar — o que esta escrito no
 * README.
 *
 * Duas janelas, e nao quatro: cada uma multiplica a cardinalidade inteira.
 */
const WINDOWS: ReadonlyArray<{ label: string; durationMs: number }> = [
  { label: '24h', durationMs: 24 * 60 * 60 * 1_000 },
  { label: '7d', durationMs: 7 * 24 * 60 * 60 * 1_000 },
];

const TOKEN_KINDS = [
  { kind: 'input', of: (row: MetricsUsageRow) => row.inputTokens },
  { kind: 'output', of: (row: MetricsUsageRow) => row.outputTokens },
  { kind: 'cache_read', of: (row: MetricsUsageRow) => row.cacheReadTokens },
  { kind: 'cache_write_5m', of: (row: MetricsUsageRow) => row.cacheWrite5mTokens },
  { kind: 'cache_write_1h', of: (row: MetricsUsageRow) => row.cacheWrite1hTokens },
] as const;

/**
 * Rota de scrape.
 *
 * Montada na **raiz**, e nao sob `/api`: `/metrics` e o caminho convencional e e
 * o padrao de todo agente de coleta. Incondicional, como as rotas de relatorio —
 * sem `TEAM_REPORT_TOKEN` ela responde 401 em vez de nao existir, senao
 * "credencial errada" e "variavel nao definida" chegariam ao operador como o
 * mesmo 404.
 */
export function createMetricsRouter(deps: MetricsRouterDeps): Router {
  const router = Router();

  router.get(
    '/metrics',
    requireGlobalRead(
      deps.config,
      wrap((req, res) => {
        const until = deps.now();
        const families = buildFamilies(deps, until);
        const openMetrics = prefersOpenMetrics(req.header('accept'));

        res.setHeader('Content-Type', openMetrics ? OPENMETRICS_CONTENT_TYPE : PROMETHEUS_CONTENT_TYPE);
        res.send(renderExposition(families, { openMetrics }));
      }),
    ),
  );

  return router;
}

function buildFamilies(deps: MetricsRouterDeps, until: number): MetricFamily[] {
  const tokens: MetricSample[] = [];
  const turns: MetricSample[] = [];
  const sessions: MetricSample[] = [];
  const cost: MetricSample[] = [];
  const unpriced: MetricSample[] = [];
  const active: MetricSample[] = [];

  // Estimativa de series antes de montar: e ela que decide se o rotulo `model`
  // sobrevive. Calculada sobre a janela mais larga, que domina o total.
  const snapshots = WINDOWS.map((window) => ({
    window,
    snapshot: deps.repository.readMetricsWindow({
      since: until - window.durationMs,
      until,
      gapCutoffMs: DEFAULT_GAP_CUTOFF_MS,
    }),
  }));

  const keepModelLabel = shouldKeepModelLabel(
    snapshots.reduce((total, entry) => total + entry.snapshot.usage.length, 0),
    deps.config.metricsMaxSeries,
  );

  for (const { window, snapshot } of snapshots) {
    const usage = keepModelLabel ? snapshot.usage : mergeModels(snapshot.usage);

    for (const row of usage) {
      // `window` por ultimo em toda serie: a exposicao e lida por gente quando
      // algo esta errado, e ordem de rotulo inconsistente atrapalha a leitura.
      const identity: Record<string, string> = { account: row.accountKey, member: row.deviceId };
      const withModel = keepModelLabel ? { ...identity, model: row.model ?? 'unknown' } : identity;

      for (const tokenKind of TOKEN_KINDS) {
        tokens.push({
          labels: { ...withModel, kind: tokenKind.kind, window: window.label },
          value: tokenKind.of(row),
        });
      }
      turns.push({ labels: { ...withModel, window: window.label }, value: row.turnCount });

      const pricing = resolvePricing(row.model);
      if (pricing === null) {
        // Modelo sem tarifa **nao vira custo zero**: zero afirmaria que os turnos
        // nao custaram nada, e um painel mostraria queda de gasto onde houve
        // modelo novo. A serie de turnos sem preco e o aviso.
        unpriced.push({ labels: { ...withModel, window: window.label }, value: row.turnCount });
      } else {
        cost.push({
          labels: { ...withModel, window: window.label },
          value: microsToUsdString(costMicros(pricing, row)),
        });
      }
    }

    // Sessoes e tempo de trabalho nao levam `model`: uma sessao que trocou de
    // modelo no meio seria contada uma vez por modelo.
    for (const row of aggregateByMember(usage)) {
      sessions.push({
        labels: { account: row.accountKey, member: row.deviceId, window: window.label },
        value: row.sessionCount,
      });
    }

    for (const row of snapshot.activity) {
      active.push({
        labels: { account: row.accountKey, member: row.deviceId, window: window.label },
        value: millisToSeconds(row),
      });
    }
  }

  return [
    { name: 'usage_monitor_tokens_window', type: 'gauge', help: 'Tokens consumidos na janela, por tipo.', samples: tokens },
    { name: 'usage_monitor_turns_window', type: 'gauge', help: 'Turnos registrados na janela.', samples: turns },
    { name: 'usage_monitor_sessions_window', type: 'gauge', help: 'Sessoes distintas com atividade na janela.', samples: sessions },
    {
      name: 'usage_monitor_cost_usd_window',
      type: 'gauge',
      help: 'Custo estimado em USD na janela, pela tabela de precos publicada em /api/v1/pricing.',
      samples: cost,
    },
    {
      name: 'usage_monitor_unpriced_turns_window',
      type: 'gauge',
      help: 'Turnos cujo modelo nao tem tarifa conhecida: o custo da janela e piso, nao total.',
      samples: unpriced,
    },
    {
      name: 'usage_monitor_active_seconds_window',
      type: 'gauge',
      help: 'Tempo de trabalho na janela, somando intervalos entre turnos abaixo do corte.',
      samples: active,
    },
    ...identityFamilies(deps),
    {
      name: 'usage_monitor_activity_gap_cutoff_seconds',
      type: 'gauge',
      help: 'Intervalo maximo entre turnos contado como trabalho continuo.',
      samples: [{ labels: {}, value: DEFAULT_GAP_CUTOFF_MS / 1_000 }],
    },
    {
      name: 'usage_monitor_metrics_model_label_dropped',
      type: 'gauge',
      help: 'Vale 1 quando o rotulo de modelo foi agregado fora por exceder TEAM_METRICS_MAX_SERIES.',
      samples: [{ labels: {}, value: keepModelLabel ? 0 : 1 }],
    },
    {
      // Sai sempre, mesmo com banco vazio: uma resposta sem serie nenhuma e
      // indistinguivel de um endpoint quebrado.
      name: 'usage_monitor_build_info',
      type: 'gauge',
      help: 'Versao do servidor e da tabela de precos aplicada ao custo.',
      samples: [
        { labels: { version: deps.version, pricing_version: PRICING_VERSION }, value: 1 },
      ],
    },
  ];
}

/**
 * Identidade fora das series de valor.
 *
 * `alias` e texto digitado e mutavel: como rotulo de uma serie de valor, renomear
 * a maquina criaria uma serie nova e o grafico do integrante quebraria no meio.
 * O idioma do Prometheus para isso e o `_info` de valor 1, com a juncao feita no
 * PromQL — as series de valor carregam so `account` e `member`, que sao estaveis.
 */
function identityFamilies(deps: MetricsRouterDeps): MetricFamily[] {
  const accounts = deps.repository.readReportMembers(deps.keyRepository.accountLabels());

  const accountInfo: MetricSample[] = [];
  const memberInfo: MetricSample[] = [];
  const lastSeen: MetricSample[] = [];

  for (const account of accounts) {
    accountInfo.push({
      labels: {
        account: account.accountKey,
        label: account.label ?? '',
        email: normalizeAccountEmail(account.accountEmail) ?? '',
        email_source: account.emailSource ?? '',
      },
      value: 1,
    });

    for (const member of account.members) {
      memberInfo.push({
        labels: {
          account: account.accountKey,
          member: member.deviceId,
          alias: member.alias,
          host: member.hostName ?? '',
        },
        value: 1,
      });
      lastSeen.push({
        labels: { account: account.accountKey, member: member.deviceId },
        // Segundos, e nao millis: o Prometheus expressa tempo em segundos, e o
        // sufixo `_seconds` do nome promete isso.
        value: member.lastSeenAt / 1_000,
      });
    }
  }

  return [
    {
      name: 'usage_monitor_account_info',
      type: 'gauge',
      help: 'Identidade da conta: rotulo administrativo e e-mail efetivo.',
      samples: accountInfo,
    },
    {
      name: 'usage_monitor_member_info',
      type: 'gauge',
      help: 'Identidade da maquina: apelido digitado e nome do host.',
      samples: memberInfo,
    },
    {
      name: 'usage_monitor_member_last_seen_timestamp_seconds',
      type: 'gauge',
      help: 'Ultimo heartbeat da maquina, pelo relogio do servidor.',
      samples: lastSeen,
    },
  ];
}

/**
 * Estimativa de series contra o teto configurado.
 *
 * Cada linha de uso vira cinco series de token mais turnos e custo; as janelas
 * multiplicam tudo. Passar do teto **degrada com sinal** — o rotulo `model` sai e
 * `usage_monitor_metrics_model_label_dropped` vai a 1 — em vez de truncar em
 * silencio ou de responder 500, que quebraria o scrape inteiro por causa de uma
 * dimensao.
 */
export function shouldKeepModelLabel(usageRowCount: number, maxSeries: number): boolean {
  const seriesPerRow = TOKEN_KINDS.length + 2;
  return usageRowCount * seriesPerRow <= maxSeries;
}

/** Colapsa os modelos numa linha por `(conta, maquina)`, preservando as somas. */
export function mergeModels(rows: ReadonlyArray<MetricsUsageRow>): MetricsUsageRow[] {
  const merged = new Map<string, MetricsUsageRow>();

  for (const row of rows) {
    const key = `${row.accountKey} ${row.deviceId}`;
    const existing = merged.get(key);
    if (existing === undefined) {
      merged.set(key, { ...row, model: null });
      continue;
    }
    existing.turnCount += row.turnCount;
    // A contagem de sessoes vira **piso**: a mesma sessao pode aparecer em duas
    // linhas de modelo, e somar as contagens a contaria duas vezes. O maximo e o
    // que se pode afirmar sem a coluna de sessao, que a consulta nao traz de
    // proposito.
    existing.sessionCount = Math.max(existing.sessionCount, row.sessionCount);
    existing.inputTokens += row.inputTokens;
    existing.outputTokens += row.outputTokens;
    existing.cacheReadTokens += row.cacheReadTokens;
    existing.cacheWrite5mTokens += row.cacheWrite5mTokens;
    existing.cacheWrite1hTokens += row.cacheWrite1hTokens;
  }

  return [...merged.values()];
}

/**
 * Uma linha por `(conta, maquina)`, para as metricas que nao tem modelo.
 *
 * A contagem de sessoes usa o **maximo** e nao a soma, pela mesma razao de
 * [mergeModels].
 */
function aggregateByMember(
  rows: ReadonlyArray<MetricsUsageRow>,
): Array<{ accountKey: string; deviceId: string; sessionCount: number }> {
  const byMember = new Map<string, { accountKey: string; deviceId: string; sessionCount: number }>();

  for (const row of rows) {
    const key = `${row.accountKey} ${row.deviceId}`;
    const existing = byMember.get(key);
    if (existing === undefined) {
      byMember.set(key, {
        accountKey: row.accountKey,
        deviceId: row.deviceId,
        sessionCount: row.sessionCount,
      });
      continue;
    }
    existing.sessionCount = Math.max(existing.sessionCount, row.sessionCount);
  }

  return [...byMember.values()];
}

function millisToSeconds(row: MetricsActivityRow): number {
  return row.activeMillis / 1_000;
}
