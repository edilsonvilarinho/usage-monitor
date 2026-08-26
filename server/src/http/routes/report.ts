import { Router } from 'express';
import type { Config } from '../../config.js';
import {
  CACHE_MULTIPLIERS,
  MODEL_MATCH_RULE,
  MODEL_PRICING,
  PRICING_VERSION,
  SYNTHETIC_MODEL_ID,
} from '../../domain/modelPricing.js';
import { requireGlobalRead } from '../access.js';
import { wrap } from '../errorHandler.js';

export interface ReportRouterDeps {
  config: Config;
}

/**
 * Rotas de consumo externo.
 *
 * Montadas incondicionalmente, ao contrario da administracao: sem
 * `TEAM_REPORT_TOKEN` elas existem e respondem 401, e o `x-admin-token` continua
 * lendo. Uma rota que some quando a variavel falta faria "credencial errada" e
 * "servidor sem a variavel" chegarem ao consumidor como o mesmo 404.
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

  return router;
}
