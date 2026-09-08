package com.usagemonitor.data

import com.usagemonitor.data.export.CodexCliUsageExporter
import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.CodexCliRolloutSource
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class CodexCliUsageExporterTest {
    @Test
    fun `csv exports the displayed token semantics without cost or content`() {
        val csv = CodexCliUsageExporter.exportSessions(listOf(summary()), UsageExportFormat.CSV)

        assertTrue(csv.startsWith("session_id,project,cwd"))
        assertTrue(csv.contains("Codex Desktop"))
        assertTrue(csv.contains("reasoning_output_tokens"))
        assertTrue(csv.contains("132"))
        assertFalse(csv.contains("cost_usd"))
        assertFalse(csv.contains("prompt"))
    }

    @Test
    fun `json preserves source and token fields without prompt content`() {
        val json = CodexCliUsageExporter.exportSessions(listOf(summary()), UsageExportFormat.JSON)

        assertTrue(json.contains("\"source\": \"EXEC\""))
        assertTrue(json.contains("\"totalTokens\": 132"))
        assertFalse(json.contains("prompt"))
    }

    private fun summary(): CodexCliSessionSummary {
        return CodexCliSessionSummary(
            sessionId = "session-1",
            filePath = "rollout.jsonl",
            cwd = "C:/work/project",
            firstTs = Instant.parse("2026-09-08T12:00:00Z"),
            lastTs = Instant.parse("2026-09-08T12:00:01Z"),
            primaryModel = "gpt-test",
            originator = "Codex Desktop",
            source = CodexCliRolloutSource.EXEC,
            rawSource = "exec",
            threadSource = "user",
            cliVersion = "0.153.4",
            turnCount = 1,
            responseCount = 1,
            inputTokens = 100,
            cachedInputTokens = 40,
            cacheWriteInputTokens = 5,
            outputTokens = 20,
            reasoningOutputTokens = 7,
            totalTokens = 132
        )
    }
}
