package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CliUsageGroupRow
import java.sql.Connection

// Leitura das linhas `(sessão, modelo)` do índice CLI. Saiu de
// `LocalCliSessionDataSource` porque agora tem dois consumidores — o Resumo e o
// corte por modelo da leitura sem janela (#445) — e aquele arquivo já está perto
// do teto de 800 linhas.

/**
 * Linhas de [SELECT_USAGE_GROUPS_SQL] desde [sinceEpochMillis]; zero lê o
 * histórico inteiro, que é o que a leitura "Total" das sessões precisa.
 */
internal fun readUsageGroupRows(
    connection: Connection,
    profileId: String?,
    sinceEpochMillis: Long
): List<CliUsageGroupRow> {
    return connection.prepareStatement(SELECT_USAGE_GROUPS_SQL).use { statement ->
        statement.setLong(1, sinceEpochMillis)
        statement.setInt(2, if (profileId == null) 1 else 0)
        statement.setString(3, profileId)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        CliUsageGroupRow(
                            sessionId = rows.getString("session_id"),
                            cwd = rows.getString("cwd"),
                            gitBranch = rows.getString("git_branch"),
                            model = rows.getString("model"),
                            turnCount = rows.getInt("turn_count"),
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
