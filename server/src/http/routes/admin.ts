import { Router } from 'express';
import type { KeyLabelMatchMode } from '../../config.js';
import { NotFoundError, ValidationError } from '../../domain/errors.js';
import { isAccountAllowedByLabel } from '../../domain/teamKeyLabel.js';
import { logger } from '../../logger.js';
import type { TeamKeyRepository, TeamKeyRecord } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { requireAdminToken, requireGlobalRead } from '../access.js';
import {
  DEFAULT_GAP_CUTOFF_MS,
  createKeyBodySchema,
  deleteMemberQuerySchema,
  overviewQuerySchema,
  sessionQuerySchema,
  updateKeyBodySchema,
} from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface AdminRouterDeps {
  adminToken: string;
  /** Credencial de leitura global. So a visao agregada a aceita; nenhum `DELETE` a ve. */
  reportToken: string | null;
  /**
   * Modo do portao do rotulo, so para a lista dizer a verdade.
   *
   * Com o portao desligado toda conta vinculada esta autorizada, e marcar
   * divergencia ali anunciaria uma recusa que nao vai acontecer.
   */
  keyLabelMatch: KeyLabelMatchMode;
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
 * nasce sem conta e se amarra a primeira que a usar num ingest.
 *
 * O `label` continua sendo texto livre, mas deixou de ser decoracao: quando ele
 * declara e-mail, aquela e a **relacao do time** daquela chave, e conta de fora
 * dela e recusada (`assertAllowedByLabel`, em `http/access.ts`). Rotulo sem
 * e-mail nao declara relacao nenhuma e o portao fica desligado.
 */
export function createAdminRouter(deps: AdminRouterDeps): Router {
  const router = Router();
  const protect = (handler: Parameters<typeof requireAdminToken>[1]) =>
    requireAdminToken(deps.adminToken, handler);

  /**
   * A chave com o e-mail de cada conta vinculada ao lado.
   *
   * Sem isto a tela do admin listava UUID cru, e a conta intrusa da issue #179
   * era indistinguivel das legitimas — descobrir que `879df04a…` era um gmail
   * pessoal exigia cruzar com outra tela. `accounts` continua sendo a lista de
   * UUIDs: mudar a forma dela quebraria app anterior, e o campo novo e ignorado
   * por quem nao o conhece.
   */
  const describeKey = (record: TeamKeyRecord) => {
    const emails = deps.repository.accountEmails();
    return toKeyResponse(
      record,
      record.accounts.map((accountKey) => {
        const accountEmail = emails.get(accountKey) ?? null;
        return {
          accountKey,
          accountEmail,
          authorized:
            deps.keyLabelMatch === 'off' || isAccountAllowedByLabel(record.label, accountEmail),
        };
      }),
    );
  };

  // Leitura que nao expoe segredo nem apaga nada aceita tambem `x-report-key`.
  // `protect` continua sendo o portao de tudo o mais — inclusive da listagem de
  // chaves, que devolve material de credencial, e de todos os `DELETE`.
  const readOnly = (handler: Parameters<typeof requireAdminToken>[1]) =>
    requireGlobalRead({ adminToken: deps.adminToken, reportToken: deps.reportToken }, handler);

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
   * Compatibilidade com clientes administrativos anteriores.
   *
   * A rota conserva o endereco antigo, mas agora nasce apenas no roteador de
   * administracao e exige `x-admin-token`. Uma chave de time, inclusive a dona
   * da conta, nao pode mais apagar integrantes.
   */
  router.delete(
    '/v1/member',
    protect(
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

  /**
   * Apaga uma sessao e os turnos dela. O integrante e as demais sessoes ficam.
   *
   * A identidade completa vem no caminho para impedir que estado obsoleto da
   * tela apague uma sessao atualmente associada a outra maquina.
   */
  router.delete(
    '/admin/v1/accounts/:accountKey/members/:deviceId/sessions/:sessionId',
    protect(
      wrap((req, res) => {
        const parsed = sessionQuerySchema.safeParse(req.params);
        if (!parsed.success) {
          const first = parsed.error.issues[0];
          throw new ValidationError(
            `Caminho invalido — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
          );
        }

        const report = deps.repository.deleteSession(
          parsed.data.accountKey,
          parsed.data.deviceId,
          parsed.data.sessionId,
        );

        logger.debug(
          {
            accountKey: parsed.data.accountKey,
            deviceId: parsed.data.deviceId,
            sessionId: parsed.data.sessionId,
            ...report,
          },
          'sessao removida',
        );
        res.json(report);
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
    readOnly(
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
          parsed.data.until ?? null,
          deps.keyRepository.accountLabels(),
          parsed.data.gapCutoffMs ?? DEFAULT_GAP_CUTOFF_MS,
        );

        res.json({ accounts });
      }),
    ),
  );

  router.get(
    '/admin/v1/keys',
    protect(
      wrap((_req, res) => {
        res.json({ keys: deps.keyRepository.list().map(describeKey) });
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
        res.status(201).json(describeKey(created));
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
        res.json(describeKey(updated as TeamKeyRecord));
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
        res.json(describeKey(regenerated as TeamKeyRecord));
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
        res.json(describeKey(revoked as TeamKeyRecord));
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
        res.json(describeKey(deps.keyRepository.findById(current.id) as TeamKeyRecord));
      }),
    ),
  );

  /**
   * Apaga uma conta inteira: integrantes, sessoes, turnos e o vinculo com a
   * chave. **Destrutivo e irreversivel.**
   *
   * E o conserto da conta que a empresa deixou de usar. Desvincular sozinho nao
   * resolvia: a visao global e derivada de `team_members` e `team_turns`, entao
   * a conta continuava na tela, agora sem rotulo. E `DELETE /v1/member` exige um
   * `deviceId` por vez, depois de descobrir cada maquina.
   *
   * **A ordem e deliberada: dados primeiro, vinculo depois.** Falhando o segundo
   * passo sobra um vinculo apontando para conta sem dados — inofensivo, e o
   * Desvincular ao lado desfaz. O inverso deixaria a conta orfa e reivindicavel
   * por outra chave com o historico ainda no banco. Nao e uma transacao unica
   * porque as duas tabelas pertencem a repositorios diferentes; compor os dois
   * na rota e o mesmo padrao do `PATCH` aqui em cima.
   *
   * **Impede a conta de voltar**, e e isto que separa esta rota da versao
   * anterior dela. Antes, ingest e presenca reivindicavam a conta sozinhos e uma
   * maquina que ainda participasse dela a recriava na batida seguinte — apagar
   * era gesto sem efeito, com o admin achando que tinha resolvido. Agora a
   * remocao tambem escreve a decisao em `team_blocked_accounts`, de onde so o
   * proprio admin a tira.
   *
   * **A ordem e dados, vinculo, bloqueio.** O bloqueio vai por ultimo porque e o
   * passo mais barato e o unico trivialmente reversivel: falhar nele deixa uma
   * conta apagada e desvinculada, que e exatamente o estado da versao anterior
   * desta rota. Escrever o bloqueio primeiro e falhar depois deixaria uma conta
   * barrada com o historico inteiro no banco.
   */
  router.delete(
    '/admin/v1/accounts/:accountKey',
    protect(
      wrap((req, res) => {
        const accountKey = req.params.accountKey ?? '';
        if (accountKey === '') {
          throw new ValidationError('Informe a conta a remover.');
        }

        // Lido antes do delete: `deleteAccount` apaga a linha de `team_accounts`,
        // e depois dela nao ha mais e-mail nenhum para guardar no retrato.
        const accountEmail = deps.repository.accountEmailOf(accountKey);
        const report = deps.repository.deleteAccount(accountKey);
        const unlinked = deps.keyRepository.unclaimAccountAnywhere(accountKey);
        deps.keyRepository.blockAccount(accountKey, accountEmail, null, deps.now());

        logger.debug({ accountKey, accountEmail, ...report, unlinked }, 'conta removida do time');
        res.json({ ...report, unlinkedKeys: unlinked ? 1 : 0, blocked: true });
      }),
    ),
  );

  /**
   * Contas que o admin declarou fora do time.
   *
   * Sob `protect` e nao `readOnly`: e politica de acesso, nao relatorio de uso.
   * O token de relatorio le consumo; quem pode ver e mexer em quem entra no time
   * e quem administra.
   */
  router.get(
    '/admin/v1/blocked-accounts',
    protect(
      wrap((_req, res) => {
        res.json({ accounts: deps.keyRepository.listBlockedAccounts() });
      }),
    ),
  );

  /**
   * Devolve a conta ao time.
   *
   * **Nao restaura dado nenhum** — o historico foi apagado junto com o bloqueio e
   * o cliente daquela maquina ja marcou os turnos como enviados. O que volta e a
   * possibilidade de reivindicar a conta de novo, e dai em diante ela precisa
   * passar pelo portao do rotulo como qualquer outra.
   */
  router.delete(
    '/admin/v1/blocked-accounts/:accountKey',
    protect(
      wrap((req, res) => {
        const accountKey = req.params.accountKey ?? '';
        if (accountKey === '') {
          throw new ValidationError('Informe a conta a desbloquear.');
        }

        const unblocked = deps.keyRepository.unblockAccount(accountKey);
        if (!unblocked) {
          throw new NotFoundError('Esta conta nao esta na lista de bloqueadas.');
        }

        logger.debug({ accountKey }, 'conta devolvida ao time');
        res.json({ accounts: deps.keyRepository.listBlockedAccounts() });
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
function toKeyResponse(record: TeamKeyRecord, accountDetails: AccountDetail[]) {
  return {
    id: record.id,
    label: record.label,
    key: record.key,
    keyPrefix: record.keyPrefix,
    maxAccounts: record.maxAccounts,
    accounts: record.accounts,
    accountDetails,
    createdAt: record.createdAt,
    revokedAt: record.revokedAt,
    lastUsedAt: record.lastUsedAt,
  };
}

/** Uma conta vinculada, com o que a tela precisa para decidir sobre ela. */
interface AccountDetail {
  accountKey: string;
  accountEmail: string | null;
  authorized: boolean;
}
