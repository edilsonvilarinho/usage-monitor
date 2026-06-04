package com.usagemonitor.data

import com.usagemonitor.data.datasource.CodexDiagnosticsFailureEvent
import com.usagemonitor.data.datasource.CodexDiagnosticsSuccessEvent
import com.usagemonitor.data.datasource.LocalCodexDiagnosticsRecorder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalCodexDiagnosticsRecorderTest {

    private val tempDir: File = createTempDirectory(prefix = "usage-monitor-codex-diagnostics").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `does not create diagnostics file when disabled`() {
        val diagnosticsFile = File(tempDir, "codex-usage.jsonl")
        val recorder = LocalCodexDiagnosticsRecorder(
            enabled = false,
            diagnosticsFile = diagnosticsFile
        )

        recorder.recordSuccess(successEvent())
        recorder.recordFailure(failureEvent())

        assertFalse(diagnosticsFile.exists())
    }

    @Test
    fun `writes success and failure events as jsonl when enabled`() {
        val diagnosticsFile = File(tempDir, "codex-usage.jsonl")
        val recorder = LocalCodexDiagnosticsRecorder(
            enabled = true,
            diagnosticsFile = diagnosticsFile
        )

        recorder.recordSuccess(successEvent())
        recorder.recordFailure(failureEvent())

        assertTrue(diagnosticsFile.exists())
        val lines = diagnosticsFile.readLines()
        assertEquals(2, lines.size)

        val successJson = Json.parseToJsonElement(lines[0]).jsonObject
        assertEquals("success", successJson.getValue("event").jsonPrimitive.content)
        assertEquals("plus", successJson.getValue("planType").jsonPrimitive.content)
        assertEquals("1", successJson.getValue("primaryUsedPercent").jsonPrimitive.content)

        val failureJson = Json.parseToJsonElement(lines[1]).jsonObject
        assertEquals("failure", failureJson.getValue("event").jsonPrimitive.content)
        assertEquals("http_error", failureJson.getValue("failureKind").jsonPrimitive.content)
        assertEquals("403", failureJson.getValue("statusCode").jsonPrimitive.content)
    }

    private fun successEvent(): CodexDiagnosticsSuccessEvent {
        return CodexDiagnosticsSuccessEvent(
            timestamp = "2026-06-04T17:04:01Z",
            planType = "plus",
            allowed = true,
            limitReached = false,
            primaryUsedPercent = 1L,
            primaryResetAt = 1780610643L,
            primaryResetAfterSeconds = 17940L,
            primaryLimitWindowSeconds = 18000L,
            secondaryUsedPercent = 12L,
            secondaryResetAt = 1781139417L,
            secondaryResetAfterSeconds = 580000L,
            secondaryLimitWindowSeconds = 604800L
        )
    }

    private fun failureEvent(): CodexDiagnosticsFailureEvent {
        return CodexDiagnosticsFailureEvent(
            timestamp = "2026-06-04T17:04:02Z",
            statusCode = 403,
            failureKind = "http_error",
            message = "Cloudflare challenge page returned by chatgpt.com"
        )
    }
}
