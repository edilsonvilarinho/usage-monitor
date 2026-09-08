package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCodexCliSessionDataSource
import com.usagemonitor.domain.entity.CodexCliRolloutSource
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.io.File

class LocalCodexCliSessionDataSourceTest {
    @Test
    fun `indexes incrementally and ignores response content`() = runTest {
        withFixture { home, dataSource ->
            val file = rolloutFile(home)
            file.writeText(
                listOf(
                    metadataLine(),
                    contextLine("turn-1", "2026-09-08T12:00:00Z", "gpt-test"),
                    responseItemLine("secret prompt must not be materialized"),
                    usageLine("turn-1", "response-1", "2026-09-08T12:00:01Z", 100, 40, 5, 20, 7, 132),
                    "not-json"
                ).joinToString(separator = "\n", postfix = "\n")
            )

            val first = dataSource.syncIndex()
            val summary = dataSource.readSessions().single()

            assertEquals(1, first.scannedFiles)
            assertEquals(1, first.updatedFiles)
            assertEquals(1, first.skippedLines)
            assertEquals(1, summary.responseCount)
            assertEquals(1, summary.turnCount)
            assertEquals(132, summary.totalTokens)
            assertEquals(100, summary.inputTokens)
            assertEquals(40, summary.cachedInputTokens)
            assertEquals(20, summary.outputTokens)
            assertEquals(CodexCliRolloutSource.EXEC, summary.source)
            assertEquals("gpt-test", summary.primaryModel)

            val unchanged = dataSource.syncIndex()
            assertEquals(0, unchanged.updatedFiles)
            assertEquals(1, dataSource.readSession("session-1")?.turns?.size)
        }
    }

    @Test
    fun `does not consume a partial final line and resumes it after newline`() = runTest {
        withFixture { home, dataSource ->
            val file = rolloutFile(home)
            file.writeText(
                listOf(
                    metadataLine(),
                    contextLine("turn-1", "2026-09-08T12:00:00Z", "gpt-test"),
                    usageLine("turn-1", "response-1", "2026-09-08T12:00:01Z", 1, 0, 0, 2, 0, 3)
                ).joinToString(separator = "\n", postfix = "\n")
            )
            dataSource.syncIndex()

            file.appendText(contextLine("turn-2", "2026-09-08T12:01:00Z", "gpt-test") + "\n")
            file.appendText(usageLine("turn-2", "response-2", "2026-09-08T12:01:01Z", 3, 0, 0, 4, 0, 7))

            val partial = dataSource.syncIndex()
            assertEquals(1, partial.updatedFiles)
            assertEquals(1, dataSource.readSession("session-1")?.summary?.responseCount)

            file.appendText("\n")
            dataSource.syncIndex()

            val complete = dataSource.readSession("session-1")
            assertEquals(2, complete?.summary?.responseCount)
            assertEquals(10, complete?.summary?.totalTokens)
            assertEquals(listOf("response-1", "response-2"), complete?.turns?.map { turn -> turn.responseId })
        }
    }

    @Test
    fun `rebuilds the file after truncation without duplicating turns`() = runTest {
        withFixture { home, dataSource ->
            val file = rolloutFile(home)
            file.writeText(
                listOf(
                    metadataLine(),
                    contextLine("turn-1", "2026-09-08T12:00:00Z", "gpt-test"),
                    usageLine("turn-1", "response-1", "2026-09-08T12:00:01Z", 1, 0, 0, 2, 0, 3),
                    contextLine("turn-2", "2026-09-08T12:01:00Z", "gpt-test"),
                    usageLine("turn-2", "response-2", "2026-09-08T12:01:01Z", 3, 0, 0, 4, 0, 7)
                ).joinToString(separator = "\n", postfix = "\n")
            )
            dataSource.syncIndex()
            assertEquals(2, dataSource.readSession("session-1")?.summary?.responseCount)

            file.writeText(
                listOf(
                    metadataLine(),
                    contextLine("turn-3", "2026-09-08T12:02:00Z", "gpt-test"),
                    usageLine("turn-3", "response-3", "2026-09-08T12:02:01Z", 5, 0, 0, 6, 0, 11)
                ).joinToString(separator = "\n", postfix = "\n")
            )
            dataSource.syncIndex()

            val rebuilt = dataSource.readSession("session-1")
            assertEquals(1, rebuilt?.summary?.responseCount)
            assertEquals(11, rebuilt?.summary?.totalTokens)
            assertEquals("response-3", rebuilt?.turns?.single()?.responseId)
        }
    }

    private suspend fun withFixture(block: suspend (File, LocalCodexCliSessionDataSource) -> Unit) {
        val root = createTempDirectory("codex-cli-index").toFile()
        val home = File(root, "codex-home").also { it.mkdirs() }
        val dataSource = LocalCodexCliSessionDataSource(
            codexHomeProvider = { home },
            databaseFile = File(root, "history.db")
        )
        try {
            block(home, dataSource)
        } finally {
            dataSource.close()
            root.deleteRecursively()
        }
    }

    private fun rolloutFile(home: File): File {
        return File(home, "sessions/2026/09/08/rollout-session-1.jsonl").also { file ->
            file.parentFile.mkdirs()
        }
    }

    private fun metadataLine(): String {
        return """{"type":"session_meta","payload":{"session_id":"session-1","cwd":"C:/work","cli_version":"0.153.4","source":"exec","thread_source":"user"}}"""
    }

    private fun contextLine(turnId: String, timestamp: String, model: String): String {
        return """{"type":"turn_context","timestamp":"$timestamp","payload":{"turn_id":"$turnId","cwd":"C:/work","model":"$model"}}"""
    }

    private fun usageLine(
        turnId: String,
        responseId: String,
        timestamp: String,
        input: Long,
        cached: Long,
        cacheWrite: Long,
        output: Long,
        reasoning: Long,
        total: Long
    ): String {
        return """{"type":"token_usage_record","timestamp":"$timestamp","payload":{"session_id":"session-1","turn_id":"$turnId","response_id":"$responseId","usage":{"input_tokens":$input,"cached_input_tokens":$cached,"cache_write_input_tokens":$cacheWrite,"output_tokens":$output,"reasoning_output_tokens":$reasoning,"total_tokens":$total}}}"""
    }

    private fun responseItemLine(content: String): String {
        return """{"type":"response_item","payload":{"type":"message","content":[{"text":"$content"}]}}"""
    }
}
