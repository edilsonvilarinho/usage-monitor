package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StartupDiagnosticsTest {

    @Test
    fun `records one line per startup with origin and outcome`() {
        withTempFile { file ->
            val diagnostics = StartupDiagnostics(diagnosticsFile = file)

            diagnostics.record(
                origin = StartupOrigin.AUTOSTART,
                outcome = StartupOutcome.STARTED,
                version = "37.0.0",
                pid = 4242,
                processStartedAtMillis = 1_000L,
                nowMillis = 3_500L
            )

            val lines = file.readLines()
            assertEquals(1, lines.size)
            val entry = lines.single()
            assertTrue(entry.contains("\"origin\":\"autostart\""), entry)
            assertTrue(entry.contains("\"outcome\":\"started\""), entry)
            assertTrue(entry.contains("\"version\":\"37.0.0\""), entry)
            assertTrue(entry.contains("\"pid\":4242"), entry)
            assertTrue(entry.contains("\"startupLatencyMillis\":2500"), entry)
        }
    }

    @Test
    fun `second instance exit is recorded with its own outcome`() {
        withTempFile { file ->
            StartupDiagnostics(diagnosticsFile = file).record(
                origin = StartupOrigin.MANUAL,
                outcome = StartupOutcome.SECOND_INSTANCE_EXIT,
                version = "37.0.0",
                pid = 7,
                processStartedAtMillis = null,
                nowMillis = 10L
            )

            val entry = file.readLines().single()
            assertTrue(entry.contains("\"origin\":\"manual\""), entry)
            assertTrue(entry.contains("\"outcome\":\"second-instance-exit\""), entry)
        }
    }

    // O app sobe a cada logon: sem corte o arquivo cresceria para sempre.
    @Test
    fun `file is trimmed to the most recent lines once it grows past the cap`() {
        withTempFile { file ->
            val existing = (1..250).joinToString(separator = "\n", postfix = "\n") { "{\"seq\":$it}" }
            file.parentFile?.mkdirs()
            file.writeText(existing)

            StartupDiagnostics(diagnosticsFile = file).record(
                origin = StartupOrigin.MANUAL,
                outcome = StartupOutcome.STARTED,
                version = "37.0.0",
                pid = 1,
                processStartedAtMillis = null,
                nowMillis = 1L
            )

            val lines = file.readLines()
            assertEquals(StartupDiagnostics.KEPT_LINES + 1, lines.size)
            // As linhas mantidas sao as ULTIMAS: o arranque recente e o que explica
            // o boot que se esta investigando.
            assertEquals("{\"seq\":151}", lines.first())
            assertTrue(lines.last().contains("\"outcome\":\"started\""), lines.last())
        }
    }

    @Test
    fun `origin comes from the auto start argument`() {
        assertEquals(StartupOrigin.AUTOSTART, StartupOrigin.from(arrayOf(StartupOrigin.AUTO_START_ARGUMENT)))
        assertEquals(StartupOrigin.AUTOSTART, StartupOrigin.from(arrayOf("--other", " --autostart ")))
        assertEquals(StartupOrigin.MANUAL, StartupOrigin.from(emptyArray()))
        assertEquals(StartupOrigin.MANUAL, StartupOrigin.from(arrayOf("--autostart-ish")))
    }

    private fun withTempFile(block: (File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("startup-diagnostics-test").toFile()
        try {
            block(File(tempDir, "diagnostics/startup.jsonl"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
