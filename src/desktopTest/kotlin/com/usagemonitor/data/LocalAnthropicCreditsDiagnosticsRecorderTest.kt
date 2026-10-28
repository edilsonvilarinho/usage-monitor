package com.usagemonitor.data

import com.usagemonitor.data.datasource.AnthropicCreditsDiagnosticsEvent
import com.usagemonitor.data.datasource.LocalAnthropicCreditsDiagnosticsRecorder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** O diagnóstico dos créditos da Anthropic: desligado por padrão, uma linha JSON por coleta. */
class LocalAnthropicCreditsDiagnosticsRecorderTest {

    private val tempDir: File = createTempDirectory(prefix = "usage-monitor-credits-diagnostics").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun event(outcome: String) = AnthropicCreditsDiagnosticsEvent(
        timestamp = "2026-10-27T12:00:00Z",
        outcome = outcome,
        extraUsageRaw = """{"is_enabled":true,"used_credits":1250}""",
        spendRaw = null
    )

    @Test
    fun `nothing is written while it is off`() {
        val file = File(tempDir, "diagnostics/anthropic-credits.jsonl")

        LocalAnthropicCreditsDiagnosticsRecorder(isEnabled = false, diagnosticsFile = file).record(event("extra_usage"))

        assertFalse(file.exists())
        assertFalse(file.parentFile.exists())
    }

    @Test
    fun `each collection appends one json line, creating the folder`() {
        val file = File(tempDir, "diagnostics/anthropic-credits.jsonl")
        val recorder = LocalAnthropicCreditsDiagnosticsRecorder(isEnabled = true, diagnosticsFile = file)

        recorder.record(event("extra_usage"))
        recorder.record(event("no_credits"))

        val lines = file.readLines()
        assertEquals(2, lines.size)
        val first = Json.parseToJsonElement(lines[0]).jsonObject
        assertEquals("anthropic_credits", first.getValue("event").jsonPrimitive.content)
        assertEquals("extra_usage", first.getValue("outcome").jsonPrimitive.content)
        assertEquals("""{"is_enabled":true,"used_credits":1250}""", first.getValue("extraUsageRaw").jsonPrimitive.content)
        assertEquals("no_credits", Json.parseToJsonElement(lines[1]).jsonObject.getValue("outcome").jsonPrimitive.content)
    }
}
