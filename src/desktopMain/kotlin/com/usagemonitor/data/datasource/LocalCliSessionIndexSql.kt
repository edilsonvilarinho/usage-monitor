package com.usagemonitor.data.datasource

// SQL e constantes do índice de sessões do Claude CLI. Moravam no companion de
// `LocalCliSessionDataSource` e saíram pelo limite de 800 linhas (#303); só a
// fonte de dados os usa.

internal const val TRANSCRIPT_EXTENSION = ".jsonl"

internal const val ASSISTANT_LINE_TYPE = "assistant"

internal const val USER_LINE_TYPE = "user"

internal const val SYSTEM_LINE_TYPE = "system"

/**
 * Marcador de fim de turno escrito pelo próprio CLI — não por hook do
 * usuário: 66 dos 181 transcripts que o trazem não têm `stop_hook_summary`
 * nenhum. É ele que separa sessão encerrada de sessão sem resposta.
 */
internal const val TURN_END_SUBTYPE = "turn_duration"

/** Pasta dos transcripts de subagente, dentro da pasta da sessão. */
internal const val SUBAGENTS_DIRECTORY_NAME = "subagents"

internal const val USAGE_MARKER = "\"usage\""

internal const val READ_BUFFER_BYTES = 64 * 1024

internal val NEW_LINE_BYTE = '\n'.code.toByte()

internal val CREATE_FILES_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS cli_session_files (
      path TEXT PRIMARY KEY,
      profile_id TEXT,
      last_modified INTEGER NOT NULL,
      size_bytes INTEGER NOT NULL,
      last_offset INTEGER NOT NULL DEFAULT 0
    );
"""

internal val CREATE_SESSIONS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS cli_sessions (
      session_id TEXT PRIMARY KEY,
      file_path TEXT NOT NULL,
      profile_id TEXT,
      cwd TEXT,
      git_branch TEXT,
      first_ts INTEGER NOT NULL DEFAULT 0,
      last_ts INTEGER NOT NULL DEFAULT 0,
      primary_model TEXT,
      turn_count INTEGER NOT NULL DEFAULT 0,
      input_tokens INTEGER NOT NULL DEFAULT 0,
      output_tokens INTEGER NOT NULL DEFAULT 0,
      cache_read_tokens INTEGER NOT NULL DEFAULT 0,
      cache_write_5m_tokens INTEGER NOT NULL DEFAULT 0,
      cache_write_1h_tokens INTEGER NOT NULL DEFAULT 0,
      cost_micros INTEGER NOT NULL DEFAULT 0,
      unpriced_turns INTEGER NOT NULL DEFAULT 0,
      host_name TEXT,
      -- Resquício do recurso de ocultar sessões, removido da aplicação.
      -- A coluna fica: derrubá-la em SQLite exige recriar a tabela e os
      -- índices legados a referenciam.
      hidden INTEGER NOT NULL DEFAULT 0
    );
"""

internal val CREATE_TURNS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS cli_turns (
      session_id TEXT NOT NULL,
      seq INTEGER NOT NULL,
      message_id TEXT NOT NULL,
      ts INTEGER NOT NULL,
      model TEXT,
      is_sidechain INTEGER NOT NULL DEFAULT 0,
      input_tokens INTEGER NOT NULL DEFAULT 0,
      output_tokens INTEGER NOT NULL DEFAULT 0,
      cache_read_tokens INTEGER NOT NULL DEFAULT 0,
      cache_write_5m_tokens INTEGER NOT NULL DEFAULT 0,
      cache_write_1h_tokens INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (session_id, message_id)
    );
"""

/**
 * Versão do índice. Subir este número força uma releitura completa dos
 * transcripts na próxima abertura.
 *
 * 1 = tabela `cli_turn_tools`, que nasceu vazia para tudo que já estava
 *     indexado. Nunca chegou a disparar: o contador era o `PRAGMA
 *     user_version`, que o histórico de uso também escreve neste mesmo
 *     arquivo, e o reset de então não forçava releitura nenhuma.
 * 2 = primeira versão com contador próprio (`cli_index_meta`) e com um
 *     reset que a varredura respeita. É a passada que finalmente preenche
 *     `cli_turn_tools` do histórico já indexado.
 */
internal const val INDEX_SCHEMA_VERSION = 2

/**
 * Metadados do índice CLI, hoje só a versão do schema.
 *
 * Existe porque o `PRAGMA user_version` é um valor por arquivo e o
 * histórico de uso mora no mesmo `.db`.
 */
internal val CREATE_INDEX_META_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS cli_index_meta (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
"""

