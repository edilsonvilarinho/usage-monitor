import 'dotenv/config';
import { buildApp } from './app.js';
import { loadConfigFromEnv } from './config.js';
import { purgeExpiredData } from './db/retention.js';
import { logger } from './logger.js';

/** A cada 6h. A retencao nao precisa ser precisa, so nao pode nunca rodar. */
const PURGE_INTERVAL_MILLIS = 6 * 60 * 60 * 1_000;

const config = loadConfigFromEnv();
const { app, db } = buildApp(config);

function runPurge(): void {
  try {
    const report = purgeExpiredData(db, config.retentionDays, Date.now());
    if (report.deletedTurns > 0 || report.deletedSessions > 0 || report.deletedMembers > 0) {
      logger.info({ ...report, retentionDays: config.retentionDays }, 'retencao aplicada');
    }
  } catch (error) {
    // Falha de limpeza nao pode derrubar o servidor: os dados continuam servindo.
    logger.error({ err: error }, 'falha ao aplicar retencao');
  }
}

runPurge();
const purgeTimer = setInterval(runPurge, PURGE_INTERVAL_MILLIS);
purgeTimer.unref();

const server = app.listen(config.port, () => {
  logger.info(
    { port: config.port, dataDir: config.dataDir, retentionDays: config.retentionDays },
    'servidor de time no ar',
  );
});

function shutdown(signal: string): void {
  logger.info({ signal }, 'encerrando');
  clearInterval(purgeTimer);
  server.close(() => {
    db.close();
    process.exit(0);
  });
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
