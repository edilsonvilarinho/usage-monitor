import express, { type Express } from 'express';
import type { Config } from './config.js';
import { openDatabase, type Db } from './db/openDatabase.js';
import { TeamRepository } from './repositories/teamRepository.js';
import { errorHandler } from './http/errorHandler.js';
import { createHealthRouter } from './http/routes/health.js';
import { createIngestRouter } from './http/routes/ingest.js';
import { createTeamRouter } from './http/routes/team.js';

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
}

/** Tamanho maximo do corpo. Um lote de 5000 turnos fica bem abaixo disso. */
const JSON_BODY_LIMIT = '8mb';

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

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxyHops);
  app.use(express.json({ limit: JSON_BODY_LIMIT }));

  app.use('/api', createHealthRouter(db));
  app.use('/api', createIngestRouter({ config, repository, now }));
  app.use('/api', createTeamRouter({ config, repository }));

  app.use((_req, res) => {
    res.status(404).json({ error: 'Rota nao encontrada.', code: 'not_found' });
  });

  app.use(errorHandler);

  return { app, db, repository };
}
