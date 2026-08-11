import { Router } from 'express';
import type { Config } from '../../config.js';
import { ValidationError } from '../../domain/errors.js';
import { logger } from '../../logger.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { requireTeamKey } from '../auth.js';
import { createIngestSchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface IngestRouterDeps {
  config: Config;
  repository: TeamRepository;
  now: () => number;
}

export function createIngestRouter(deps: IngestRouterDeps): Router {
  const router = Router();
  const schema = createIngestSchema(deps.config.maxTurnsPerRequest);

  router.post(
    '/v1/ingest',
    requireTeamKey(
      deps.config.teamApiKey,
      wrap((req, res) => {
        const parsed = schema.safeParse(req.body);
        if (!parsed.success) {
          throw new ValidationError(formatZodIssues(parsed.error.issues));
        }

        const payload = parsed.data;

        const orphan = findOrphanTurnSessionId(payload);
        if (orphan !== null) {
          throw new ValidationError(
            `Turno referencia a sessao "${orphan}", que nao veio no mesmo lote. ` +
              'Cada lote tem de trazer as sessoes dos turnos que envia.',
          );
        }

        const receipt = deps.repository.ingest(payload, deps.now());

        logger.debug(
          {
            accountKey: payload.accountKey,
            deviceId: payload.member.deviceId,
            acceptedTurns: receipt.acceptedTurns,
            ignoredTurns: receipt.ignoredTurns,
          },
          'ingest concluido',
        );

        res.json(receipt);
      }),
    ),
  );

  return router;
}

/**
 * Devolve o primeiro `sessionId` de turno sem sessao no lote, ou `null`.
 *
 * A consulta de leitura faz `JOIN team_sessions`: um turno gravado sem a sessao
 * correspondente sumiria da agregacao sem nenhum erro. Rejeitar o lote e melhor
 * que aceitar dado que nunca vai aparecer.
 */
function findOrphanTurnSessionId(payload: {
  sessions: Array<{ sessionId: string }>;
  turns: Array<{ sessionId: string }>;
}): string | null {
  const known = new Set(payload.sessions.map((session) => session.sessionId));
  for (const turn of payload.turns) {
    if (!known.has(turn.sessionId)) {
      return turn.sessionId;
    }
  }
  return null;
}

function formatZodIssues(issues: Array<{ path: Array<string | number>; message: string }>): string {
  const formatted = issues
    .slice(0, 5)
    .map((issue) => `${issue.path.join('.') || '(raiz)'}: ${issue.message}`)
    .join('; ');
  return `Payload de ingest invalido — ${formatted}`;
}
