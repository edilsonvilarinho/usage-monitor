import pino from 'pino';

/**
 * Logger unico do processo.
 *
 * `LOG_LEVEL` controla o nivel; nos testes o default e `silent` para nao poluir
 * a saida do vitest.
 */
export const logger = pino({
  level: process.env.LOG_LEVEL ?? (process.env.NODE_ENV === 'test' ? 'silent' : 'info'),
});

export type Logger = typeof logger;