internal const val INDEX_SCHEMA_VERSION_KEY = "schema_version"

internal val SELECT_INDEX_SCHEMA_VERSION_SQL = """
    SELECT value FROM cli_index_meta WHERE key = '$INDEX_SCHEMA_VERSION_KEY';
"""

internal val UPSERT_INDEX_SCHEMA_VERSION_SQL = """
    INSERT INTO cli_index_meta (key, value)
    VALUES ('$INDEX_SCHEMA_VERSION_KEY', ?)
    ON CONFLICT(key) DO UPDATE SET value = excluded.value;
"""

/**
 * Ferramentas invocadas em cada turno.
 *
 * Guarda **só o nome e a contagem** — nunca o `input` da ferramenta nem
 * o texto da resposta, na mesma regra que o envio para o time segue.
 */
internal val CREATE_TURN_TOOLS_TABLE_SQL = """
    CREATE TABLE IF NOT EXISTS cli_turn_tools (
      session_id TEXT NOT NULL,
      message_id TEXT NOT NULL,
      tool_name TEXT NOT NULL,
      call_count INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (session_id, message_id, tool_name)
    );
"""

/** Sem ele o recorte temporal varre a tabela inteira de ferramentas. */
internal val CREATE_TURN_TOOLS_INDEX_SQL = """
    CREATE INDEX IF NOT EXISTS idx_cli_turn_tools_session
    ON cli_turn_tools(session_id, message_id);
"""

/**
 * Esquece os arquivos já lidos para forçar a releitura.
 *
 * Apagar a linha, e não zerar o `last_offset`, é o que faz diferença:
 * `syncIndex` pula por tamanho + data de modificação antes de consultar o
 * offset, então um arquivo inalterado com offset zerado continuava sendo
 * pulado. Sem a linha ele é tratado como novo.
 *
 * Não apaga sessões nem turnos: eles voltam pelos mesmos `INSERT OR
 * IGNORE`/`INSERT OR REPLACE` da indexação normal e não se duplicam.
 */
internal val RESET_INDEXED_FILES_SQL = """
    DELETE FROM cli_session_files;
"""

/**
 * Reatribui a conta pelo caminho do arquivo, para as sessões que ficaram
 * com ela nula. Só toca em linha órfã.
 */
internal val BACKFILL_SESSION_PROFILE_SQL = """
    UPDATE cli_sessions
    SET profile_id = (
      SELECT f.profile_id FROM cli_session_files f WHERE f.path = cli_sessions.file_path
    )
    WHERE profile_id IS NULL
      AND EXISTS (
        SELECT 1 FROM cli_session_files f
        WHERE f.path = cli_sessions.file_path AND f.profile_id IS NOT NULL
      );
"""

internal val STAMP_SESSION_PROFILE_SQL = """
    UPDATE cli_sessions
    SET profile_id = ?
    WHERE file_path = ? AND (profile_id IS NULL OR profile_id <> ?);
"""

internal val INSERT_TURN_TOOL_SQL = """
    INSERT OR REPLACE INTO cli_turn_tools
      (session_id, message_id, tool_name, call_count)
    VALUES (?, ?, ?, ?);
"""

internal val DELETE_TURN_TOOLS_BY_FILE_SQL = """
    DELETE FROM cli_turn_tools
    WHERE session_id IN (SELECT session_id FROM cli_sessions WHERE file_path = ?);
"""

internal val CREATE_SESSIONS_INDEX_SQL = """
    CREATE INDEX IF NOT EXISTS idx_cli_sessions_last_ts
    ON cli_sessions(hidden, last_ts DESC);
"""

internal val CREATE_SESSIONS_PROFILE_INDEX_SQL = """
    CREATE INDEX IF NOT EXISTS idx_cli_sessions_profile
    ON cli_sessions(profile_id, hidden, last_ts DESC);
"""

