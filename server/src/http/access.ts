import type { NextFunction, Request, RequestHandler, Response } from 'express';
import type { Config } from '../config.js';
import { normalizeAccountEmail } from '../domain/accountEmail.js';
import { ForbiddenError, UnauthorizedError } from '../domain/errors.js';
import { isAccountAllowedByLabel } from '../domain/teamKeyLabel.js';
import { logger } from '../logger.js';
import { hashKey, type ResolvedTeamKey, type TeamKeyRepository } from '../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../repositories/teamRepository.js';
import { isValidTeamKey, TEAM_KEY_HEADER } from './auth.js';

export const ADMIN_TOKEN_HEADER = 'x-admin-token';
export const REPORT_TOKEN_HEADER = 'x-report-key';

export interface AccessDeps {
  config: Config;
  keyRepository: TeamKeyRepository;
  /** Le o e-mail que a conta reportou, para o portao do rotulo valer nas leituras. */
  repository: TeamRepository;
  now: () => number;
}

/** Como a requisicao foi autorizada. O ingest usa isto para marcar o uso. */
export interface TeamAccess {
  /** `null` para a chave legada de ambiente, para o token de admin e para o de relatorio. */
  keyId: string | null;
  kind: 'admin' | 'legacy' | 'report' | 'team-key';
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
  /**
   * Le o e-mail que a conta declara **nesta** requisicao.
   *
   * So ingest e presenca o tem, porque so eles carregam corpo. As leituras caem
   * no e-mail gravado, que e a mesma informacao vinda da escrita anterior — a
   * memoria e confiavel porque `upsertAccountEmail` nunca a sobrescreve com
   * nulo.
   */
  extractAccountEmail?: (req: Request) => unknown;
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
  const extractAccountEmail = options.extractAccountEmail;

  return (req: Request, res: Response, next: NextFunction) => {
    try {
      const access = authorize(deps, req, extractAccountKey, allowClaim, extractAccountEmail);
      res.locals[ACCESS_LOCALS_KEY] = access;
      handler(req, res, next);
    } catch (error) {
      next(error);
    }
  };
}

/**
 * Envolve um handler exigindo credencial de **leitura global**: `x-admin-token`
 * ou `x-report-key`, sem escopo de conta.
 *
 * Separado de [requireTeamAccess] porque aqui nao ha conta a autorizar — a rota
 * ou devolve dado de todo mundo, ou nao devolve nada. Chave de time nao serve:
 * ela e por conta, e uma leitura global feita com ela devolveria as outras.
 */
export function requireGlobalRead(
  tokens: Pick<Config, 'adminToken' | 'reportToken'>,
  handler: RequestHandler,
): RequestHandler {
  return (req: Request, res: Response, next: NextFunction) => {
    const access = authorizeGlobalRead(tokens, req);
    if (access === null) {
      next(new UnauthorizedError('Credencial de leitura global ausente ou invalida.'));
      return;
    }
    res.locals[ACCESS_LOCALS_KEY] = access;
    handler(req, res, next);
  };
}

