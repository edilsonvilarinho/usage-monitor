package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudWindowDiagnosticsTest {

    @Test
    fun `records the platform decision with version and pid`() {
        withTempFile { file ->
            HudWindowDiagnostics(diagnosticsFile = file).record(
                event = HudHitRegionEvent.SKIPPED,
                version = "41.3.1",
                pid = 4242,
                nowMillis = 0L,
                platform = "linux"
            )

            val entry = file.readLines().single()
            assertTrue(entry.contains("\"event\":\"skipped\""), entry)
            assertTrue(entry.contains("\"platform\":\"linux\""), entry)
            assertTrue(entry.contains("\"version\":\"41.3.1\""), entry)
            assertTrue(entry.contains("\"pid\":4242"), entry)
        }
    }

    /**
     * O recorte entra e sai a cada hover: gravar todos expulsaria do arquivo o
     * que explica o defeito. Cada evento uma vez por processo.
     */
    @Test
    fun `each event is recorded once per process`() {
        withTempFile { file ->
            val diagnostics = HudWindowDiagnostics(diagnosticsFile = file)

            repeat(3) { diagnostics.record(HudHitRegionEvent.SET_FAILED, "UnsupportedOperationException: x", platform = "linux") }
            diagnostics.record(HudHitRegionEvent.ENABLED, platform = "windows")

            val events = file.readLines().map { line -> line.substringAfter("\"event\":\"").substringBefore('"') }
            assertEquals(listOf("set-failed", "enabled"), events)
            assertTrue(file.readLines().first().contains("UnsupportedOperationException: x"))
        }
    }

    @Test
    fun `file is trimmed with the startup limits`() {
        withTempFile { file ->
            file.writeText((1..250).joinToString(separator = "\n", postfix = "\n") { "{\"seq\":$it}" })

            HudWindowDiagnostics(diagnosticsFile = file).record(HudHitRegionEvent.ENABLED, platform = "windows")

            assertEquals(StartupDiagnostics.KEPT_LINES + 1, file.readLines().size)
        }
    }

    private fun withTempFile(block: (File) -> Unit) {
        val dir = createTempDirectory()
        try {
            block(File(dir, "hud-window.jsonl"))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File =
        kotlin.io.path.createTempDirectory("hud-window-diagnostics").toFile()
}
