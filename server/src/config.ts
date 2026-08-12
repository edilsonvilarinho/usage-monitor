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
  /**
   * Chave unica de ambiente, agora **legada**.
   *
   * Ficou opcional quando as chaves passaram a viver no banco. Enquanto
   * [legacyKeyMode] for `open` ela continua lendo qualquer conta, que e
   * exatamente o comportamento anterior — trocar para `off` e o passo que
   * efetiva o isolamento entre times.
   */
  teamApiKey: string | null;
  /** Presente, monta as rotas administrativas e vale como credencial de leitura. */
  adminToken: string | null;
  /** Segredo de derivacao da cifra das chaves. Exigido junto do [adminToken]. */
  keySecret: string | null;
  legacyKeyMode: LegacyKeyMode;
  retentionDays: number;
  maxTurnsPerRequest: number;
  trustProxyHops: number;
}

export type LegacyKeyMode = 'open' | 'off';

/** Tamanho minimo de qualquer segredo aceito. Abaixo disso o boot falha. */
export const MIN_TEAM_API_KEY_LENGTH = 32;

const LEGACY_KEY_MODES: readonly string[] = ['open', 'off'];

const DEFAULT_PORT = 3000;
const DEFAULT_RETENTION_DAYS = 45;
const DEFAULT_MAX_TURNS_PER_REQUEST = 5000;

const GENERATE_HINT =
  'Gere um com: node -e "console.log(require(\'crypto\').randomBytes(32).toString(\'base64url\'))"';

export function loadConfigFromEnv(env: NodeJS.ProcessEnv = process.env): Config {
  return validateConfig({
    port: parseIntegerOrDefault(env.PORT, DEFAULT_PORT),
    dataDir: env.DATA_DIR ?? './data',
    teamApiKey: readOptionalSecret(env.TEAM_API_KEY),
    adminToken: readOptionalSecret(env.TEAM_ADMIN_TOKEN),
    keySecret: readOptionalSecret(env.TEAM_KEY_SECRET),
    legacyKeyMode: parseLegacyKeyMode(env.TEAM_LEGACY_KEY_MODE),
    retentionDays: parseIntegerOrDefault(env.TEAM_RETENTION_DAYS, DEFAULT_RETENTION_DAYS),
    maxTurnsPerRequest: parseIntegerOrDefault(
      env.TEAM_MAX_TURNS_PER_REQUEST,
      DEFAULT_MAX_TURNS_PER_REQUEST,
    ),
    trustProxyHops: parseIntegerOrDefault(env.TRUST_PROXY_HOPS, 0),
  });
}

export function validateConfig(config: Config): Config {
  // Sem nenhum dos dois o servidor nao tem como autenticar cliente nem como
  // emitir a primeira chave: ele subiria inutil e so daria 401.
  if (config.teamApiKey === null && config.adminToken === null) {
    throw new Error(
      'Defina TEAM_ADMIN_TOKEN (recomendado) ou TEAM_API_KEY (legado). ' + GENERATE_HINT,
    );
  }

  requireStrongSecret(config.teamApiKey, 'TEAM_API_KEY');
  requireStrongSecret(config.adminToken, 'TEAM_ADMIN_TOKEN');
  requireStrongSecret(config.keySecret, 'TEAM_KEY_SECRET');

  // A chave emitida e guardada cifrada para o admin poder rele-la depois. Sem o
  // segredo de derivacao nao ha como criar chave nenhuma, entao as rotas
  // administrativas subiriam so para responder erro.
  if (config.adminToken !== null && config.keySecret === null) {
    throw new Error('TEAM_KEY_SECRET obrigatoria quando TEAM_ADMIN_TOKEN esta definida. ' + GENERATE_HINT);
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

function requireStrongSecret(value: string | null, name: string): void {
  if (value === null) {
    return;
  }
  if (value.trim().length < MIN_TEAM_API_KEY_LENGTH) {
    throw new Error(`${name} precisa de pelo menos ${MIN_TEAM_API_KEY_LENGTH} caracteres. ` + GENERATE_HINT);
  }
}

/** Vazio e ausente sao o mesmo caso: um `.env` com a linha em branco nao configura nada. */
function readOptionalSecret(raw: string | undefined): string | null {
  if (raw === undefined || raw.trim() === '') {
    return null;
  }
  return raw;
}

function parseLegacyKeyMode(raw: string | undefined): LegacyKeyMode {
  if (raw === undefined || raw.trim() === '') {
    return 'open';
  }
  const normalized = raw.trim().toLowerCase();
  if (!LEGACY_KEY_MODES.includes(normalized)) {
    throw new Error(`TEAM_LEGACY_KEY_MODE aceita apenas ${LEGACY_KEY_MODES.join(' ou ')}.`);
  }
  return normalized as LegacyKeyMode;
}

function parseIntegerOrDefault(raw: string | undefined, fallback: number): number {
  if (raw === undefined || raw.trim() === '') {
    return fallback;
  }
  // Number() em vez de parseInt: "3000abc" tem de falhar na validacao, nao virar 3000.
  return Number(raw);
}
