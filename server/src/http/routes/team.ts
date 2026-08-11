import { Router } from 'express';
import type { Config } from '../../config.js';
import { ValidationError } from '../../domain/errors.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { requireTeamKey } from '../auth.js';
import { teamQuerySchema } from '../dto.js';
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

  return router;
}
