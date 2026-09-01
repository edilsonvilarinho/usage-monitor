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
 *
 * `team_keys` e `team_key_accounts` sao o que transforma esse escopo em
 * isolamento de verdade: sem elas o `account_key` apenas evita que consultas se
 * cruzem, mas qualquer portador da chave unica de ambiente le qualquer conta.
 */
export const SCHEMA = `
CREATE TABLE IF NOT EXISTS team_accounts (
  account_key        TEXT    PRIMARY KEY,
  account_email      TEXT    NOT NULL,
  email_updated_at   INTEGER NOT NULL
);

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

-- Estado interno do proprio servidor. Hoje guarda so o salt de derivacao da
-- cifra das chaves, que precisa sobreviver a reinicio sem virar variavel de
-- ambiente: quem opera ja tem segredo demais para gerenciar.
CREATE TABLE IF NOT EXISTS server_meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

-- Chaves de time emitidas pelo admin.
--
-- key_hash e o que a autenticacao consulta: a chave crua e aleatoria de 32
-- bytes, entao um SHA-256 indexado basta e nao ha prefixo a descobrir
-- incrementalmente. key_cipher/key_iv/key_tag existem apenas para o modal do
-- admin reler a chave depois de criada; a autenticacao nunca decifra nada.
--
-- label e texto livre de quem administra (normalmente o e-mail da pessoa). Ele
-- nao autentica nada, mas passou a AUTORIZAR: quando traz e-mail, so a conta que
-- reporta um dos e-mails listados ali pode usar a chave. Rotulo sem e-mail nao
-- declara relacao nenhuma e a verificacao fica desligada.
CREATE TABLE IF NOT EXISTS team_keys (
  id           TEXT    PRIMARY KEY,
  key_hash     TEXT    NOT NULL UNIQUE,
  key_prefix   TEXT    NOT NULL,
  key_cipher   TEXT    NOT NULL,
  key_iv       TEXT    NOT NULL,
  key_tag      TEXT    NOT NULL,
  label        TEXT    NOT NULL,
  max_accounts INTEGER NOT NULL DEFAULT 1,
  created_at   INTEGER NOT NULL,
  revoked_at   INTEGER,
  last_used_at INTEGER
);

CREATE TABLE IF NOT EXISTS team_key_accounts (
  key_id      TEXT    NOT NULL REFERENCES team_keys(id) ON DELETE CASCADE,
  account_key TEXT    NOT NULL,
  claimed_at  INTEGER NOT NULL,
  PRIMARY KEY (key_id, account_key)
);

-- Invariante do isolamento: uma conta pertence a no maximo uma chave. E tambem
-- a rede contra corrida no vinculo por primeiro uso — em dois ingests
-- simultaneos um grava e o outro falha na constraint em vez de duplicar.
CREATE UNIQUE INDEX IF NOT EXISTS idx_team_key_accounts_account
  ON team_key_accounts(account_key);

-- Contas que o admin declarou fora do time.
--
-- Sem esta tabela, apagar uma conta era inutil: a maquina que ainda participa
-- dela reivindica de novo na batida de presenca seguinte, e o historico volta a
-- crescer. E o unico lugar onde "esta conta nao faz parte do time" fica escrito.
--
-- account_email e um RETRATO do momento do bloqueio, nao uma juncao com
-- team_accounts: apagar a conta apaga a linha de la, e a lista precisa continuar
-- legivel depois disso — UUID cru nao identifica ninguem para quem vai decidir
-- desbloquear.
--
-- purgeExpiredData nao toca aqui: bloqueio nao envelhece.
CREATE TABLE IF NOT EXISTS team_blocked_accounts (
  account_key   TEXT    PRIMARY KEY,
  account_email TEXT,
  reason        TEXT,
  blocked_at    INTEGER NOT NULL
);
`;
