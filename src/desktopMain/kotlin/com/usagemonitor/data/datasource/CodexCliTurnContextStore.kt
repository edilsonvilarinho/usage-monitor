package com.usagemonitor.data.datasource

import com.usagemonitor.data.parser.CodexCliTurnContext
import java.sql.Connection

/**
 * Contextos de turno (`turn_context`) de cada rollout, por `(path, turn_id)`.
 * Um registro de uso que chega num trecho posterior resolve o modelo pelo contexto
 * gravado antes, mesmo que um `turn_context` mais novo já tenha sido lido (#561).
 */
internal object CodexCliTurnContextStore {

    fun createTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS codex_cli_turn_contexts (
                  path TEXT NOT NULL,
                  turn_id TEXT NOT NULL,
                  model TEXT,
                  cwd TEXT,
                  PRIMARY KEY (path, turn_id)
                )
                """.trimIndent()
            )
        }
    }

    fun read(connection: Connection, path: String): Map<String, CodexCliTurnContext> {
        return connection.prepareStatement(
            "SELECT turn_id, model, cwd FROM codex_cli_turn_contexts WHERE path = ?"
        ).use { statement ->
            statement.setString(1, path)
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getString("turn_id"),
                            CodexCliTurnContext(model = rows.getString("model"), cwd = rows.getString("cwd"))
                        )
                    }
                }
            }
        }
    }

    /** Grava os contextos do trecho; valor nulo novo não apaga o que já estava gravado. */
    fun upsert(connection: Connection, path: String, contexts: Map<String, CodexCliTurnContext>) {
        if (contexts.isEmpty()) {
            return
        }
        connection.prepareStatement(
            """
            INSERT INTO codex_cli_turn_contexts(path, turn_id, model, cwd)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(path, turn_id) DO UPDATE SET
              model = COALESCE(excluded.model, codex_cli_turn_contexts.model),
              cwd = COALESCE(excluded.cwd, codex_cli_turn_contexts.cwd)
            """.trimIndent()
        ).use { statement ->
            for ((turnId, context) in contexts) {
                statement.setString(1, path)
                statement.setString(2, turnId)
                statement.setString(3, context.model)
                statement.setString(4, context.cwd)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    fun purge(connection: Connection, path: String) {
        connection.prepareStatement("DELETE FROM codex_cli_turn_contexts WHERE path = ?").use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
    }
}
