/**
 * Configuracao do servidor, lida de variaveis de ambiente.
 *
 * A validacao e fail-fast no boot: um segredo fraco ou um numero invalido
 * derruba o processo antes de aceitar a primeira requisicao, em vez de falhar
 * em runtime no meio de um ingest.
 */
export interface Config {
  port: number;
  dataDir: string;
  teamApiKey: string;
  retentionDays: number;
  maxTurnsPerRequest: number;
  trustProxyHops: number;
}

/** Tamanho minimo da chave de time. Abaixo disso o boot falha. */
export const MIN_TEAM_API_KEY_LENGTH = 32;

const DEFAULT_PORT = 3000;
const DEFAULT_RETENTION_DAYS = 45;
const DEFAULT_MAX_TURNS_PER_REQUEST = 5000;

export function loadConfigFromEnv(env: NodeJS.ProcessEnv = process.env): Config {
  return validateConfig({
    port: parseIntegerOrDefault(env.PORT, DEFAULT_PORT),
    dataDir: env.DATA_DIR ?? './data',
    teamApiKey: env.TEAM_API_KEY ?? '',
    retentionDays: parseIntegerOrDefault(env.TEAM_RETENTION_DAYS, DEFAULT_RETENTION_DAYS),
    maxTurnsPerRequest: parseIntegerOrDefault(
      env.TEAM_MAX_TURNS_PER_REQUEST,
      DEFAULT_MAX_TURNS_PER_REQUEST,
    ),
    trustProxyHops: parseIntegerOrDefault(env.TRUST_PROXY_HOPS, 0),
  });
}

export function validateConfig(config: Config): Config {
  if (config.teamApiKey.trim().length < MIN_TEAM_API_KEY_LENGTH) {
    throw new Error(
      `TEAM_API_KEY obrigatoria e com pelo menos ${MIN_TEAM_API_KEY_LENGTH} caracteres. ` +
        'Gere uma com: node -e "console.log(require(\'crypto\').randomBytes(32).toString(\'base64url\'))"',
    );
  }

  if (!Number.isInteger(config.port) || config.port <= 0 || config.port > 65535) {
    throw new Error('PORT deve ser um inteiro entre 1 e 65535.');
  }

  if (!config.dataDir.trim()) {
    throw new Error('DATA_DIR obrigatoria.');
  }

  if (!Number.isInteger(config.retentionDays) || config.retentionDays < 1) {
    throw new Error('TEAM_RETENTION_DAYS deve ser um inteiro maior ou igual a 1.');
  }

  if (!Number.isInteger(config.maxTurnsPerRequest) || config.maxTurnsPerRequest < 1) {
    throw new Error('TEAM_MAX_TURNS_PER_REQUEST deve ser um inteiro maior ou igual a 1.');
  }

  if (!Number.isInteger(config.trustProxyHops) || config.trustProxyHops < 0) {
    throw new Error('TRUST_PROXY_HOPS deve ser um inteiro maior ou igual a 0.');
  }

  return config;
}

function parseIntegerOrDefault(raw: string | undefined, fallback: number): number {
  if (raw === undefined || raw.trim() === '') {
    return fallback;
  }
  // Number() em vez de parseInt: "3000abc" tem de falhar na validacao, nao virar 3000.
  return Number(raw);
}
