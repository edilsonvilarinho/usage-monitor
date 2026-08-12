import { Router } from 'express';
import type { Config } from '../../config.js';
import { ValidationError } from '../../domain/errors.js';
import { logger } from '../../logger.js';
import type { TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { readTeamAccess, requireTeamAccess, type AccessDeps } from '../access.js';
import { createIngestSchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface IngestRouterDeps {
  config: Config;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
  now: () => number;
}

export function createIngestRouter(deps: IngestRouterDeps): Router {
  const router = Router();
  const schema = createIngestSchema(deps.config.maxTurnsPerRequest);
  const access: AccessDeps = {
    config: deps.config,
    keyRepository: deps.keyRepository,
    now: deps.now,
  };

  router.post(
    '/v1/ingest',
    requireTeamAccess(
      access,
      // O corpo ja foi desserializado pelo `express.json` antes do roteamento;
      // a autorizacao le o campo cru e o zod confere o formato logo abaixo.
      (req) => (req.body as { accountKey?: unknown } | undefined)?.accountKey,
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

        const now = deps.now();
        const receipt = deps.repository.ingest(payload, now);

        // Marcador de uso da chave. So aqui, e nao nas leituras: o ingest ja e
        // caminho de escrita, enquanto marcar na leitura custaria uma escrita em
        // WAL a cada tique de 5s de cada janela de time aberta.
        const granted = readTeamAccess(res);
        if (granted?.keyId != null) {
          deps.keyRepository.touch(granted.keyId, now);
        }

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
      { allowClaim: true },
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
