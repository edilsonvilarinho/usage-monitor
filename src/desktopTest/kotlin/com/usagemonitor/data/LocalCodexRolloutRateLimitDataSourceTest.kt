package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCodexRolloutRateLimitDataSource
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalCodexRolloutRateLimitDataSourceTest {

    private val home: File = Files.createTempDirectory("codex-home").toFile()

    @AfterTest
    fun cleanUp() {
        home.deleteRecursively()
    }

    private fun rollout(day: String, name: String, vararg lines: String): File {
        val dir = File(home, "sessions/$day").apply { mkdirs() }
        return File(dir, name).apply { writeText(lines.joinToString("\n", postfix = "\n")) }
    }

    private fun tokenCount(timestamp: String, limitId: String, used: Double) =
        """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"token_count",""" +
            """"rate_limits":{"limit_id":"$limitId","limit_name":null,""" +
            """"secondary":{"used_percent":$used,"window_minutes":10080,"resets_at":1791066495},"plan_type":"plus"}}}"""

    @Test
    fun `reads the newest rollouts by date tree and name`() {
        val old = rollout("2026/09/01", "rollout-2026-09-01T10-00-00-a.jsonl")
        val newer = rollout("2026/09/27", "rollout-2026-09-27T08-00-00-b.jsonl")
        val newest = rollout("2026/09/27", "rollout-2026-09-27T09-00-00-c.jsonl")
        val source = LocalCodexRolloutRateLimitDataSource(codexHome = { home }, maxFiles = 2)

        assertEquals(listOf(newest, newer), source.newestRollouts(File(home, "sessions")))
        assertEquals(false, old in source.newestRollouts(File(home, "sessions")))
    }

    @Test
    fun `the latest event across files wins`() = runTest {
        rollout("2026/09/26", "rollout-2026-09-26T10-00-00-a.jsonl", tokenCount("2026-09-26T10:00:00Z", "spark", 40.0))
        rollout("2026/09/27", "rollout-2026-09-27T09-00-00-b.jsonl", tokenCount("2026-09-27T09:00:00Z", "spark", 70.0))
        val source = LocalCodexRolloutRateLimitDataSource(codexHome = { home })

        val result = source.latestRateLimits()

        assertEquals(70.0, result.single().limits.secondary?.usedPercent)
    }

    @Test
    fun `the tail drops the line cut in half`() {
        val file = rollout(
            "2026/09/27",
            "rollout-2026-09-27T09-00-00-a.jsonl",
            "x".repeat(100),
            tokenCount("2026-09-27T09:00:00Z", "spark", 70.0)
        )
        val source = LocalCodexRolloutRateLimitDataSource(codexHome = { home }, tailBytes = file.length() - 50)

        val lines = source.tailLines(file).filter { it.isNotBlank() }

        assertEquals(1, lines.size)
        assertEquals(tokenCount("2026-09-27T09:00:00Z", "spark", 70.0), lines.single())
    }

    @Test
    fun `a missing sessions tree reads nothing`() = runTest {
        assertEquals(emptyList(), LocalCodexRolloutRateLimitDataSource(codexHome = { File(home, "absent") }).latestRateLimits())
    }
}
