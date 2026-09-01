import 'dotenv/config';
import { buildApp } from './app.js';
import { loadConfigFromEnv } from './config.js';
import { auditKeyLabels } from './db/keyLabelAudit.js';
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

/**
 * Diz, no arranque, quem o portao do rotulo vai recusar.
 *
 * O portao vale para vinculo que ja existia, entao um deploy pode cortar quem
 * sincronizava ontem. Sem esta linha o operador descobriria isso pela
 * reclamacao da pessoa, e nao pelo log de quem subiu a versao.
 *
 * Nao derruba o boot e nao conserta nada sozinho: quem decide o destino de cada
 * vinculo e o admin, com o rotulo ou com a remocao.
 */
function reportKeyLabelMismatches(): void {
  if (config.keyLabelMatch === 'off') {
    return;
  }

  try {
    const mismatches = auditKeyLabels(db);
    for (const mismatch of mismatches) {
      logger.warn(mismatch, 'vinculo fora da relacao declarada no rotulo da chave');
    }
    if (mismatches.length > 0) {
      logger.warn(
        { mismatches: mismatches.length },
        'contas que vao parar de sincronizar ate o rotulo ou o vinculo serem corrigidos',
      );
    }
  } catch (error) {
    // Diagnostico nao pode impedir o servidor de subir.
    logger.error({ err: error }, 'falha ao auditar rotulos de chave');
  }
}

reportKeyLabelMismatches();

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
