package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.UsageTokenCounts
import kotlinx.coroutines.test.runTest
import java.io.File
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PRICING_PROFILE = "default"
private const val HAIKU_MODEL = "claude-haiku-5-5"
private const val OPUS_MODEL = "claude-opus-5-5"
private const val LONG_PROMPT_INPUT = 150_000L
private const val SHORT_PROMPT_INPUT = 1_000L
private const val TURN_OUTPUT = 1_000L

/** Migração do índice (versões 3 e 4) e leitura de `longPrompt` no Haiku 5.5, que tem faixa de prompt longo. */
class LocalCliSessionPricingMigrationTest {

    @Test
    fun `index below version 4 recomputes session cost from stored turns after the transcript is gone`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "cli.db")
        val transcript = writeTranscript(
            root,
            "session-a",
            assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = OPUS_MODEL, inputTokens = LONG_PROMPT_INPUT, outputTokens = TURN_OUTPUT)
        )
        withPricingDataSource(root, databaseFile) { dataSource -> dataSource.syncIndex() }

        // Simula o índice gravado pela versão 3: custo antigo e contador em 3.
        executeSql(databaseFile, "UPDATE cli_sessions SET cost_micros = 0;", "UPDATE cli_index_meta SET value = '3';")
        // Sem o transcript, só os turnos gravados em `cli_turns` servem de fonte para o recálculo.
        assertEquals(true, transcript.delete())

        withPricingDataSource(root, databaseFile) { dataSource -> dataSource.syncIndex() }

        val expected = ModelPricingTable.forModel(OPUS_MODEL)!!.turnCostMicros(
            inputTokens = LONG_PROMPT_INPUT,
            outputTokens = TURN_OUTPUT
        )
        assertEquals(expected, readSessionCostMicros(databaseFile, "session-a"))
        tempDir.deleteRecursively()
    }

    @Test
    fun `index version 3 keeps the indexed files table because the reset only runs below version 3`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "cli.db")
        writeTranscript(
            root,
            "session-a",
            assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = OPUS_MODEL, inputTokens = SHORT_PROMPT_INPUT, outputTokens = TURN_OUTPUT)
        )
        withPricingDataSource(root, databaseFile) { dataSource -> dataSource.syncIndex() }
        executeSql(databaseFile, "UPDATE cli_index_meta SET value = '3';")
        val filesBefore = readCount(databaseFile, "SELECT COUNT(*) FROM cli_session_files;")

        withPricingDataSource(root, databaseFile) { dataSource -> dataSource.syncIndex() }

        assertEquals(1L, filesBefore)
        assertEquals(filesBefore, readCount(databaseFile, "SELECT COUNT(*) FROM cli_session_files;"))
        tempDir.deleteRecursively()
    }

    @Test
    fun `index version 4 does not recompute a cost already stored`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "cli.db")
        writeTranscript(
            root,
            "session-a",
            assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = OPUS_MODEL, inputTokens = SHORT_PROMPT_INPUT, outputTokens = TURN_OUTPUT)
        )
        withPricingDataSource(root, databaseFile) { dataSource -> dataSource.syncIndex() }
        executeSql(databaseFile, "UPDATE cli_sessions SET cost_micros = 12345;")

        withPricingDataSource(root, databaseFile) { dataSource -> dataSource.syncIndex() }

        assertEquals(12345L, readSessionCostMicros(databaseFile, "session-a"))
        tempDir.deleteRecursively()
    }

    @Test
    fun `usage groups carry the long prompt part of each turn`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "cli.db")
        writeTranscript(
            root,
            "session-a",
            assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = HAIKU_MODEL, inputTokens = LONG_PROMPT_INPUT, outputTokens = TURN_OUTPUT),
            assistantLine("session-a", "msg-2", "2026-08-01T10:05:00Z", model = HAIKU_MODEL, inputTokens = SHORT_PROMPT_INPUT, outputTokens = TURN_OUTPUT)
        )

        withPricingDataSource(root, databaseFile) { dataSource ->
            dataSource.syncIndex()
            val groups = dataSource.readUsageGroups(profileId = PRICING_PROFILE, sinceEpochMillis = 0L)

            assertEquals(1, groups.size)
            assertEquals(
                UsageTokenCounts(inputTokens = LONG_PROMPT_INPUT, outputTokens = TURN_OUTPUT),
                groups.single().longPrompt
            )
        }
        tempDir.deleteRecursively()
    }

    @Test
    fun `hourly usage carries the long prompt part of each turn`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "cli.db")
        writeTranscript(
            root,
            "session-a",
            assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = HAIKU_MODEL, inputTokens = LONG_PROMPT_INPUT, outputTokens = TURN_OUTPUT),
            assistantLine("session-a", "msg-2", "2026-08-01T10:30:00Z", model = HAIKU_MODEL, inputTokens = SHORT_PROMPT_INPUT, outputTokens = TURN_OUTPUT)
        )

        withPricingDataSource(root, databaseFile) { dataSource ->
            dataSource.syncIndex()
            val rows = dataSource.readHourlyUsage(profileId = PRICING_PROFILE, sinceEpochMillis = 0L)

            assertEquals(1, rows.size)
            assertEquals(
                UsageTokenCounts(inputTokens = LONG_PROMPT_INPUT, outputTokens = TURN_OUTPUT),
                rows.single().longPrompt
            )
        }
        tempDir.deleteRecursively()
    }

    @Test
    fun `session summary prices haiku turns on both sides of the long prompt tier`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "cli.db")
        writeTranscript(
            root,
            "session-a",
            assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = HAIKU_MODEL, inputTokens = LONG_PROMPT_INPUT, outputTokens = TURN_OUTPUT),
            assistantLine("session-a", "msg-2", "2026-08-01T10:05:00Z", model = HAIKU_MODEL, inputTokens = SHORT_PROMPT_INPUT, outputTokens = TURN_OUTPUT)
        )

        withPricingDataSource(root, databaseFile) { dataSource ->
            dataSource.syncIndex()
            val session = dataSource.readSessions(profileId = PRICING_PROFILE, sinceEpochMillis = 0L).single()

            val expected = haikuTurnCostMicros(LONG_PROMPT_INPUT) + haikuTurnCostMicros(SHORT_PROMPT_INPUT)
            assertEquals(expected, session.costMicros)
            assertEquals(0, session.unpricedTurnCount)
        }
        tempDir.deleteRecursively()
    }

    private fun haikuTurnCostMicros(inputTokens: Long): Long {
        return ModelPricingTable.forModel(HAIKU_MODEL)!!.turnCostMicros(
            inputTokens = inputTokens,
            outputTokens = TURN_OUTPUT
        )
    }

    private suspend fun withPricingDataSource(
        root: File,
        databaseFile: File,
        block: suspend (LocalCliSessionDataSource) -> Unit
    ) {
        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PRICING_PROFILE, root.absolutePath)) },
            databaseFile = databaseFile
        )
        try {
            block(dataSource)
        } finally {
            dataSource.close()
        }
    }

    private fun writeTranscript(root: File, sessionId: String, vararg lines: String): File {
        val projectDir = File(root, "-workspace-usage-monitor").also { it.mkdirs() }
        val file = File(projectDir, "$sessionId.jsonl")
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        return file
    }

    private fun assistantLine(
        sessionId: String,
        messageId: String,
        timestamp: String,
        model: String,
        inputTokens: Long = 0L,
        outputTokens: Long = 0L
    ): String {
        return """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$sessionId",""" +
            """"timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":false,""" +
            """"message":{"id":"$messageId","model":"$model","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":$inputTokens,"output_tokens":$outputTokens,""" +
            """"cache_read_input_tokens":0,""" +
            """"cache_creation_input_tokens":0,""" +
            """"cache_creation":{"ephemeral_5m_input_tokens":0,""" +
            """"ephemeral_1h_input_tokens":0},"service_tier":"standard"}}}"""
    }

    private fun executeSql(databaseFile: File, vararg statements: String) {
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statements.forEach { sql -> statement.execute(sql) }
            }
        }
    }

    private fun readCount(databaseFile: File, sql: String): Long {
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
        }
    }

    private fun readSessionCostMicros(databaseFile: File, sessionId: String): Long {
        return DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.prepareStatement("SELECT cost_micros FROM cli_sessions WHERE session_id = ?").use { statement ->
                statement.setString(1, sessionId)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
        }
    }
}
