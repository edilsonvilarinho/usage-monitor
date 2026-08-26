import express, { type Express } from 'express';
import type { Config } from './config.js';
import { KeyCipher, loadOrCreateCipherSalt } from './crypto/keyCipher.js';
import { openDatabase, type Db } from './db/openDatabase.js';
import { TeamKeyRepository } from './repositories/teamKeyRepository.js';
import { TeamRepository } from './repositories/teamRepository.js';
import { errorHandler } from './http/errorHandler.js';
import { createAdminRouter } from './http/routes/admin.js';
import { createHealthRouter } from './http/routes/health.js';
import { createIngestRouter } from './http/routes/ingest.js';
import { createPresenceRouter } from './http/routes/presence.js';
import { createReportRouter } from './http/routes/report.js';
import { createTeamRouter } from './http/routes/team.js';
import { createVerifyRouter } from './http/routes/verify.js';

export interface BuildOverrides {
  /** Banco ja aberto. Os testes passam um em diretorio temporario. */
  db?: Db;
  /** Relogio injetavel, para o teste controlar `last_seen_at`. */
  now?: () => number;
}

export interface BuiltApp {
  app: Express;
  db: Db;
  repository: TeamRepository;
  keyRepository: TeamKeyRepository;
}

/** Tamanho maximo do corpo. Um lote de 5000 turnos fica bem abaixo disso. */
const JSON_BODY_LIMIT = '8mb';

/**
 * Segredo de derivacao usado quando nao ha administracao ligada.
 *
 * Sem `TEAM_ADMIN_TOKEN` nenhuma chave pode ser criada, entao nada e cifrado e o
 * valor nunca protege nada. Ele existe apenas para o repositorio ter um objeto
 * valido, em vez de espalhar `null` por todo o grafo de dependencias.
 */
const UNUSED_CIPHER_SECRET = 'team-key-cipher-disabled-placeholder-secret';

/**
 * Monta a aplicacao sem nenhum efeito colateral de import.
 *
 * Nada de `app.listen` aqui: quem sobe o socket e o `main.ts`, e o teste usa a
 * `app` direto com supertest. O grafo de dependencias e montado a mao — nao ha
 * container de DI.
 */
export function buildApp(config: Config, overrides: BuildOverrides = {}): BuiltApp {
  const db = overrides.db ?? openDatabase(config.dataDir);
  const now = overrides.now ?? (() => Date.now());
  const repository = new TeamRepository(db);

  const cipher = new KeyCipher(config.keySecret ?? UNUSED_CIPHER_SECRET, loadOrCreateCipherSalt(db));
  const keyRepository = new TeamKeyRepository(db, cipher);

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxyHops);
  app.use(express.json({ limit: JSON_BODY_LIMIT }));

  app.use('/api', createHealthRouter(db));
  app.use('/api', createIngestRouter({ config, repository, keyRepository, now }));
  // Junto do outro caminho de escrita, e incondicional: ao contrario da
  // administracao, a presenca vale para qualquer deploy.
  app.use('/api', createPresenceRouter({ config, repository, keyRepository, now }));
  app.use('/api', createTeamRouter({ config, repository, keyRepository, now }));
  // Incondicional: sem `TEAM_REPORT_TOKEN` as rotas respondem 401 em vez de nao
  // existir. Rota ausente faria "credencial errada" e "variavel nao definida"
  // chegarem ao consumidor como o mesmo 404.
  app.use('/api', createReportRouter({ config }));
  app.use('/api', createVerifyRouter({ config, keyRepository, now }));

  // Sem token de administracao as rotas nem existem: caem na coringa 404 abaixo,
  // e um deploy que nunca pediu administracao nao ganha superficie nova.
  if (config.adminToken !== null) {
    app.use(
      '/api',
      createAdminRouter({
        adminToken: config.adminToken,
        reportToken: config.reportToken,
        repository,
        keyRepository,
        now,
      }),
    );
  }

  app.use((_req, res) => {
    res.status(404).json({ error: 'Rota nao encontrada.', code: 'not_found' });
  });

  app.use(errorHandler);

  return { app, db, repository, keyRepository };
}
