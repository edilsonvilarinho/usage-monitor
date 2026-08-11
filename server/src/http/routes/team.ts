import { Router } from 'express';
import type { Config } from '../../config.js';
import { ValidationError } from '../../domain/errors.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { logger } from '../../logger.js';
import { requireTeamKey } from '../auth.js';
import { deleteMemberQuerySchema, teamQuerySchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface TeamRouterDeps {
  config: Config;
  repository: TeamRepository;
}

export function createTeamRouter(deps: TeamRouterDeps): Router {
  const router = Router();

  router.get(
    '/v1/team',
    requireTeamKey(
      deps.config.teamApiKey,
      wrap((req, res) => {
        const parsed = teamQuerySchema.safeParse(req.query);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
          );
        }

        // O escopo e sempre a conta pedida: uma resposta nunca mistura contas.
        const snapshot = deps.repository.readTeam(
          parsed.data.accountKey,
          parsed.data.since ?? null,
        );

        res.json(snapshot);
      }),
    ),
  );

  /**
   * Remove um integrante da conta, com tudo o que ele enviou.
   *
   * Existe para desfazer duplicata: a mesma maquina que perdeu o `team.json`
   * volta com outro `deviceId` e o antigo fica na lista sem atividade ate a
   * retencao passar. Idempotente — device desconhecido responde 200 com zeros.
   */
  router.delete(
    '/v1/member',
    requireTeamKey(
      deps.config.teamApiKey,
      wrap((req, res) => {
        const parsed = deleteMemberQuerySchema.safeParse(req.query);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
          );
        }

        const report = deps.repository.deleteMember(
          parsed.data.accountKey,
          parsed.data.deviceId,
        );

        logger.debug(
          { accountKey: parsed.data.accountKey, deviceId: parsed.data.deviceId, ...report },
          'integrante removido',
        );

        res.json(report);
      }),
    ),
  );

  return router;
}
