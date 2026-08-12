import { Router } from 'express';
import { NotFoundError, ValidationError } from '../../domain/errors.js';
import { logger } from '../../logger.js';
import type { TeamKeyRepository, TeamKeyRecord } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { requireAdminToken } from '../access.js';
import { createKeyBodySchema, overviewQuerySchema, updateKeyBodySchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface AdminRouterDeps {
  adminToken: string;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
  now: () => number;
}

/**
 * Administracao das chaves de time, consumida pelo app desktop.
 *
 * So e montada quando `TEAM_ADMIN_TOKEN` existe. Sem ela um deploy antigo
 * continua exatamente como estava, e nao ha superficie administrativa exposta em
 * quem nao pediu por uma.
 *
 * O que a torna usavel sem ninguem descobrir `accountUuid` de ninguem: a chave
 * nasce sem conta e o `label` e texto livre — normalmente o e-mail da pessoa,
 * digitado por quem administra. O servidor **nao** verifica esse rotulo; a
 * verdade e a lista `accounts`, preenchida no primeiro ingest de cada chave.
 */
export function createAdminRouter(deps: AdminRouterDeps): Router {
  const router = Router();
  const protect = (handler: Parameters<typeof requireAdminToken>[1]) =>
    requireAdminToken(deps.adminToken, handler);

  /** Existe para o botao "Validar" do app dizer sim ou nao sem efeito colateral. */
  router.get(
    '/admin/v1/ping',
    protect(
      wrap((_req, res) => {
        res.json({ status: 'ok' });
      }),
    ),
  );

  /**
   * Todas as contas de uma vez.
   *
   * E o que alimenta a visao global: quem administra o servidor nao esta
   * necessariamente logado em nenhuma das contas que administra, entao a lista
   * nao pode depender de credencial de conta.
   */
  router.get(
    '/admin/v1/overview',
    protect(
      wrap((req, res) => {
        const parsed = overviewQuerySchema.safeParse(req.query);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
          );
        }

        const accounts = deps.repository.readOverview(
          parsed.data.since ?? null,
          deps.keyRepository.accountLabels(),
        );

        res.json({ accounts });
      }),
    ),
  );

  router.get(
    '/admin/v1/keys',
    protect(
      wrap((_req, res) => {
        res.json({ keys: deps.keyRepository.list().map(toKeyResponse) });
      }),
    ),
  );

  router.post(
    '/admin/v1/keys',
    protect(
      wrap((req, res) => {
        const parsed = createKeyBodySchema.safeParse(req.body);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Corpo invalido — ${first ? `${first.path.join('.')}: ${first.message}` : 'campos ausentes'}`,
          );
        }

        const created = deps.keyRepository.create(parsed.data, deps.now());
        logger.debug({ keyId: created.id, label: created.label }, 'chave de time criada');
        res.status(201).json(toKeyResponse(created));
      }),
    ),
  );

  router.patch(
    '/admin/v1/keys/:id',
    protect(
      wrap((req, res) => {
        const parsed = updateKeyBodySchema.safeParse(req.body);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Corpo invalido — ${first ? `${first.path.join('.')}: ${first.message}` : 'campos ausentes'}`,
          );
        }

        const current = requireKey(deps.keyRepository, req.params.id);

        // Reduzir o teto abaixo do que ja foi reivindicado deixaria a chave num
        // estado que ela mesma nao poderia ter alcancado, e sem nenhum efeito
        // pratico: os vinculos existentes continuam valendo de qualquer forma.
        const nextMax = parsed.data.maxAccounts;
        if (nextMax !== undefined && nextMax < current.accounts.length) {
          throw new ValidationError(
            `Esta chave ja tem ${current.accounts.length} conta(s) vinculada(s); ` +
              'remova um vinculo antes de reduzir o limite.',
          );
        }

        const updated = deps.keyRepository.update(current.id, parsed.data);
        res.json(toKeyResponse(updated as TeamKeyRecord));
      }),
    ),
  );

  /**
   * Troca a chave crua mantendo os vinculos.
   *
   * A chave antiga para de valer na requisicao seguinte. Serve tanto para chave
   * perdida quanto para chave vazada, e tambem reativa uma chave revogada sem
   * refazer o vinculo com a conta na mao.
   */
  router.post(
    '/admin/v1/keys/:id/regenerate',
    protect(
      wrap((req, res) => {
        const current = requireKey(deps.keyRepository, req.params.id);
        const regenerated = deps.keyRepository.regenerate(current.id);
        logger.debug({ keyId: current.id }, 'chave de time regerada');
        res.json(toKeyResponse(regenerated as TeamKeyRecord));
      }),
    ),
  );

  /**
   * Revoga a chave. **Nao apaga dados** — para isso existe `DELETE /v1/member`.
   *
   * Sao decisoes diferentes: tirar o acesso de uma maquina e apagar o historico
   * que ela ja enviou. Juntar as duas numa rota so faria a primeira, muito mais
   * comum, carregar o risco da segunda.
   */
  router.delete(
    '/admin/v1/keys/:id',
    protect(
      wrap((req, res) => {
        const current = requireKey(deps.keyRepository, req.params.id);
        const revoked = deps.keyRepository.revoke(current.id, deps.now());
        logger.debug({ keyId: current.id }, 'chave de time revogada');
        res.json(toKeyResponse(revoked as TeamKeyRecord));
      }),
    ),
  );

  /**
   * Desfaz um vinculo. E o conserto de quem reivindicou a conta errada.
   *
   * O vinculo nasce no primeiro ingest, entao uma chave entregue a pessoa errada
   * se amarra a conta errada — e nenhuma outra chave consegue adotar aquela
   * conta enquanto isso durar, por causa do indice unico.
   */
  router.delete(
    '/admin/v1/keys/:id/accounts/:accountKey',
    protect(
      wrap((req, res) => {
        const current = requireKey(deps.keyRepository, req.params.id);
        const removed = deps.keyRepository.unclaimAccount(current.id, req.params.accountKey ?? '');
        if (!removed) {
          throw new NotFoundError('Vinculo nao encontrado para esta chave e conta.');
        }
        res.json(toKeyResponse(deps.keyRepository.findById(current.id) as TeamKeyRecord));
      }),
    ),
  );

  return router;
}

/**
 * Resolve o `:id` da rota ou responde 404.
 *
 * Aceita `undefined` porque o tipo de `req.params` admite ausencia; um id que
 * nao chegou e um id que nao existe tem o mesmo desfecho para quem chama.
 */
function requireKey(repository: TeamKeyRepository, id: string | undefined): TeamKeyRecord {
  const found = id === undefined ? null : repository.findById(id);
  if (found === null) {
    throw new NotFoundError('Chave de time nao encontrada.');
  }
  return found;
}

/**
 * A chave crua vai no corpo de proposito.
 *
 * A alternativa — mostrar so na criacao — obrigaria o admin a guardar a chave
 * fora do sistema ou a regerar a cada consulta, e o painel existe justamente
 * para ser a lista de "quem tem qual chave". O preco esta registrado: quem tiver
 * o banco **e** o `TEAM_KEY_SECRET` le todas as chaves.
 */
function toKeyResponse(record: TeamKeyRecord) {
  return {
    id: record.id,
    label: record.label,
    key: record.key,
    keyPrefix: record.keyPrefix,
    maxAccounts: record.maxAccounts,
    accounts: record.accounts,
    createdAt: record.createdAt,
    revokedAt: record.revokedAt,
    lastUsedAt: record.lastUsedAt,
  };
}
