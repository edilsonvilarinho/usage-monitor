package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.ModelPricingTable
import java.sql.Connection

// Schema, migração e registro de arquivos do índice de sessões do Claude CLI.
// Saíram de `LocalCliSessionDataSource` pelo limite de 800 linhas (#303).

internal fun initializeCliIndexSchema(connection: Connection, hostName: () -> String?) {
    connection.createStatement().use { statement ->
        statement.execute("PRAGMA journal_mode = WAL;")
        statement.execute("PRAGMA synchronous = NORMAL;")
        // O histórico de uso vive no mesmo arquivo por outra conexão. Sem
        // timeout, uma escrita que encontra o writer lock ocupado falha na
        // hora — e a indexação de background segura o lock por transações
        // longas.
        statement.execute("PRAGMA busy_timeout = 5000;")
        statement.execute(CREATE_FILES_TABLE_SQL)
        statement.execute(CREATE_SESSIONS_TABLE_SQL)
        statement.execute(CREATE_TURNS_TABLE_SQL)
        statement.execute(CREATE_TURN_TOOLS_TABLE_SQL)
        statement.execute(CREATE_INDEX_META_TABLE_SQL)
    }

    // Bases criadas antes do suporte a múltiplas contas não têm `profile_id`.
    // Recriar as tabelas descartaria o índice já construído, então a coluna é
    // acrescentada e as linhas antigas ficam nulas até a próxima sincronização
    // reatribuí-las pelo caminho do arquivo.
    addColumnIfMissing(connection, "cli_session_files", "profile_id")
    addColumnIfMissing(connection, "cli_sessions", "profile_id")
    // Antes de `migrateSchemaVersion`, obrigatoriamente: o reset dele apaga
    // `cli_session_files`, que é de onde este backfill lê a conta. Invertido,
    // sessão de transcript já apagado do disco ficaria órfã para sempre.
    backfillSessionProfile(connection)

    // Tudo que já está no índice foi lido de um transcript desta máquina, então
    // atribuir o hostname atual às linhas antigas é factual — e é a única
    // chance de preenchê-las: `syncIndex` só reprocessa arquivos alterados.
    if (addColumnIfMissing(connection, "cli_sessions", "host_name")) {
        backfillHostName(connection, hostName())
    }

    // Vazão de saída (#381). Quem preenche as linhas antigas é a releitura
    // forçada pela versão 3 do índice: o conflito do `message_id` grava as
    // duas colunas nos turnos que já existiam.
    addColumnIfMissing(connection, "cli_turns", "request_ts", type = "INTEGER")
    addColumnIfMissing(connection, "cli_turns", "last_line_ts", type = "INTEGER")

    connection.createStatement().use { statement ->
        statement.execute(CREATE_SESSIONS_INDEX_SQL)
        statement.execute(CREATE_TURNS_INDEX_SQL)
        statement.execute(CREATE_SESSIONS_PROFILE_INDEX_SQL)
        statement.execute(CREATE_TURNS_TS_INDEX_SQL)
        statement.execute(CREATE_TURN_TOOLS_INDEX_SQL)
    }

    migrateSchemaVersion(connection)
}

/**
 * Reindexa do zero quando a versão do índice muda.
 *
 * A tabela de ferramentas nasceu vazia para tudo que já estava indexado, e
 * `syncIndex` só reprocessa arquivo alterado. Sem forçar a releitura, o
 * ranking de ferramentas mostraria apenas as sessões novas — um número que
 * parece completo e não é.
 *
 * O contador mora em `cli_index_meta`, tabela deste índice. Ele já esteve no
 * `PRAGMA user_version` e aquilo nunca funcionou: o histórico de uso vive no
 * mesmo arquivo e grava `user_version = 3` a cada abertura, então esta
 * migração lia um número alheio, concluía que já havia rodado e voltava sem
 * fazer nada. Um pragma por arquivo não comporta dois donos.
 *
 * O reset apaga as linhas de `cli_session_files`, não os offsets: `syncIndex`
 * decide pular por tamanho + data de modificação e faz `continue` **antes** de
 * olhar o `last_offset`, então zerá-lo era inerte. Sem a linha do arquivo a
 * varredura o trata como novo e o lê inteiro. Sessões e turnos ficam onde
 * estão e voltam pelos mesmos `INSERT OR IGNORE`/`INSERT OR REPLACE`, sem
 * duplicar.
 */
