package com.usagemonitor

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

    // --- Contexto da maquina no registro --------------------------------------

    /**
     * Campo novo com default e retrocompativel: uma linha ja gravada, sem
     * nenhum deles, continua desserializando. Valor novo de enum nao seria.
     */
    @Test
    fun `a line written before the machine context still deserializes`() {
        val legacyLine = """
            {"ts":"2026-08-25T11:00:00Z","pid":42,"version":"37.0.0","origin":"autostart","outcome":"started"}
        """.trimIndent()

        val entry = Json { ignoreUnknownKeys = true }
            .decodeFromString<StartupDiagnosticsEntry>(legacyLine)

        assertEquals("autostart", entry.origin)
        assertNull(entry.sessionType)
        assertNull(entry.alwaysOnTopSupported)
        assertNull(entry.autostartEntryValid)
    }

    /**
     * `null` e "nao medido", nunca "medido e falso": nesta versao so o Linux mede
     * o ambiente grafico e a entrada de autostart, e num arquivo do Windows um
     * `false` afirmaria uma medida que ninguem fez.
     */
    @Test
    fun `the machine context is written when it was measured`() {
        val line = Json { encodeDefaults = true }.encodeToString(
            StartupDiagnosticsEntry(
                ts = "2026-08-29T11:00:00Z",
                pid = 42,
                version = "38.0.2",
                origin = "autostart",
                outcome = "started",
                osName = "Linux",
                osVersion = "6.16.3-200.bazzite.fc42.x86_64",
                sessionType = "wayland",
                desktop = "KDE",
                alwaysOnTopSupported = true,
                autostartEntryPresent = true,
                autostartEntryValid = false
            )
        )

        assertTrue(line.contains("\"sessionType\":\"wayland\""), line)
        assertTrue(line.contains("\"desktop\":\"KDE\""), line)
        assertTrue(line.contains("\"alwaysOnTopSupported\":true"), line)
        assertTrue(line.contains("\"autostartEntryPresent\":true"), line)
        assertTrue(line.contains("\"autostartEntryValid\":false"), line)
    }

    // --- Ambiente grafico do Linux -------------------------------------------

    /**
     * O tipo de sessao e normalizado para minusculas: `X11` e `x11` sao a mesma
     * resposta, e duas grafias no arquivo dariam duas linhas para o mesmo caso.
     * O `XDG_CURRENT_DESKTOP` vai verbatim porque e uma lista separada por dois
     * pontos, e recortar so o primeiro item perderia o que distingue uma sessao
     * derivada da original.
     */
    @Test
    fun `graphics environment normalizes the session type and keeps the desktop list intact`() {
        val environment = linuxGraphicsEnvironment { name ->
            when (name) {
                "XDG_SESSION_TYPE" -> "Wayland"
                "XDG_CURRENT_DESKTOP" -> "ubuntu:GNOME"
                else -> null
            }
        }

        assertEquals("wayland", environment.sessionType)
        assertEquals("ubuntu:GNOME", environment.desktop)
    }

    /**
     * Variavel ausente e variavel em branco sao a mesma resposta -- "nao
     * informado". Guardar string vazia faria um campo sem medida parecer medido.
     */
    @Test
    fun `graphics environment reports missing and blank variables as not informed`() {
        val absent = linuxGraphicsEnvironment { null }
        assertNull(absent.sessionType)
        assertNull(absent.desktop)

        val blank = linuxGraphicsEnvironment { "   " }
        assertNull(blank.sessionType)
        assertNull(blank.desktop)
    }

    /**
     * A leitura do ambiente nao pode derrubar o arranque: o gestor de seguranca
     * de um ambiente restrito lanca em `getenv`, e o registro existe para
     * explicar o app, nao para impedi-lo de subir.
     */
    @Test
    fun `graphics environment survives a lookup that throws`() {
        val environment = linuxGraphicsEnvironment { throw SecurityException("bloqueado") }

        assertNull(environment.sessionType)
        assertNull(environment.desktop)
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
