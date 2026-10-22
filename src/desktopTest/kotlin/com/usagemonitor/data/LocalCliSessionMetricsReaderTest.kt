package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalCliSessionMetricsReader
import com.usagemonitor.domain.entity.CliProjectRoot
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalCliSessionMetricsReaderTest {

    @Test
    fun `reads the indexed cost and the measured throughput of one session`() = runTest {
        val dir = createTempDirectory().toFile()
        try {
            val projects = File(dir, "projects/-workspace-usage-monitor").also { it.mkdirs() }
            File(projects, "session-a.jsonl").writeText(
                listOf(
                    userLine("session-a", "2026-08-01T10:00:00Z"),
                    assistantLine("session-a", "msg-1", "2026-08-01T10:00:10Z", outputTokens = 500L)
                ).joinToString("\n", postfix = "\n")
            )
            val database = File(dir, "usage-history.db")
            val index = LocalCliSessionDataSource({ listOf(CliProjectRoot("default", File(dir, "projects").absolutePath)) }, database)
            index.syncIndex()
            val expectedCost = index.readSessions().single().costMicros
            index.close()

            val metrics = assertNotNull(LocalCliSessionMetricsReader(database).read("session-a"))
            assertEquals(expectedCost, metrics.costMicros)
            assertTrue(metrics.costMicros > 0L)
            assertEquals(0, metrics.unpricedTurns)
            assertEquals(500L, metrics.throughput?.outputTokens)
            assertEquals(10_000L, metrics.throughput?.generationMillis)
            assertNull(LocalCliSessionMetricsReader(database).read("other-session"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `without a database there is nothing to read and nothing is created`() {
        val dir = createTempDirectory().toFile()
        try {
            val missing = File(dir, "usage-history.db")
            assertNull(LocalCliSessionMetricsReader(missing).read("session-a"))
            assertTrue(!missing.exists())
            // Arquivo que não é um índice: a leitura falha calada e não cria schema nele.
            val empty = File(dir, "empty.db").also { it.writeText("") }
            assertNull(LocalCliSessionMetricsReader(empty).read("session-a"))
            assertEquals(0L, empty.length())
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun userLine(sessionId: String, timestamp: String): String =
        """{"type":"user","uuid":"uuid-user-$timestamp","sessionId":"$sessionId",""" +
            """"timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"isSidechain":false,"message":{"role":"user","content":"oi"}}"""

    private fun assistantLine(sessionId: String, messageId: String, timestamp: String, outputTokens: Long): String =
        """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$sessionId",""" +
            """"timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":false,""" +
            """"message":{"id":"$messageId","model":"claude-opus-5","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":10,"output_tokens":$outputTokens,"cache_read_input_tokens":0,""" +
            """"cache_creation_input_tokens":0,"cache_creation":{"ephemeral_5m_input_tokens":0,""" +
            """"ephemeral_1h_input_tokens":0},"service_tier":"standard"}}}"""
}
