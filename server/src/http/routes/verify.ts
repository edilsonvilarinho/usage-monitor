import { Router } from 'express';
import type { Config } from '../../config.js';
import { ForbiddenError, UnauthorizedError, ValidationError } from '../../domain/errors.js';
import { hashKey, type TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import { ADMIN_TOKEN_HEADER } from '../access.js';
import { isValidTeamKey, TEAM_KEY_HEADER } from '../auth.js';
import { verifyQuerySchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface VerifyRouterDeps {
  config: Config;
  keyRepository: TeamKeyRepository;
}

/**
 * Responde se a chave apresentada cobre — ou pode cobrir — uma conta.
 *
 * Existe porque o "Testar conexao" do app precisa de um alvo real. Ate aqui ele
 * consultava uma conta inventada, o que funcionava enquanto qualquer chave lia
 * qualquer conta; com autorizacao por conta aquilo passaria a responder 403 e o
 * botao mentiria sobre uma configuracao correta.
 *
 * **Nao reivindica nada.** O vinculo continua nascendo so no ingest: uma rota de
 * leitura que amarrasse conta permitiria adotar contas alheias por varredura de
 * UUID, sem nunca provar que aquela maquina usa a conta.
 */
export function createVerifyRouter(deps: VerifyRouterDeps): Router {
  const router = Router();

  router.get(
    '/v1/verify',
    wrap((req, res) => {
      const parsed = verifyQuerySchema.safeParse(req.query);
      if (!parsed.success) {
        const first = parsed.error.issues[0];
        throw new ValidationError(
          `Query invalida — ${first ? `${first.path.join('.')}: ${first.message}` : 'parametros ausentes'}`,
        );
      }

      const accountKey = parsed.data.accountKey;
      const adminToken = deps.config.adminToken;
      const presentedAdmin = req.header(ADMIN_TOKEN_HEADER);
      if (adminToken !== null && presentedAdmin !== undefined && isValidTeamKey(presentedAdmin, adminToken)) {
        res.json({
          authorized: true,
          claimed: true,
          label: null,
          maxAccounts: 0,
          claimedAccounts: 0,
        });
        return;
      }

      const presentedKey = req.header(TEAM_KEY_HEADER);
      if (presentedKey === undefined || presentedKey === '') {
        throw new UnauthorizedError();
      }

      // A chave legada continua respondendo "autorizada" enquanto o modo aberto
      // valer: ela de fato le tudo, e informar o contrario faria o app avisar de
      // um problema que nao existe naquele deploy.
      if (
        deps.config.teamApiKey !== null &&
        deps.config.legacyKeyMode === 'open' &&
        isValidTeamKey(presentedKey, deps.config.teamApiKey)
      ) {
        res.json({
          authorized: true,
          claimed: true,
          label: null,
          maxAccounts: 0,
          claimedAccounts: 0,
        });
        return;
      }

      const resolved = deps.keyRepository.resolve(hashKey(presentedKey));
      if (resolved === null) {
        throw new UnauthorizedError();
      }

      const claimed = resolved.accounts.includes(accountKey);
      if (!claimed) {
        // Dona diferente e recusa imediata: dizer "autorizada" aqui e falhar no
        // ingest seguinte transformaria um erro de configuracao numa sincronia
        // silenciosamente parada.
        const owner = deps.keyRepository.ownerOf(accountKey);
        if (owner !== null) {
          throw new ForbiddenError('Esta conta ja pertence a outra chave de time.');
        }
        if (resolved.accounts.length >= resolved.maxAccounts) {
          throw new ForbiddenError(
            'Esta chave ja atingiu o limite de contas. Peca ao administrador para aumentar o limite ou emitir outra chave.',
          );
        }
      }

      const record = deps.keyRepository.findById(resolved.id);
      res.json({
        authorized: true,
        claimed,
        label: record?.label ?? null,
        maxAccounts: resolved.maxAccounts,
        claimedAccounts: resolved.accounts.length,
      });
    }),
  );

  return router;
}
