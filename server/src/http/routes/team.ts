import { Router } from 'express';
import type { Config } from '../../config.js';
import { NotFoundError, ValidationError } from '../../domain/errors.js';
import type { TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { requireTeamAccess, type AccessDeps } from '../access.js';
import {
  DEFAULT_GAP_CUTOFF_MS,
  sessionQuerySchema,
  teamQuerySchema,
  trendQuerySchema,
} from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface TeamRouterDeps {
  config: Config;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
  now: () => number;
}

/** Janela padrao da serie diaria: um mes cobre a leitura tipica sem inchar. */
const DEFAULT_TREND_DAYS = 30;

const MILLIS_PER_DAY = 24 * 60 * 60 * 1_000;

/** A conta alvo destas rotas vem sempre da query. */
const accountFromQuery = (req: { query: Record<string, unknown> }): unknown => req.query.accountKey;

export function createTeamRouter(deps: TeamRouterDeps): Router {
  const router = Router();
  const access: AccessDeps = {
    config: deps.config,
    keyRepository: deps.keyRepository,
    repository: deps.repository,
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
        // A janela e semiaberta: `since <= ts < until`.
        const snapshot = deps.repository.readTeam(
          parsed.data.accountKey,
          parsed.data.since ?? null,
          parsed.data.until ?? null,
          parsed.data.gapCutoffMs ?? DEFAULT_GAP_CUTOFF_MS,
        );

        res.json(snapshot);
      }),
    ),
  );

  /**
   * Serie diaria da conta, para a tendencia do time.
   *
   * Mesma familia de leitura de `/v1/team`: o acesso passa pelo mesmo
   * `requireTeamAccess` com a conta na query, e nenhum `GET` reivindica conta.
   *
   * Devolve linhas cruas por `(maquina, dia, modelo)` — o servidor continua sem
   * precificar nada, e o cliente aplica a propria tabela de precos, como ja faz
   * com `/v1/team` e `/v1/session`.
   */
  router.get(
    '/v1/team/trend',
    requireTeamAccess(
      access,
      accountFromQuery,
      wrap((req, res) => {
        const parsed = trendQuerySchema.safeParse(req.query);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
          );
        }

        // `since` explicito vence; sem ele, a contagem de dias a partir de agora,
        // que e o que o desktop manda hoje e nao pode quebrar.
        const days = parsed.data.days ?? DEFAULT_TREND_DAYS;
        const since = parsed.data.since ?? deps.now() - days * MILLIS_PER_DAY;

        res.json(deps.repository.readTrend(parsed.data.accountKey, since, parsed.data.until ?? null));
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

  return router;
}
