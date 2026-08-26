import { Router, type Request } from 'express';
import type { z } from 'zod';
import type { Config } from '../../config.js';
import {
  CACHE_MULTIPLIERS,
  MODEL_MATCH_RULE,
  MODEL_PRICING,
  PRICING_VERSION,
  SYNTHETIC_MODEL_ID,
} from '../../domain/modelPricing.js';
import { ValidationError } from '../../domain/errors.js';
import type { TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type { ReportCursor, TeamRepository } from '../../repositories/teamRepository.js';
import { requireGlobalRead } from '../access.js';
import {
  DEFAULT_GAP_CUTOFF_MS,
  DEFAULT_REPORT_LIMIT,
  reportActivityQuerySchema,
  reportUsageQuerySchema,
} from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface ReportRouterDeps {
  config: Config;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
}

/**
 * Rotas de consumo externo.
 *
 * Montadas incondicionalmente, ao contrario da administracao: sem
 * `TEAM_REPORT_TOKEN` elas existem e respondem 401, e o `x-admin-token` continua
 * lendo. Uma rota que some quando a variavel falta faria "credencial errada" e
 * "servidor sem a variavel" chegarem ao consumidor como o mesmo 404.
 *
 * Sao rotas **planas e paginadas**, e nao uma versao paginada de
 * `/admin/v1/overview`: aquela resposta monta a tela do app, cujo
 * `flattenAccounts`/`toUsageBreakdown` assume o conjunto inteiro — uma pagina
 * parcial subestimaria os totais da tela sem erro nenhum.
 */
export function createReportRouter(deps: ReportRouterDeps): Router {
  const router = Router();

  /**
   * Tabela de precos publicada como dado.
   *
   * O servidor **continua nao precificando** — quem consome aplica a aritmetica,
   * como o app desktop ja faz com `/v1/team` e `/v1/session`. Publicar a tabela e
   * o que impede o consumidor de manter uma copia a mao, que divergiria na
   * proxima mudanca de preco.
   *
   * Dois contratos vao explicitos no corpo porque nao sao derivaveis da lista:
   * `<synthetic>` e preco zero **conhecido** (trata-lo como desconhecido marcaria
   * sessoes inteiras como custo incompleto), e modelo nao reconhecido devolve
   * custo **indisponivel**, nunca zero.
   */
  router.get(
    '/v1/pricing',
    requireGlobalRead(
      deps.config,
      wrap((_req, res) => {
        res.json({
          version: PRICING_VERSION,
          models: MODEL_PRICING,
          cacheMultipliers: CACHE_MULTIPLIERS,
          matchRule: MODEL_MATCH_RULE,
          syntheticModelId: SYNTHETIC_MODEL_ID,
        });
      }),
    ),
  );

  /**
   * Consumo cru por `(conta, maquina, sessao, modelo)`, no recorte pedido.
   *
   * Ordenado pela propria chave de agrupamento, e nao por recencia: quem pagina
   * um periodo fechado quer completude, e recencia nao da ordem total sem
   * desempate. Sem `since` a consulta agrupa a tabela inteira — o mesmo custo
   * que `/admin/v1/overview` ja paga hoje.
   */
  router.get(
    '/v1/report/usage',
    requireGlobalRead(
      deps.config,
      wrap((req, res) => {
        const query = parseQuery(reportUsageQuerySchema, req);
        const page = deps.repository.readReportUsage({
          since: query.since ?? null,
          until: query.until ?? null,
          limit: query.limit ?? DEFAULT_REPORT_LIMIT,
          cursor: decodeCursor(query.cursor),
        });

        res.json({ rows: page.rows, nextCursor: encodeCursor(page.nextCursor) });
      }),
    ),
  );

  /**
   * Tempo de trabalho por sessao, na mesma janela e com a mesma paginacao.
   *
   * Lista separada do consumo, e nunca uma coluna dele: a linha de uso e
   * `(maquina, sessao, modelo)`, e uma sessao que trocou de modelo no meio
   * apareceria varias vezes — o consumidor somaria a hora uma vez por modelo.
   */
  router.get(
    '/v1/report/activity',
    requireGlobalRead(
      deps.config,
      wrap((req, res) => {
        const query = parseQuery(reportActivityQuerySchema, req);
        const page = deps.repository.readReportActivity({
          since: query.since ?? null,
          until: query.until ?? null,
          gapCutoffMs: query.gapCutoffMs ?? DEFAULT_GAP_CUTOFF_MS,
          limit: query.limit ?? DEFAULT_REPORT_LIMIT,
          cursor: decodeCursor(query.cursor),
        });

        res.json({ rows: page.rows, nextCursor: encodeCursor(page.nextCursor) });
      }),
    ),
  );

  /**
   * Quem e quem: contas, rotulo, e-mail e integrantes.
   *
   * Sem paginacao — o tamanho da resposta e o tamanho do time, nao o do
   * historico.
   *
   * `emailSource` sai sempre junto de `accountEmail` de proposito: `reported` e o
   * e-mail que a propria conta Anthropic informou, e `label` e texto que o admin
   * digitou sem verificacao nenhuma. Um consumidor que ignore o campo trata
   * rotulo administrativo como identidade de pessoa.
   */
  router.get(
    '/v1/report/members',
    requireGlobalRead(
      deps.config,
      wrap((_req, res) => {
        res.json({ accounts: deps.repository.readReportMembers(deps.keyRepository.accountLabels()) });
      }),
    ),
  );

  return router;
}

/**
 * Valida a query e traduz a primeira issue para 400.
 *
 * Uma funcao para as tres rotas: o bloco repetido em `team.ts` e `admin.ts` e
 * anterior a elas, e triplica-lo aqui seria triplicar tambem a mensagem.
 */
function parseQuery<T extends z.ZodTypeAny>(schema: T, req: Request): z.infer<T> {
  const parsed = schema.safeParse(req.query);
  if (!parsed.success) {
    const first = parsed.error.issues[0];
    throw new ValidationError(
      `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
    );
  }
  return parsed.data;
}

/**
 * Cursor opaco: base64url do JSON da posicao.
 *
 * Opaco porque a ordem e detalhe do servidor — um cursor legivel viraria
 * contrato e prenderia a ordenacao. Cursor ilegivel responde **400**, e nao 500:
 * e entrada do cliente, nao defeito do servidor.
 */
function decodeCursor(raw: string | undefined): ReportCursor | null {
  if (raw === undefined) {
    return null;
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(Buffer.from(raw, 'base64url').toString('utf8'));
  } catch {
    throw new ValidationError('Cursor invalido.');
  }

  if (parsed === null || typeof parsed !== 'object') {
    throw new ValidationError('Cursor invalido.');
  }

  const candidate = parsed as Record<string, unknown>;
  if (
    typeof candidate.accountKey !== 'string' ||
    typeof candidate.deviceId !== 'string' ||
    typeof candidate.sessionId !== 'string' ||
    typeof candidate.model !== 'string'
  ) {
    throw new ValidationError('Cursor invalido.');
  }

  return {
    accountKey: candidate.accountKey,
    deviceId: candidate.deviceId,
    sessionId: candidate.sessionId,
    model: candidate.model,
  };
}

function encodeCursor(cursor: ReportCursor | null): string | null {
  if (cursor === null) {
    return null;
  }
  return Buffer.from(JSON.stringify(cursor), 'utf8').toString('base64url');
}
