package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.sql.Connection

/** Um lote pronto para envio, já com as sessões dos turnos que carrega. */
internal data class PendingTeamBatch(
    val sessions: List<CliSessionSummary>,
    val turns: List<CliSessionTurn>
) {
    val isEmpty: Boolean
        get() = turns.isEmpty()
}

/**
 * Decide o que ainda falta enviar ao servidor de time.
 *
 * O marcador é o maior `seq` já enviado por sessão, guardado em
 * `team_sync_state` no mesmo `usage-history.db` do índice. Usa a conexão
 * compartilhada do [LocalCliSessionDataSource] — as duas leituras disputariam o
 * mesmo arquivo se abrissem conexões separadas.
 *
 * O envio não precisa ser exato: o servidor deduplica por
 * `(account_key, session_id, message_id)`. O marcador existe para não reenviar o
 * histórico inteiro a cada 30 segundos, não para garantir exatidão.
 */
internal class LocalTeamSyncStateDataSource(
    private val connectionManager: SqliteConnectionManager
) {

    /**
     * Turnos ainda não enviados de um perfil, no máximo [limit].
     *
     * As sessões vêm junto porque o servidor rejeita turno órfão: a consulta de
     * leitura dele faz `JOIN` na sessão e um turno sem ela nunca apareceria.
     */
    suspend fun readPendingBatch(profileId: String, limit: Int): PendingTeamBatch {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                ensureSchema(connection)
                resetStaleWatermarks(connection)

                val turns = readPendingTurns(connection, profileId, limit)
                if (turns.isEmpty()) {
                    return@useConnection PendingTeamBatch(emptyList(), emptyList())
                }

                val sessionIds = turns.map { turn -> turn.sessionId }.toSet()
                PendingTeamBatch(
                    sessions = readSessions(connection, sessionIds),
                    turns = turns
                )
            }
        }
    }

    /** Avança o marcador para o maior `seq` de cada sessão do lote confirmado. */
    suspend fun markPushed(turns: List<CliSessionTurn>, pushedAt: Instant) {
        if (turns.isEmpty()) {
            return
        }

        val maxSeqBySession = turns.groupBy { turn -> turn.sessionId }
            .mapValues { (_, sessionTurns) -> sessionTurns.maxOf { turn -> turn.seq } }

        withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                ensureSchema(connection)
                connection.prepareStatement(UPSERT_STATE_SQL).use { statement ->
                    for ((sessionId, maxSeq) in maxSeqBySession) {
                        statement.setString(1, sessionId)
                        statement.setInt(2, maxSeq)
                        statement.setLong(3, pushedAt.toEpochMilliseconds())
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
            }
        }
    }

    /** Zera o marcador de tudo: força o próximo ciclo a reenviar o histórico. */
    suspend fun resetAll() {
        withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                ensureSchema(connection)
                connection.createStatement().use { statement ->
                    statement.execute("DELETE FROM team_sync_state;")
                }
            }
        }
    }

    private fun ensureSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(CREATE_STATE_TABLE_SQL)
        }
    }

    /**
     * Descarta marcadores maiores que o maior `seq` que a sessão ainda tem.
     *
     * Acontece quando um transcript é truncado ou recriado no mesmo caminho: o
     * índice reconstrói a sessão do zero e os `seq` recomeçam. Sem este reset, os
     * turnos novos ficariam abaixo do marcador antigo e nunca seriam enviados.
     */
    private fun resetStaleWatermarks(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(RESET_STALE_STATE_SQL)
        }
    }

    private fun readPendingTurns(
        connection: Connection,
        profileId: String,
        limit: Int
    ): List<CliSessionTurn> {
        return connection.prepareStatement(SELECT_PENDING_TURNS_SQL).use { statement ->
            statement.setString(1, profileId)
            statement.setInt(2, limit)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            CliSessionTurn(
                                sessionId = rows.getString("session_id"),
                                seq = rows.getInt("seq"),
                                messageId = rows.getString("message_id"),
                                ts = Instant.fromEpochMilliseconds(rows.getLong("ts")),
                                model = rows.getString("model"),
                                isSidechain = rows.getInt("is_sidechain") != 0,
                                inputTokens = rows.getLong("input_tokens"),
                                outputTokens = rows.getLong("output_tokens"),
                                cacheReadTokens = rows.getLong("cache_read_tokens"),
                                cacheWrite5mTokens = rows.getLong("cache_write_5m_tokens"),
                                cacheWrite1hTokens = rows.getLong("cache_write_1h_tokens")
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Só os campos que o servidor guarda da sessão.
     *
     * `file_path` fica de fora de propósito: é um caminho da máquina de quem
     * enviou e não tem nenhum uso para o time.
     */
    private fun readSessions(
        connection: Connection,
        sessionIds: Set<String>
    ): List<CliSessionSummary> {
        if (sessionIds.isEmpty()) {
            return emptyList()
        }

        val placeholders = sessionIds.joinToString(separator = ",") { "?" }
        val sql = SELECT_SESSIONS_TEMPLATE_SQL.replace(SESSION_IDS_PLACEHOLDER, placeholders)

        return connection.prepareStatement(sql).use { statement ->
            sessionIds.forEachIndexed { index, sessionId ->
                statement.setString(index + 1, sessionId)
            }
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            CliSessionSummary(
                                sessionId = rows.getString("session_id"),
                                filePath = "",
                                profileId = rows.getString("profile_id"),
                                cwd = rows.getString("cwd"),
                                gitBranch = rows.getString("git_branch"),
                                hostName = rows.getString("host_name"),
                                firstTs = Instant.fromEpochMilliseconds(rows.getLong("first_ts")),
                                lastTs = Instant.fromEpochMilliseconds(rows.getLong("last_ts")),
                                liveContextTokens = rows.getLong("live_context_tokens"),
                                liveContextModel = rows.getString("live_context_model")
                            )
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val SESSION_IDS_PLACEHOLDER = "{{SESSION_IDS}}"

        val CREATE_STATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS team_sync_state (
              session_id TEXT PRIMARY KEY,
              last_seq_sent INTEGER NOT NULL DEFAULT -1,
              last_pushed_at INTEGER NOT NULL DEFAULT 0
            );
        """

        val RESET_STALE_STATE_SQL = """
            DELETE FROM team_sync_state
            WHERE last_seq_sent > (
              SELECT COALESCE(MAX(t.seq), -1)
              FROM cli_turns t
              WHERE t.session_id = team_sync_state.session_id
            );
        """

        val UPSERT_STATE_SQL = """
            INSERT INTO team_sync_state (session_id, last_seq_sent, last_pushed_at)
            VALUES (?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
              last_seq_sent = MAX(excluded.last_seq_sent, team_sync_state.last_seq_sent),
              last_pushed_at = excluded.last_pushed_at;
        """

        /**
         * Ordena por `(session_id, seq)` para que o `LIMIT` corte no meio de uma
         * sessão sem deixar buracos: o marcador guarda o maior `seq` enviado, e o
         * resto da sessão sai no lote seguinte.
         */
        val SELECT_PENDING_TURNS_SQL = """
            SELECT t.session_id AS session_id,
                   t.seq AS seq,
                   t.message_id AS message_id,
                   t.ts AS ts,
                   t.model AS model,
                   t.is_sidechain AS is_sidechain,
                   t.input_tokens AS input_tokens,
                   t.output_tokens AS output_tokens,
                   t.cache_read_tokens AS cache_read_tokens,
                   t.cache_write_5m_tokens AS cache_write_5m_tokens,
                   t.cache_write_1h_tokens AS cache_write_1h_tokens
            FROM cli_turns t
            JOIN cli_sessions s ON s.session_id = t.session_id
            LEFT JOIN team_sync_state st ON st.session_id = t.session_id
            WHERE s.profile_id = ?
              AND t.seq > COALESCE(st.last_seq_sent, -1)
            ORDER BY t.session_id ASC, t.seq ASC
            LIMIT ?;
        """

        /**
         * O contexto vivo é o `cache_read` do último turno principal — o mesmo
         * critério que a lista local usa, para que o modal de time mostre a mesma
         * saturação que a máquina de origem mostraria.
         */
        val SELECT_SESSIONS_TEMPLATE_SQL = """
            SELECT s.session_id AS session_id,
                   s.profile_id AS profile_id,
                   s.cwd AS cwd,
                   s.git_branch AS git_branch,
                   s.host_name AS host_name,
                   s.first_ts AS first_ts,
                   s.last_ts AS last_ts,
                   COALESCE(live.cache_read_tokens, 0) AS live_context_tokens,
                   live.model AS live_context_model
            FROM cli_sessions s
            LEFT JOIN (
              SELECT t.session_id AS session_id,
                     t.cache_read_tokens AS cache_read_tokens,
                     t.model AS model
              FROM cli_turns t
              JOIN (
                SELECT session_id, MAX(seq) AS max_seq
                FROM cli_turns
                WHERE is_sidechain = 0
                GROUP BY session_id
              ) m ON m.session_id = t.session_id AND t.seq = m.max_seq
            ) live ON live.session_id = s.session_id
            WHERE s.session_id IN ($SESSION_IDS_PLACEHOLDER);
        """
    }
}