internal val CREATE_TURNS_INDEX_SQL = """
    CREATE INDEX IF NOT EXISTS idx_cli_turns_session_seq
    ON cli_turns(session_id, seq);
"""

/** Sem ele o recorte temporal varre a tabela inteira de turnos. */
internal val CREATE_TURNS_TS_INDEX_SQL = """
    CREATE INDEX IF NOT EXISTS idx_cli_turns_ts
    ON cli_turns(ts DESC);
"""

internal val SELECT_FILES_SQL = """
    SELECT path, profile_id, last_modified, size_bytes, last_offset FROM cli_session_files;
"""

internal val UPSERT_FILE_SQL = """
    INSERT INTO cli_session_files (path, profile_id, last_modified, size_bytes, last_offset)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(path) DO UPDATE SET
      profile_id = excluded.profile_id,
      last_modified = excluded.last_modified,
      size_bytes = excluded.size_bytes,
      last_offset = excluded.last_offset;
"""

internal val INSERT_TURN_SQL = """
    INSERT OR IGNORE INTO cli_turns (
      session_id, seq, message_id, ts, model, is_sidechain,
      input_tokens, output_tokens, cache_read_tokens,
      cache_write_5m_tokens, cache_write_1h_tokens
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
"""

internal val INSERT_SESSION_SHELL_SQL = """
    INSERT OR IGNORE INTO cli_sessions (session_id, file_path, profile_id, host_name)
    VALUES (?, ?, ?, ?);
"""

internal val UPSERT_SESSION_SQL = """
    INSERT INTO cli_sessions (
      session_id, file_path, cwd, git_branch, first_ts, last_ts, primary_model,
      turn_count, input_tokens, output_tokens, cache_read_tokens,
      cache_write_5m_tokens, cache_write_1h_tokens, cost_micros, unpriced_turns,
      profile_id, host_name
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(session_id) DO UPDATE SET
      file_path = excluded.file_path,
      profile_id = excluded.profile_id,
      host_name = COALESCE(excluded.host_name, cli_sessions.host_name),
      cwd = COALESCE(excluded.cwd, cli_sessions.cwd),
      git_branch = COALESCE(excluded.git_branch, cli_sessions.git_branch),
      first_ts = excluded.first_ts,
      last_ts = excluded.last_ts,
      primary_model = excluded.primary_model,
      turn_count = excluded.turn_count,
      input_tokens = excluded.input_tokens,
      output_tokens = excluded.output_tokens,
      cache_read_tokens = excluded.cache_read_tokens,
      cache_write_5m_tokens = excluded.cache_write_5m_tokens,
      cache_write_1h_tokens = excluded.cache_write_1h_tokens,
      cost_micros = excluded.cost_micros,
      unpriced_turns = excluded.unpriced_turns;
"""

internal val SELECT_MAX_SEQ_SQL = """
    SELECT COALESCE(MAX(seq), 0) FROM cli_turns WHERE session_id = ?;
"""

internal val SELECT_TURNS_SQL = """
    SELECT seq, message_id, ts, model, is_sidechain,
           input_tokens, output_tokens, cache_read_tokens,
           cache_write_5m_tokens, cache_write_1h_tokens
    FROM cli_turns
    WHERE session_id = ?
    ORDER BY seq ASC;
"""

internal val SESSION_COLUMNS = """
    session_id, file_path, profile_id, cwd, git_branch, host_name, first_ts, last_ts,
    primary_model, turn_count, input_tokens, output_tokens, cache_read_tokens,
    cache_write_5m_tokens, cache_write_1h_tokens, cost_micros, unpriced_turns
"""

internal val BACKFILL_HOST_NAME_SQL = """
    UPDATE cli_sessions SET host_name = ? WHERE host_name IS NULL;
"""

internal val SELECT_SESSIONS_SQL = """
    SELECT $SESSION_COLUMNS
    FROM cli_sessions
    WHERE (? = 1 OR profile_id = ?)
    ORDER BY last_ts DESC;
"""

