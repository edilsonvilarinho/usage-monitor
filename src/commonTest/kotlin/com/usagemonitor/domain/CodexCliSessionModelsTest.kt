package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CodexCliRolloutSource
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexCliSessionModelsTest {
    @Test
    fun `unknown source is explicit and known source mapping is case insensitive`() {
        assertEquals(CodexCliRolloutSource.CLI, CodexCliRolloutSource.fromRaw("CLI"))
        assertEquals(CodexCliRolloutSource.EXEC, CodexCliRolloutSource.fromRaw("exec"))
        assertEquals(CodexCliRolloutSource.UNKNOWN, CodexCliRolloutSource.fromRaw("future-mode"))
    }

    @Test
    fun `usage exposes raw dimensions without content`() {
        val usage = CodexCliUsageDelta(
            inputTokens = 100,
            cachedInputTokens = 40,
            cacheWriteInputTokens = 5,
            outputTokens = 20,
            reasoningOutputTokens = 7,
            totalTokens = 132
        )

        assertEquals(60, usage.billableInputTokens)
        assertTrue(usage.totalTokens > usage.outputTokens)
    }

    @Test
    fun `summary derives project name from cwd`() {
        val summary = CodexCliSessionSummary(
            sessionId = "session",
            filePath = "rollout.jsonl",
            cwd = "C:/work/usage-monitor",
            firstTs = Instant.parse("2026-09-08T12:00:00Z"),
            lastTs = Instant.parse("2026-09-08T12:01:00Z")
        )

        assertEquals("usage-monitor", summary.projectName)
    }
}