internal fun migrateSchemaVersion(connection: Connection) {
    val current = readIndexSchemaVersion(connection)
    if (current >= INDEX_SCHEMA_VERSION) {
        return
    }

    if (current < 3) {
        connection.createStatement().use { statement ->
            statement.execute(RESET_INDEXED_FILES_SQL)
        }
    }
    if (current < 4) {
        recomputeAllSessionCosts(connection)
    }
    connection.prepareStatement(UPSERT_INDEX_SCHEMA_VERSION_SQL).use { statement ->
        statement.setString(1, INDEX_SCHEMA_VERSION.toString())
        statement.executeUpdate()
    }
}

/**
 * Recalcula `cost_micros` e `unpriced_turns` de toda sessão com a tarifa atual. Só a migração
 * para a versão 4 chama: a faixa de prompt longo do Haiku 5.5 mudou o custo de turnos já
 * gravados, e `syncIndex` pula arquivo inalterado. Os turnos já estão em `cli_turns`, então o
 * custo sai deles sem reler transcript. Turno sem tarifa conta como não precificado, o mesmo
 * critério de `CliSessionTurn.costMicros`. Roda numa transação: ou recalcula tudo ou nada, e o
 * upsert da versão só acontece depois do commit. Cobre a faixa de prompt longo do Haiku 5.5 e as
 * tarifas de cache read de Opus 5.5 e Sonnet 5.5 (divisor 20), Fable 5.1 e Mythos 5.1 (divisor 40).
 */
internal fun recomputeAllSessionCosts(connection: Connection) {
    connection.autoCommit = false
    try {
        val sessionIds = connection.createStatement().use { statement ->
            statement.executeQuery(SELECT_ALL_SESSION_IDS_SQL).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(rows.getString(1))
                    }
                }
            }
        }
        for (sessionId in sessionIds) {
            var costMicros = 0L
            var unpricedTurns = 0
            connection.prepareStatement(SELECT_TURN_TOKENS_SQL).use { statement ->
                statement.setString(1, sessionId)
                statement.executeQuery().use { rows ->
                    while (rows.next()) {
                        val pricing = ModelPricingTable.forModel(rows.getString("model"))
                        if (pricing == null) {
                            unpricedTurns++
                        } else {
                            costMicros += pricing.turnCostMicros(
                                inputTokens = rows.getLong("input_tokens"),
                                outputTokens = rows.getLong("output_tokens"),
                                cacheReadTokens = rows.getLong("cache_read_tokens"),
                                cacheWrite5mTokens = rows.getLong("cache_write_5m_tokens"),
                                cacheWrite1hTokens = rows.getLong("cache_write_1h_tokens")
                            )
                        }
                    }
                }
            }
            connection.prepareStatement(UPDATE_SESSION_COST_SQL).use { statement ->
                statement.setLong(1, costMicros)
                statement.setInt(2, unpricedTurns)
                statement.setString(3, sessionId)
                statement.executeUpdate()
            }
        }
        connection.commit()
    } catch (error: Throwable) {
        connection.rollback()
        throw error
    } finally {
        connection.autoCommit = true
    }
}

private const val SELECT_ALL_SESSION_IDS_SQL = "SELECT session_id FROM cli_sessions;"

private const val SELECT_TURN_TOKENS_SQL = """
    SELECT model, input_tokens, output_tokens, cache_read_tokens,
           cache_write_5m_tokens, cache_write_1h_tokens
    FROM cli_turns WHERE session_id = ?;
"""

private const val UPDATE_SESSION_COST_SQL =
    "UPDATE cli_sessions SET cost_micros = ?, unpriced_turns = ? WHERE session_id = ?;"

/** Versão gravada do índice; ausente ou ilegível conta como zero. */
internal fun readIndexSchemaVersion(connection: Connection): Int {
    return connection.prepareStatement(SELECT_INDEX_SCHEMA_VERSION_SQL).use { statement ->
        statement.executeQuery().use { rows ->
            if (rows.next()) rows.getString(1)?.toIntOrNull() ?: 0 else 0
        }
    }
}