internal val SELECT_SESSION_BY_ID_SQL = """
    SELECT $SESSION_COLUMNS FROM cli_sessions WHERE session_id = ?;
"""

/**
 * Agregados de uma janela temporal, calculados a partir dos turnos.
 * O `GROUP BY` por modelo é o que permite precificar cada trecho com a
 * sua própria tarifa; `model` nulo agrupa junto e cai em unpriced.
 */
internal val SELECT_SESSIONS_SINCE_SQL = """
    SELECT s.session_id AS session_id,
           s.file_path AS file_path,
           s.profile_id AS profile_id,
           s.cwd AS cwd,
           s.git_branch AS git_branch,
           s.host_name AS host_name,
           t.model AS model,
           COUNT(*) AS turn_count,
           MIN(t.ts) AS first_ts,
           MAX(t.ts) AS last_ts,
           SUM(t.input_tokens) AS input_tokens,
           SUM(t.output_tokens) AS output_tokens,
           SUM(t.cache_read_tokens) AS cache_read_tokens,
           SUM(t.cache_write_5m_tokens) AS cache_write_5m_tokens,
           SUM(t.cache_write_1h_tokens) AS cache_write_1h_tokens
    FROM cli_turns t
    JOIN cli_sessions s ON s.session_id = t.session_id
    WHERE t.ts >= ?
      AND (? = 1 OR s.profile_id = ?)
    GROUP BY t.session_id, t.model
    ORDER BY last_ts DESC;
"""

/**
 * Mesmas linhas de [SELECT_SESSIONS_SINCE_SQL], com `cwd` e `git_branch`
 * no lugar dos campos que só a lista usa.
 *
 * Manter as duas com o mesmo `GROUP BY` e o mesmo corte é o que garante
 * que o total do resumo bate com o do cabeçalho da lista. Sem `ORDER BY`:
 * a ordenação final é por custo, e custo é calculado no domain.
 */
internal val SELECT_USAGE_GROUPS_SQL = """
    SELECT t.session_id AS session_id,
           s.cwd AS cwd,
           s.git_branch AS git_branch,
           t.model AS model,
           COUNT(*) AS turn_count,
           SUM(t.input_tokens) AS input_tokens,
           SUM(t.output_tokens) AS output_tokens,
           SUM(t.cache_read_tokens) AS cache_read_tokens,
           SUM(t.cache_write_5m_tokens) AS cache_write_5m_tokens,
           SUM(t.cache_write_1h_tokens) AS cache_write_1h_tokens
    FROM cli_turns t
    JOIN cli_sessions s ON s.session_id = t.session_id
    WHERE t.ts >= ?
      AND (? = 1 OR s.profile_id = ?)
    GROUP BY t.session_id, t.model;
"""

/**
 * Tempo de trabalho por sessão: soma dos intervalos entre turnos
 * consecutivos da thread principal menores que o corte.
 *
 * É `activeTimeMillisOf` escrito em SQL, e a equivalência entre os dois é
 * garantida por teste — não por semelhança de leitura. Três detalhes
 * carregam essa equivalência:
 *
 * - **O corte é `?`, não literal.** A constante continua morando no domain;
 *   duplicá-la aqui daria duas respostas para a mesma pergunta.
 * - **`is_sidechain = 0`**: o subagente roda em paralelo com a thread
 *   principal e somar os intervalos dele contaria tempo em dobro.
 * - **`ORDER BY seq`**, e não por `ts`: a ordem de inserção é a do
 *   transcript e `seq` não empata como `ts` pode empatar — o mesmo motivo
 *   que ordena a leitura do contexto vivo.
 *
 * O primeiro turno da janela não tem antecessor, `LAG` devolve `NULL` e ele
 * fica de fora — que é o que a função do domain faz sobre a lista recortada.
 */
internal val SELECT_SESSION_ACTIVE_TIME_SQL = """
    SELECT session_id, SUM(gap) AS active_millis
    FROM (
      SELECT t.session_id AS session_id,
             t.ts - LAG(t.ts) OVER (PARTITION BY t.session_id ORDER BY t.seq) AS gap
      FROM cli_turns t
      JOIN cli_sessions s ON s.session_id = t.session_id
      WHERE t.ts >= ?
        AND t.is_sidechain = 0
        AND (? = 1 OR s.profile_id = ?)
    )
    WHERE gap > 0 AND gap < ?
    GROUP BY session_id;
"""

