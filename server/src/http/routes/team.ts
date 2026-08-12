import { Router } from 'express';
import type { Config } from '../../config.js';
import { NotFoundError, ValidationError } from '../../domain/errors.js';
import type { TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { logger } from '../../logger.js';
import { requireTeamAccess, type AccessDeps } from '../access.js';
import { deleteMemberQuerySchema, sessionQuerySchema, teamQuerySchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface TeamRouterDeps {
  config: Config;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
  now: () => number;
}

/** A conta alvo destas rotas vem sempre da query. */
const accountFromQuery = (req: { query: Record<string, unknown> }): unknown => req.query.accountKey;

export function createTeamRouter(deps: TeamRouterDeps): Router {
  const router = Router();
  const access: AccessDeps = {
    config: deps.config,
    keyRepository: deps.keyRepository,
    now: deps.now,
  };

  router.get(
    '/v1/team',
    requireTeamAccess(
      access,
      accountFromQuery,
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
   * Detalhe de uma sessao: metadados e turnos crus, em ordem.
   *
   * Existe porque o transcript e de outra maquina e nao esta no disco de quem
   * consulta — mas os turnos estao aqui desde o primeiro ingest. O cliente
   * sintetiza a ordem, aplica a propria tabela de precos e monta os mesmos
   * graficos do modal local.
   *
   * Nao trafega conteudo de prompt ou resposta: um turno e contagem de token e
   * modelo, igual ao que o `POST /v1/ingest` recebeu.
   */
  router.get(
    '/v1/session',
    requireTeamAccess(
      access,
      accountFromQuery,
      wrap((req, res) => {
        const parsed = sessionQuerySchema.safeParse(req.query);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
          );
        }

        // O escopo e sempre a conta pedida: uma resposta nunca mistura contas.
        const detail = deps.repository.readSession(
          parsed.data.accountKey,
          parsed.data.deviceId,
          parsed.data.sessionId,
        );

        if (detail === null) {
          throw new NotFoundError('Sessao nao encontrada para esta conta e maquina.');
        }

        res.json(detail);
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
    requireTeamAccess(
      access,
      accountFromQuery,
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
