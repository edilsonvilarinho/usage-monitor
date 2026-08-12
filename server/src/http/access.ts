import type { NextFunction, Request, RequestHandler, Response } from 'express';
import type { Config } from '../config.js';
import { ForbiddenError, UnauthorizedError } from '../domain/errors.js';
import { hashKey, type TeamKeyRepository } from '../repositories/teamKeyRepository.js';
import { isValidTeamKey, TEAM_KEY_HEADER } from './auth.js';

export const ADMIN_TOKEN_HEADER = 'x-admin-token';

export interface AccessDeps {
  config: Config;
  keyRepository: TeamKeyRepository;
  now: () => number;
}

/** Como a requisicao foi autorizada. O ingest usa isto para marcar o uso. */
export interface TeamAccess {
  /** `null` para a chave legada de ambiente e para o token de admin. */
  keyId: string | null;
  kind: 'admin' | 'legacy' | 'team-key';
}

/** Extrai a conta alvo da requisicao. Query nas leituras, corpo no ingest. */
export type AccountKeyExtractor = (req: Request) => unknown;

export interface TeamAccessOptions {
  /**
   * Permite amarrar uma conta nova a chave.
   *
   * So o ingest liga isto. Se a leitura tambem reivindicasse, bastaria uma chave
   * recem-criada varrer `accountUuid` conhecidos para adotar contas alheias — a
   * escrita, ao contrario, prova que aquela maquina realmente usa a conta.
   */
  allowClaim?: boolean;
}

const ACCESS_LOCALS_KEY = 'teamAccess';

/** Le a autorizacao resolvida pelo [requireTeamAccess] desta requisicao. */
export function readTeamAccess(res: Response): TeamAccess | null {
  const value = res.locals[ACCESS_LOCALS_KEY] as TeamAccess | undefined;
  return value ?? null;
}

/**
 * Envolve um handler exigindo credencial valida **para a conta pedida**.
 *
 * Sucessor do `requireTeamKey`, que so conferia o segredo unico de ambiente.
 * Continua sendo HOF em vez de middleware encadeado pelo mesmo motivo de la: a
 * rota declara a propria protecao no ponto de uso e nao da para esquecer de
 * proteger uma rota nova.
 *
 * O `x-admin-token` entra como credencial alternativa **de leitura** — e o que
 * evita duplicar `/v1/team`, `/v1/session` e `/v1/member` numa familia admin
 * paralela. Ele nao vale no ingest de proposito: admin le, nao escreve dado de
 * uso em nome de ninguem.
 */
export function requireTeamAccess(
  deps: AccessDeps,
  extractAccountKey: AccountKeyExtractor,
  handler: RequestHandler,
  options: TeamAccessOptions = {},
): RequestHandler {
  const allowClaim = options.allowClaim ?? false;

  return (req: Request, res: Response, next: NextFunction) => {
    try {
      const access = authorize(deps, req, extractAccountKey, allowClaim);
      res.locals[ACCESS_LOCALS_KEY] = access;
      handler(req, res, next);
    } catch (error) {
      next(error);
    }
  };
}

/** Envolve um handler exigindo o token de admin. */
export function requireAdminToken(expectedToken: string, handler: RequestHandler): RequestHandler {
  return (req: Request, res: Response, next: NextFunction) => {
    if (!isValidTeamKey(req.header(ADMIN_TOKEN_HEADER) ?? undefined, expectedToken)) {
      next(new UnauthorizedError('Token de administracao ausente ou invalido.'));
      return;
    }
    handler(req, res, next);
  };
}

function authorize(
  deps: AccessDeps,
  req: Request,
  extractAccountKey: AccountKeyExtractor,
  allowClaim: boolean,
): TeamAccess {
  const adminToken = deps.config.adminToken;
  if (!allowClaim && adminToken !== null) {
    const received = req.header(ADMIN_TOKEN_HEADER);
    if (received !== undefined && isValidTeamKey(received, adminToken)) {
      return { keyId: null, kind: 'admin' };
    }
  }

  const presentedKey = req.header(TEAM_KEY_HEADER);
  if (presentedKey === undefined || presentedKey === '') {
    throw new UnauthorizedError();
  }

  // A chave legada e comparada em tempo constante; a de banco e buscada por
  // hash indexado. Sao 32 bytes aleatorios, entao nao ha prefixo a descobrir
  // incrementalmente — e o mesmo modelo de qualquer API key.
  if (
    deps.config.teamApiKey !== null &&
    deps.config.legacyKeyMode === 'open' &&
    isValidTeamKey(presentedKey, deps.config.teamApiKey)
  ) {
    return { keyId: null, kind: 'legacy' };
  }

  const resolved = deps.keyRepository.resolve(hashKey(presentedKey));
  if (resolved === null) {
    throw new UnauthorizedError();
  }

  const accountKey = readAccountKey(extractAccountKey(req));
  if (accountKey === null) {
    // Sem conta no pedido nao ha o que autorizar. A validacao de formato do
    // handler responderia 400 depois, mas nao da para deixar passar um pedido
    // sem escopo por um caminho que decide acesso.
    throw new ForbiddenError('Requisicao sem conta alvo.');
  }

  if (resolved.accounts.includes(accountKey)) {
    return { keyId: resolved.id, kind: 'team-key' };
  }

  if (allowClaim && resolved.accounts.length < resolved.maxAccounts) {
    const claimed = deps.keyRepository.claimAccount(resolved.id, accountKey, deps.now());
    if (claimed) {
      return { keyId: resolved.id, kind: 'team-key' };
    }
    throw new ForbiddenError('Esta conta ja pertence a outra chave de time.');
  }

  throw new ForbiddenError();
}

function readAccountKey(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}