/** Uma hora em millis; o `hour_bucket` da grade é `ts` dividido por ela. */
internal const val MILLIS_PER_HOUR = 3_600_000L

/**
 * Turnos por hora cheia e modelo.
 *
 * O bucket é UTC — a divisão inteira do `ts` — e quem o traduz para hora
 * local é o domain. Trinta dias produzem no máximo 720 horas × modelos
 * distintos, então a lista cabe folgada na memória.
 */
internal val SELECT_HOURLY_USAGE_SQL = """
    SELECT t.ts / $MILLIS_PER_HOUR AS hour_bucket,
           t.model AS model,
           COUNT(*) AS turn_count,
           SUM(t.input_tokens) AS input_tokens,
           SUM(t.output_tokens) AS output_tokens,
           SUM(t.cache_read_tokens) AS cache_read_tokens,
           SUM(t.cache_write_5m_tokens) AS cache_write_5m_tokens,
           SUM(t.cache_write_1h_tokens) AS cache_write_1h_tokens
    FROM cli_turns t
    JOIN cli_sessions s ON s.session_id = t.session_id
    WHERE t.ts >= ?
      AND (? = 1 OR s.profile_id = ?)
    GROUP BY hour_bucket, t.model;
"""

/**
 * Ferramentas dos turnos da janela.
 *
 * O `ts` vem de `cli_turns`, não da tabela de ferramentas: repetir o
 * carimbo ali seria dado duplicado que poderia divergir. O desempate por
 * nome mantém a ordem total — duas leituras iguais têm de dar listas
 * iguais, ou a tela recompõe a cada tique do laço ao vivo.
 */
internal val SELECT_TOOL_USAGE_SQL = """
    SELECT tt.tool_name AS tool_name,
           SUM(tt.call_count) AS call_count,
           COUNT(*) AS turn_count
    FROM cli_turn_tools tt
    JOIN cli_turns t
      ON t.session_id = tt.session_id AND t.message_id = tt.message_id
    JOIN cli_sessions s ON s.session_id = t.session_id
    WHERE t.ts >= ?
      AND (? = 1 OR s.profile_id = ?)
    GROUP BY tt.tool_name
    ORDER BY call_count DESC, tool_name ASC;
"""

/**
 * Último turno da thread principal de cada sessão, por `MAX(seq)` — a ordem
 * de inserção é a ordem do transcript, e `seq` não empata como `ts` pode
 * empatar. O `idx_cli_turns_session_seq` cobre a agregação.
 */
internal val SELECT_LIVE_CONTEXTS_SQL = """
    SELECT t.session_id AS session_id,
           t.cache_read_tokens AS cache_read_tokens,
           t.model AS model
    FROM cli_turns t
    JOIN (
      SELECT session_id, MAX(seq) AS max_seq
      FROM cli_turns
      WHERE is_sidechain = 0
      GROUP BY session_id
    ) m ON m.session_id = t.session_id AND t.seq = m.max_seq;
"""

internal val SELECT_LIVE_CONTEXT_BY_SESSION_SQL = """
    SELECT t.session_id AS session_id,
           t.cache_read_tokens AS cache_read_tokens,
           t.model AS model
    FROM cli_turns t
    JOIN (
      SELECT session_id, MAX(seq) AS max_seq
      FROM cli_turns
      WHERE is_sidechain = 0 AND session_id = ?
      GROUP BY session_id
    ) m ON m.session_id = t.session_id AND t.seq = m.max_seq
    WHERE t.session_id = ?;
"""

internal val DELETE_TURNS_BY_FILE_SQL = """
    DELETE FROM cli_turns
    WHERE session_id IN (SELECT session_id FROM cli_sessions WHERE file_path = ?);
"""

internal val DELETE_SESSIONS_BY_FILE_SQL = """
    DELETE FROM cli_sessions WHERE file_path = ?;
"""

internal val DELETE_FILE_SQL = """
    DELETE FROM cli_session_files WHERE path = ?;
"""
