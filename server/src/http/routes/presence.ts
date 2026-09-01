import { Router } from 'express';
import type { Config } from '../../config.js';
import { ValidationError } from '../../domain/errors.js';
import { logger } from '../../logger.js';
import type { TeamKeyRepository } from '../../repositories/teamKeyRepository.js';
import type { TeamRepository } from '../../repositories/teamRepository.js';
import { readTeamAccess, requireTeamAccess, type AccessDeps } from '../access.js';
import { presenceBodySchema } from '../dto.js';
import { wrap } from '../errorHandler.js';

export interface PresenceRouterDeps {
  config: Config;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
  now: () => number;
}

/**
 * `POST /v1/presence` — heartbeat de "o app esta aberto nesta maquina".
 *
 * Vive fora de `team.ts` de proposito. Aquele arquivo e a familia de leitura:
 * conta na **query**, `x-admin-token` aceito, sem `claim`. A presenca e o oposto
 * nos tres eixos — conta no **corpo**, admin recusado, `allowClaim: true` — e
 * misturar as duas formas no mesmo arquivo faria a proxima rota copiar o vizinho
 * errado. E o mesmo motivo pelo qual `ingest.ts` ja e separado.
 *
 * Nao existe rota de leitura correspondente: `GET /v1/team` e
 * `GET /admin/v1/overview` ja devolvem `members[].lastSeenAt`.
 */
export function createPresenceRouter(deps: PresenceRouterDeps): Router {
  const router = Router();
  const access: AccessDeps = {
    config: deps.config,
    keyRepository: deps.keyRepository,
    repository: deps.repository,
    now: deps.now,
  };

  router.post(
    '/v1/presence',
    requireTeamAccess(
      access,
      // Mesmo extrator do ingest: o corpo ja foi desserializado pelo
      // `express.json`, a autorizacao le o campo cru e o zod confere abaixo.
      (req) => (req.body as { accountKey?: unknown } | undefined)?.accountKey,
      wrap((req, res) => {
        const parsed = presenceBodySchema.safeParse(req.body);
        if (!parsed.success) {
          throw new ValidationError(formatZodIssues(parsed.error.issues));
        }

        const now = deps.now();
        deps.repository.touchMember(
          parsed.data.accountKey,
          parsed.data.member,
          now,
          parsed.data.accountEmail,
        );

        const granted = readTeamAccess(res);
        if (granted?.keyId != null) {
          deps.keyRepository.touch(granted.keyId, now);
        }

        // O relogio do servidor vai na resposta porque a maquina que bate e a
        // mesma que le a tela de presenca: sem ele o cliente compararia o
        // `last_seen_at` do servidor com o proprio relogio, e um desvio de
        // minutos deixaria o time inteiro "online" para sempre.
        logger.debug(
          { accountKey: parsed.data.accountKey, deviceId: parsed.data.member.deviceId },
          'presenca registrada',
        );

        res.json({ lastSeenAt: now });
      }),
      // Obrigatorio, nao opcional. Dois efeitos, os dois desejados:
      //  - recusa o `x-admin-token` com 401. Presenca e declaracao de identidade
      //    em nome de um `deviceId`; um admin capaz de marcar presenca de
      //    terceiros criaria membro fantasma e faria a tela mentir. Consequencia
      //    a nao "consertar": instalacao em modo admin puro nao bate presenca.
      //  - vincula a conta a chave na primeira batida, como o ingest. O
      //    `accountKey` e auto-declarado no corpo nos dois casos, entao a forca
      //    da prova e a mesma; sem isso o fallback por 404 (que e ingest, e
      //    portanto vincula) teria semantica diferente do caminho primario.
      {
        allowClaim: true,
        // A batida carrega o mesmo `accountEmail` do ingest, e e ela que chega
        // primeiro numa maquina sem turno pendente: sem o extrator aqui, o
        // portao so veria o e-mail no envio seguinte, que pode nunca vir.
        extractAccountEmail: (req) =>
          (req.body as { accountEmail?: unknown } | undefined)?.accountEmail,
      },
    ),
  );

  return router;
}

function formatZodIssues(issues: Array<{ path: Array<string | number>; message: string }>): string {
  const formatted = issues
    .slice(0, 5)
    .map((issue) => `${issue.path.join('.') || '(raiz)'}: ${issue.message}`)
    .join('; ');
  return `Corpo de presenca invalido — ${formatted}`;
}
