package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CliSessionMetrics
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.TURN_GAP_CUTOFF_MILLIS
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.sqlite.SQLiteConfig

/**
 * Custo e vazão de uma sessão do Claude Code, lidos do índice da #381 em **somente
 * leitura**, para a linha de status do modo terminal (#439).
 *
 * Não passa por [LocalCliSessionDataSource]: abrir o banco por ele roda a criação e a
 * migração do schema, e a linha de status roda a cada mensagem, possivelmente numa versão
 * diferente da do app aberto que escreve nesse mesmo arquivo. Sem banco, sem a sessão no
 * índice ou com qualquer falha de leitura, `null`: não medido, e nunca custo zero.
 */
internal class LocalCliSessionMetricsReader(
    private val databaseFile: File = LocalCliSessionDataSource.defaultDatabaseFile()
) {
    fun read(sessionId: String): CliSessionMetrics? {
        if (!databaseFile.isFile) {
            return null
        }
        return runCatching {
            val config = SQLiteConfig()
            config.setReadOnly(true)
            DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}", config.toProperties()).use { connection ->
                readCost(connection, sessionId)?.let { (cost, unpriced) ->
                    CliSessionMetrics(costMicros = cost, unpricedTurns = unpriced, throughput = readThroughput(connection, sessionId))
                }
            }
        }.getOrNull()
    }

    private fun readCost(connection: Connection, sessionId: String): Pair<Long, Int>? =
        connection.prepareStatement(SELECT_SESSION_COST_SQL).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getLong("cost_micros") to rows.getInt("unpriced_turns") else null
            }
        }

    private fun readThroughput(connection: Connection, sessionId: String): OutputThroughput? =
        connection.prepareStatement(SELECT_ONE_SESSION_THROUGHPUT_SQL).use { statement ->
            statement.setString(1, sessionId)
            statement.setLong(2, TURN_GAP_CUTOFF_MILLIS)
            // Agregado sem GROUP BY: sempre uma linha, com SUM nulo quando nenhum turno foi medido.
            statement.executeQuery().use { rows ->
                rows.next()
                val tokens = rows.getLong("output_tokens")
                val millis = rows.getLong("generation_millis")
                if (rows.wasNull() || millis <= 0L) null else OutputThroughput(tokens, millis)
            }
        }
}
