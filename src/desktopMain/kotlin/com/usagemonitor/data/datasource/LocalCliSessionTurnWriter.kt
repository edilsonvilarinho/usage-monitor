package com.usagemonitor.data.datasource

import java.sql.Connection

// Escrita dos turnos do índice de sessões do Claude CLI. Saiu de
// `LocalCliSessionDataSource` pelo limite de 800 linhas antes de a gravação
// passar a medir a vazão de cada turno (#381).

/** Insere os turnos ignorando `message_id` repetido. Devolve as sessões tocadas. */
internal fun insertCliTurns(
    connection: Connection,
    filePath: String,
    profileId: String,
    hostName: String?,
    turns: List<ParsedTurn>
): Set<String> {
    if (turns.isEmpty()) {
        return emptySet()
    }

    val nextSeqBySession = mutableMapOf<String, Int>()
    val touched = linkedSetOf<String>()

    connection.prepareStatement(INSERT_TURN_SQL).use { statement ->
        for (turn in turns) {
            val seq = nextSeqBySession.getOrPut(turn.sessionId) {
                readMaxSeq(connection, turn.sessionId) + 1
            }
            nextSeqBySession[turn.sessionId] = seq + 1
            touched.add(turn.sessionId)

            statement.setString(1, turn.sessionId)
            statement.setInt(2, seq)
            statement.setString(3, turn.messageId)
            statement.setLong(4, turn.timestampMillis)
            statement.setString(5, turn.model)
            statement.setInt(6, if (turn.isSidechain) 1 else 0)
            statement.setLong(7, turn.inputTokens)
            statement.setLong(8, turn.outputTokens)
            statement.setLong(9, turn.cacheReadTokens)
            statement.setLong(10, turn.cacheWrite5mTokens)
            statement.setLong(11, turn.cacheWrite1hTokens)
            statement.addBatch()
        }
        statement.executeBatch()
    }

    insertTurnTools(connection, turns)

    // Garante que a sessão existe antes do recálculo, mesmo sem turnos novos aceitos.
    connection.prepareStatement(INSERT_SESSION_SHELL_SQL).use { statement ->
        for (sessionId in touched) {
            statement.setString(1, sessionId)
            statement.setString(2, filePath)
            statement.setString(3, profileId)
            statement.setString(4, hostName)
            statement.addBatch()
        }
        statement.executeBatch()
    }

    return touched
}

/**
 * Grava as ferramentas de cada turno.
 *
 * A chave é `(session_id, message_id, tool_name)` e o `INSERT OR REPLACE`
 * torna a passada idempotente: reindexar o mesmo arquivo não soma chamadas
 * de novo, do mesmo jeito que o `message_id` já protege os turnos.
 */
private fun insertTurnTools(connection: Connection, turns: List<ParsedTurn>) {
    val withTools = turns.filter { turn -> turn.toolCalls.isNotEmpty() }
    if (withTools.isEmpty()) {
        return
    }

    connection.prepareStatement(INSERT_TURN_TOOL_SQL).use { statement ->
        for (turn in withTools) {
            for ((toolName, callCount) in turn.toolCalls) {
                statement.setString(1, turn.sessionId)
                statement.setString(2, turn.messageId)
                statement.setString(3, toolName)
                statement.setInt(4, callCount)
                statement.addBatch()
            }
        }
        statement.executeBatch()
    }
}

private fun readMaxSeq(connection: Connection, sessionId: String): Int {
    return connection.prepareStatement(SELECT_MAX_SEQ_SQL).use { statement ->
        statement.setString(1, sessionId)
        statement.executeQuery().use { rows ->
            if (rows.next()) rows.getInt(1) else 0
        }
    }
}
