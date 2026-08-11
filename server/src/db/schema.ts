/**
 * Schema do indice de time.
 *
 * Espelha as tabelas `cli_sessions` / `cli_turns` do indice local do desktop, com
 * `account_key` na frente de toda chave: um servidor guarda varias contas
 * Anthropic e nenhuma consulta pode cruzar os dados de duas.
 *
 * `PRIMARY KEY (account_key, session_id, message_id)` em `team_turns` e o que
 * torna o ingest idempotente: o cliente pode reenviar o mesmo lote (retry, push
 * duplicado, watermark perdido) que o `INSERT OR IGNORE` descarta os repetidos.
 * E a mesma chave de deduplicacao que o indexador local ja usa.
 */
export const SCHEMA = `
CREATE TABLE IF NOT EXISTS team_members (
  account_key       TEXT    NOT NULL,
  device_id         TEXT    NOT NULL,
  alias             TEXT    NOT NULL,
  host_name         TEXT,
  organization_uuid TEXT,
  organization_name TEXT,
  last_seen_at      INTEGER NOT NULL,
  PRIMARY KEY (account_key, device_id)
);

CREATE TABLE IF NOT EXISTS team_sessions (
  account_key         TEXT    NOT NULL,
  session_id          TEXT    NOT NULL,
  device_id           TEXT    NOT NULL,
  cwd                 TEXT,
  git_branch          TEXT,
  first_ts            INTEGER NOT NULL,
  last_ts             INTEGER NOT NULL,
  live_context_tokens INTEGER NOT NULL DEFAULT 0,
  live_context_model  TEXT,
  PRIMARY KEY (account_key, session_id)
);

CREATE TABLE IF NOT EXISTS team_turns (
  account_key           TEXT    NOT NULL,
  session_id            TEXT    NOT NULL,
  message_id            TEXT    NOT NULL,
  ts                    INTEGER NOT NULL,
  model                 TEXT,
  is_sidechain          INTEGER NOT NULL DEFAULT 0,
  input_tokens          INTEGER NOT NULL DEFAULT 0,
  output_tokens         INTEGER NOT NULL DEFAULT 0,
  cache_read_tokens     INTEGER NOT NULL DEFAULT 0,
  cache_write_5m_tokens INTEGER NOT NULL DEFAULT 0,
  cache_write_1h_tokens INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (account_key, session_id, message_id)
);

-- Sem este indice o filtro temporal varre a tabela inteira de turnos.
CREATE INDEX IF NOT EXISTS idx_team_turns_window ON team_turns(account_key, ts DESC);
CREATE INDEX IF NOT EXISTS idx_team_sessions_device ON team_sessions(account_key, device_id);
`;