/**
 * Reatribui a conta das sessões que ficaram sem ela.
 *
 * A conta da sessão só era gravada por `recomputeSession`, que roda apenas
 * para sessão com turno novo. Quando a coluna nasceu, `syncIndex` reprocessou
 * os arquivos inalterados, não leu turno nenhum — o offset já estava no fim —
 * e mesmo assim carimbou `cli_session_files`. Da passada seguinte em diante o
 * arquivo passou a ser pulado, e a sessão ficou com a conta nula para sempre:
 * invisível em toda tela, porque `profile_id = ?` não casa com nulo.
 *
 * Roda em toda abertura, e não só quando a coluna é criada, porque nas bases
 * já danificadas ela existe há muito. Em base sã não casa nenhuma linha.
 */
internal fun backfillSessionProfile(connection: Connection) {
    connection.createStatement().use { statement ->
        statement.execute(BACKFILL_SESSION_PROFILE_SQL)
    }
}

internal fun backfillHostName(connection: Connection, hostName: String?) {
    val host = hostName ?: return
    connection.prepareStatement(BACKFILL_HOST_NAME_SQL).use { statement ->
        statement.setString(1, host)
        statement.executeUpdate()
    }
}

/** Devolve `true` quando a coluna acabou de ser criada. */
internal fun addColumnIfMissing(
    connection: Connection,
    table: String,
    column: String,
    type: String = "TEXT"
): Boolean {
    val existing = connection.prepareStatement("PRAGMA table_info($table);").use { statement ->
        statement.executeQuery().use { rows ->
            buildSet {
                while (rows.next()) {
                    add(rows.getString("name"))
                }
            }
        }
    }
    if (column in existing) {
        return false
    }

    connection.createStatement().use { statement ->
        statement.execute("ALTER TABLE $table ADD COLUMN $column $type;")
    }
    return true
}

internal data class IndexedFileState(
    val profileId: String?,
    val lastModified: Long,
    val sizeBytes: Long,
    val lastOffset: Long
)

internal fun readFileStates(connection: Connection): Map<String, IndexedFileState> {
    return connection.prepareStatement(SELECT_FILES_SQL).use { statement ->
        statement.executeQuery().use { rows ->
            buildMap {
                while (rows.next()) {
                    val path = rows.getString("path")
                    put(
                        path,
                        IndexedFileState(
                            profileId = rows.getString("profile_id"),
                            lastModified = rows.getLong("last_modified"),
                            sizeBytes = rows.getLong("size_bytes"),
                            lastOffset = rows.getLong("last_offset")
                        )
                    )
                }
            }
        }
    }
}

/** Atribui [profileId] a todas as sessões vindas de [path]. Caminho e conta são 1:1. */
internal fun stampSessionProfile(connection: Connection, path: String, profileId: String) {
    connection.prepareStatement(STAMP_SESSION_PROFILE_SQL).use { statement ->
        statement.setString(1, profileId)
        statement.setString(2, path)
        statement.setString(3, profileId)
        statement.executeUpdate()
    }
}

internal fun upsertFileState(
    connection: Connection,
    path: String,
    profileId: String,
    lastModified: Long,
    sizeBytes: Long,
    lastOffset: Long
) {
    connection.prepareStatement(UPSERT_FILE_SQL).use { statement ->
        statement.setString(1, path)
        statement.setString(2, profileId)
        statement.setLong(3, lastModified)
        statement.setLong(4, sizeBytes)
        statement.setLong(5, lastOffset)
        statement.executeUpdate()
    }
}

/** Apaga sessões e turnos vindos de um arquivo que precisa ser reindexado do zero. */
internal fun purgeIndexedFile(connection: Connection, path: String) {
    connection.prepareStatement(DELETE_TURN_TOOLS_BY_FILE_SQL).use { statement ->
        statement.setString(1, path)
        statement.executeUpdate()
    }
    connection.prepareStatement(DELETE_TURNS_BY_FILE_SQL).use { statement ->
        statement.setString(1, path)
        statement.executeUpdate()
    }
    connection.prepareStatement(DELETE_SESSIONS_BY_FILE_SQL).use { statement ->
        statement.setString(1, path)
        statement.executeUpdate()
    }
    connection.prepareStatement(DELETE_FILE_SQL).use { statement ->
        statement.setString(1, path)
        statement.executeUpdate()
    }
}