function authorizeGlobalRead(
  tokens: Pick<Config, 'adminToken' | 'reportToken'>,
  req: Request,
): TeamAccess | null {
  if (
    tokens.adminToken !== null &&
    isValidTeamKey(req.header(ADMIN_TOKEN_HEADER) ?? undefined, tokens.adminToken)
  ) {
    return { keyId: null, kind: 'admin' };
  }
  if (
    tokens.reportToken !== null &&
    isValidTeamKey(req.header(REPORT_TOKEN_HEADER) ?? undefined, tokens.reportToken)
  ) {
    return { keyId: null, kind: 'report' };
  }
  return null;
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
  extractAccountEmail?: (req: Request) => unknown,
): TeamAccess {
  // Antes de qualquer decisao de credencial no caminho de escrita: conta
  // declarada fora do time nao entra por porta nenhuma, nem pela chave legada em
  // modo aberto. Escrever era justamente o que trazia a conta de volta depois de
  // o admin a remover.
  if (allowClaim) {
    assertNotBlocked(deps, readAccountKey(extractAccountKey(req)));
  }

  const adminToken = deps.config.adminToken;
  if (!allowClaim && adminToken !== null) {
    const received = req.header(ADMIN_TOKEN_HEADER);
    if (received !== undefined && isValidTeamKey(received, adminToken)) {
      return { keyId: null, kind: 'admin' };
    }
  }

  // O token de relatorio entra no mesmo ponto do de admin — dentro do
  // `!allowClaim`, que e o que ja o recusa no ingest e na presenca. Ele le
  // qualquer conta e nao reivindica nenhuma: reivindicar e escrita.
  const reportToken = deps.config.reportToken;
  if (!allowClaim && reportToken !== null) {
    const received = req.header(REPORT_TOKEN_HEADER);
    if (received !== undefined && isValidTeamKey(received, reportToken)) {
      return { keyId: null, kind: 'report' };
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

  // Tambem na leitura: quem foi declarado fora do time nao le o time.
  assertNotBlocked(deps, accountKey);

  // **Antes** do teste de vinculo, e nao so no ramo que reivindica: e isto que
  // faz o portao valer para os vinculos que ja existiam. Uma conta que entrou
  // quando nada era conferido continuaria sincronizando para sempre se a
  // verificacao morasse so no caminho do `claim`.
  assertAllowedByLabel(deps, resolved, accountKey, readAccountEmail(req, extractAccountEmail));

  if (resolved.accounts.includes(accountKey)) {
    return { keyId: resolved.id, kind: 'team-key' };
  }

  if (allowClaim && resolved.accounts.length < resolved.maxAccounts) {
    const claimed = deps.keyRepository.claimAccount(resolved.id, accountKey, deps.now());
    if (claimed) {
      return { keyId: resolved.id, kind: 'team-key' };
    }
    throw new ForbiddenError(OTHER_KEY_MESSAGE);
  }

  throw new ForbiddenError(describeRefusal(deps, resolved, accountKey));
}

/**
 * Recusa a conta que o admin declarou fora do time.
 *
 * A trava mais forte que existe aqui, e por isso a primeira: ela vem de uma
 * decisao explicita de quem administra, enquanto o portao do rotulo e regra
 * derivada de um texto. `TEAM_KEY_LABEL_MATCH=off` desliga aquele e **nao**
 * desliga este — desfazer a decisao do admin por variavel de ambiente seria
 * outra pessoa decidindo.
 */
export function assertNotBlocked(deps: AccessDeps, accountKey: string | null): void {
  if (accountKey === null) {
    return;
  }
  if (deps.keyRepository.isAccountBlocked(accountKey)) {
    logger.warn({ accountKey }, 'requisicao de conta declarada fora do time');
    throw new ForbiddenError(BLOCKED_ACCOUNT_MESSAGE);
  }
}

/**
 * Recusa a conta que nao esta na relacao declarada no rotulo da chave.
 *
 * O e-mail do pedido vence o gravado: numa maquina que trocou de conta, o
 * gravado descreve a anterior. O gravado cobre as leituras, que nao carregam
 * e-mail nenhum.
 */
export function assertAllowedByLabel(
  deps: AccessDeps,
  resolved: ResolvedTeamKey,
  accountKey: string,
  reportedEmail: string | null,
): void {
  if (deps.config.keyLabelMatch === 'off') {
    return;
  }

  const accountEmail = reportedEmail ?? deps.repository.accountEmailOf(accountKey);
  if (isAccountAllowedByLabel(resolved.label, accountEmail)) {
    return;
  }

  logger.warn(
    { keyId: resolved.id, label: resolved.label, accountKey, accountEmail },
    'conta fora da relacao declarada no rotulo da chave',
  );
  throw new ForbiddenError(labelMismatchMessage(resolved.label, accountEmail));
}

function readAccountEmail(req: Request, extract?: (req: Request) => unknown): string | null {
  if (extract === undefined) {
    return null;
  }
  const value = extract(req);
  return typeof value === 'string' ? normalizeAccountEmail(value) : null;
}

export const OTHER_KEY_MESSAGE = 'Esta conta ja pertence a outra chave de time.';

export const BLOCKED_ACCOUNT_MESSAGE =
  'Esta conta foi removida do time pelo administrador e nao participa mais dele. ' +
  'Desmarque-a em Configuracoes > Time, ou peca ao administrador para devolve-la ao time.';

/**
 * Diz **quem** a chave cobre e **qual** conta foi recusada.
 *
 * As duas metades importam: sem a primeira o usuario nao sabe se colou a chave
 * de outra pessoa; sem a segunda ele nao sabe qual das contas marcadas na
 * maquina e a intrusa — e o caso tipico e justamente uma maquina com duas.
 */
export function labelMismatchMessage(label: string, accountEmail: string | null): string {
  const account = accountEmail ?? 'sem e-mail conhecido';
  return (
    `Esta chave de time e de ${label}, e a conta ${account} nao esta na relacao dela. ` +
    'Desmarque essa conta em Configuracoes > Time, ou peca ao administrador para incluir ' +
    'o e-mail dela no rotulo da chave.'
  );
}

export const NOT_CLAIMED_MESSAGE =
  'Esta conta ainda nao foi vinculada a esta chave. O vinculo nasce no primeiro envio: ' +
  'use "Testar conexao" nas Configuracoes para vincular agora.';

export const MAX_ACCOUNTS_MESSAGE =
  'Esta chave ja atingiu o limite de contas. Peca ao administrador para aumentar o limite ' +
  'ou emitir outra chave.';

/**
 * Explica **por que** a leitura foi recusada.
 *
 * Os tres casos tem consertos diferentes e a mensagem generica anterior nao
 * distinguia nenhum deles: quem colou a chave certa e ainda nao sincronizou lia
 * o mesmo texto de quem colou a chave de outra pessoa.
 */
function describeRefusal(
  deps: AccessDeps,
  resolved: { id: string; maxAccounts: number; accounts: string[] },
  accountKey: string,
): string {
  const owner = deps.keyRepository.ownerOf(accountKey);
  if (owner !== null && owner !== resolved.id) {
    return OTHER_KEY_MESSAGE;
  }
  if (resolved.accounts.length >= resolved.maxAccounts) {
    return MAX_ACCOUNTS_MESSAGE;
  }
  return NOT_CLAIMED_MESSAGE;
}

function readAccountKey(value: unknown): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}
