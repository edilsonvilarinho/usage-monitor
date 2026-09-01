import { Router } from 'express';
import type { Config } from '../../config.js';
import { ForbiddenError, UnauthorizedError, ValidationError } from '../../domain/errors.js';
import { hashKey, type ResolvedTeamKey, type TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import {
  ADMIN_TOKEN_HEADER,
  MAX_ACCOUNTS_MESSAGE,
  OTHER_KEY_MESSAGE,
  assertAllowedByLabel,
  assertNotBlocked,
} from '../access.js';
import { isValidTeamKey, TEAM_KEY_HEADER } from '../auth.js';
import { claimBodySchema, verifyQuerySchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface VerifyRouterDeps {
  config: Config;
  keyRepository: TeamKeyRepository;
  /** Le o e-mail gravado da conta, para as travas responderem como no ingest. */
  repository: TeamRepository;
  now: () => number;
}

/**
 * Resposta de quem tem acesso amplo — administrador ou chave legada aberta.
 *
 * Nenhum dos dois pertence a uma conta, entao nao ha vinculo a criar nem a
 * informar; `claimed: true` aqui significa "voce le esta conta", que e verdade.
 */
const WIDE_ACCESS_RESPONSE = {
  authorized: true,
  claimed: true,
  label: null,
  maxAccounts: 0,
  claimedAccounts: 0,
};

/**
 * Verificacao e vinculo da chave de time com uma conta.
 *
 * `GET /v1/verify` responde se a chave cobre — ou pode cobrir — a conta, **sem
 * criar vinculo**. `POST /v1/claim` cria.
 *
 * A separacao existe porque leitura que reivindica seria varredura: bastaria uma
 * chave nova consultar `accountUuid` conhecidos para adotar contas alheias. Um
 * `POST` explicito, disparado por clique do usuario, tem a mesma forca do ingest
 * — que tambem carrega o `accountKey` auto-declarado no corpo — e resolve o
 * problema de o vinculo depender de haver atividade nova no Claude Code.
 */
export function createVerifyRouter(deps: VerifyRouterDeps): Router {
  const router = Router();

  router.get(
    '/v1/verify',
    wrap((req, res) => {
      const parsed = verifyQuerySchema.safeParse(req.query);
      if (!parsed.success) {
        throw new ValidationError(describeIssue(parsed.error.issues[0]));
      }

      const resolution = resolveCredential(deps, req.header(ADMIN_TOKEN_HEADER), req.header(TEAM_KEY_HEADER));
      if (resolution === 'wide') {
        res.json(WIDE_ACCESS_RESPONSE);
        return;
      }

      const accountKey = parsed.data.accountKey;
      requireAdmissible(deps, resolution, accountKey, parsed.data.accountEmail ?? null);
      if (!resolution.accounts.includes(accountKey)) {
        requireClaimable(deps, resolution, accountKey);
      }

      res.json(describeKey(deps, resolution, accountKey));
    }),
  );

  /**
   * Vincula a conta a chave apresentada. Idempotente.
   *
   * E o que o botao "Testar conexao" do app chama: sem ele o vinculo so nascia
   * dentro de um ingest, e trocar a chave numa maquina sem turno pendente nao
   * gerava requisicao nenhuma — a conta ficava sem dona indefinidamente.
   */
  router.post(
    '/v1/claim',
    wrap((req, res) => {
      const parsed = claimBodySchema.safeParse(req.body);
      if (!parsed.success) {
        throw new ValidationError(describeIssue(parsed.error.issues[0]));
      }

      const resolution = resolveCredential(deps, req.header(ADMIN_TOKEN_HEADER), req.header(TEAM_KEY_HEADER));
      if (resolution === 'wide') {
        // Administrador e chave legada leem tudo e nao representam conta: nao ha
        // vinculo a criar em nome de ninguem.
        res.json(WIDE_ACCESS_RESPONSE);
        return;
      }

      const accountKey = parsed.data.accountKey;
      requireAdmissible(deps, resolution, accountKey, parsed.data.accountEmail ?? null);
      if (!resolution.accounts.includes(accountKey)) {
        requireClaimable(deps, resolution, accountKey);

        const claimed = deps.keyRepository.claimAccount(resolution.id, accountKey, deps.now());
        if (!claimed) {
          // Perdeu a corrida para outra chave entre a checagem e o insert.
          throw new ForbiddenError(OTHER_KEY_MESSAGE);
        }
      }

      const refreshed = deps.keyRepository.resolve(hashKey(req.header(TEAM_KEY_HEADER) ?? ''));
      res.json(describeKey(deps, refreshed ?? resolution, accountKey));
    }),
  );

  return router;
}

/** `'wide'` para acesso amplo; senao a chave de time resolvida. */
function resolveCredential(
  deps: VerifyRouterDeps,
  adminHeader: string | undefined,
  teamKeyHeader: string | undefined,
): ResolvedTeamKey | 'wide' {
  const adminToken = deps.config.adminToken;
  if (adminToken !== null && adminHeader !== undefined && isValidTeamKey(adminHeader, adminToken)) {
    return 'wide';
  }

  if (teamKeyHeader === undefined || teamKeyHeader === '') {
    throw new UnauthorizedError();
  }

  // A chave legada continua respondendo "autorizada" enquanto o modo aberto
  // valer: ela de fato le tudo, e informar o contrario faria o app avisar de um
  // problema que nao existe naquele deploy.
  if (
    deps.config.teamApiKey !== null &&
    deps.config.legacyKeyMode === 'open' &&
    isValidTeamKey(teamKeyHeader, deps.config.teamApiKey)
  ) {
    return 'wide';
  }

  const resolved = deps.keyRepository.resolve(hashKey(teamKeyHeader));
  if (resolved === null) {
    throw new UnauthorizedError();
  }
  return resolved;
}

/**
 * As mesmas duas travas do ingest, aplicadas antes de responder.
 *
 * Reusa os asserts de `access.ts` em vez de repetir a regra: uma segunda copia
 * daria duas respostas para a mesma pergunta, e esta rota existe justamente para
 * antecipar o veredito do envio. Aprovar aqui e recusar la transformaria um erro
 * de configuracao numa sincronia parada em silencio — o mesmo motivo pelo qual
 * [requireClaimable] ja recusa dona diferente.
 *
 * Vale tambem para a conta **ja vinculada**: no `GET`, um "autorizada" para quem
 * o ingest recusa e a mentira mais cara que esta rota pode contar.
 */
function requireAdmissible(
  deps: VerifyRouterDeps,
  resolution: ResolvedTeamKey,
  accountKey: string,
  accountEmail: string | null,
): void {
  assertNotBlocked(deps, accountKey);
  assertAllowedByLabel(deps, resolution, accountKey, accountEmail);
}

/**
 * Recusa quando a conta nao esta vinculada **e** nao pode ser.
 *
 * Dona diferente e recusa imediata: dizer "autorizada" aqui e falhar no vinculo
 * seguinte transformaria um erro de configuracao numa sincronia parada em
 * silencio.
 */
function requireClaimable(
  deps: VerifyRouterDeps,
  resolved: ResolvedTeamKey,
  accountKey: string,
): void {
  const owner = deps.keyRepository.ownerOf(accountKey);
  if (owner !== null && owner !== resolved.id) {
    throw new ForbiddenError(OTHER_KEY_MESSAGE);
  }
  if (resolved.accounts.length >= resolved.maxAccounts) {
    throw new ForbiddenError(MAX_ACCOUNTS_MESSAGE);
  }
}

function describeKey(deps: VerifyRouterDeps, resolved: ResolvedTeamKey, accountKey: string) {
  const record = deps.keyRepository.findById(resolved.id);
  return {
    authorized: true,
    claimed: resolved.accounts.includes(accountKey),
    label: record?.label ?? null,
    maxAccounts: resolved.maxAccounts,
    claimedAccounts: resolved.accounts.length,
  };
}

function describeIssue(issue: { path: Array<string | number>; message: string } | undefined): string {
  return `Requisicao invalida — ${issue ? `${issue.path.join('.')}: ${issue.message}` : 'parametros ausentes'}`;
}
