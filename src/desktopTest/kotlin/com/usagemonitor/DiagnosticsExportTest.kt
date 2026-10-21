package com.usagemonitor

import java.io.File
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsExportTest {

    @Test
    fun `only allowlisted files enter the package, plus the environment report`() {
        withDirs { diagnostics, output ->
            File(diagnostics, "app.log").writeText("{\"level\":\"warn\"}\n")
            File(diagnostics, "startup.jsonl").writeText("{\"outcome\":\"started\"}\n")
            // Corpo de resposta e captura de tela nunca saem por aqui.
            File(diagnostics, "anthropic-credits.jsonl").writeText("{\"body\":\"segredo\"}\n")
            File(diagnostics, "codex-usage.jsonl").writeText("{\"body\":\"segredo\"}\n")
            File(diagnostics, "pending-crash.png").writeBytes(byteArrayOf(1, 2, 3))

            val zip = DiagnosticsExport.export(
                diagnosticsDir = diagnostics,
                outputDir = output,
                environmentReport = { "version=9.9.9\n" },
                nowMillis = 1_700_000_000_000L
            )

            assertEquals("usage-monitor-diagnostics-2023-11-14T221320.zip", zip.name)
            val names = ZipFile(zip).use { file -> file.entries().toList().map { entry -> entry.name } }
            assertEquals(listOf("environment.txt", "app.log", "startup.jsonl"), names)
        }
    }

    @Test
    fun `every line is redacted on the way out`() {
        withDirs { diagnostics, output ->
            File(diagnostics, "linux-update.log").writeText(
                "2026-10-15T12:00:00Z ABORT tar: /home/fulano/.local/share/usage-monitor/x.tar.gz\n" +
                    "conta pessoa@example.com\n"
            )

            val zip = DiagnosticsExport.export(diagnosticsDir = diagnostics, outputDir = output, environmentReport = { "" })

            val content = ZipFile(zip).use { file ->
                file.getInputStream(file.getEntry("linux-update.log")).readBytes().decodeToString()
            }
            assertTrue(content.contains("<caminho>/x.tar.gz"), content)
            assertTrue(content.contains("<e-mail>"), content)
            assertFalse(content.contains("fulano"), content)
        }
    }

    @Test
    fun `a failing environment probe still produces the package`() {
        withDirs { diagnostics, output ->
            val zip = DiagnosticsExport.export(
                diagnosticsDir = diagnostics,
                outputDir = output,
                environmentReport = { error("sem AWT") }
            )

            val environment = ZipFile(zip).use { file ->
                file.getInputStream(file.getEntry("environment.txt")).readBytes().decodeToString()
            }
            assertEquals("falha ao medir: IllegalStateException", environment)
        }
    }

    @Test
    fun `the environment report names every measured key`() {
        val report = currentEnvironmentReport()

        for (key in listOf("version=", "os.name=", "XDG_SESSION_TYPE=", "translucency.perpixel=", "tray.supported=")) {
            assertTrue(report.contains(key), report)
        }
    }

    /**
     * O pacote da #416 não listava os monitores: os limites tiveram de ser
     * deduzidos da posição em que o COSMIC centralizou a janela (#430).
     */
    @Test
    fun `every monitor gets a line with bounds, insets, default depth and alpha`() {
        val lines = screenEnvironmentLines(
            listOf(
                ScreenEnvironmentEntry(":0.0", java.awt.Rectangle(0, 1440, 3440, 1440), java.awt.Insets(0, 0, 0, 0), 24, true),
                ScreenEnvironmentEntry(":0.1", java.awt.Rectangle(421, 0, 2560, 1440), java.awt.Insets(48, 0, 0, 0), 24, false)
            )
        )

        assertEquals(
            listOf(
                "screens" to "2",
                "screen.0" to "id=:0.0 bounds=0,1440 3440x1440 insets=t0,l0,b0,r0 default=24bpp alpha=true",
                "screen.1" to "id=:0.1 bounds=421,0 2560x1440 insets=t48,l0,b0,r0 default=24bpp alpha=false"
            ),
            lines
        )
    }

    @Test
    fun `an unmeasured monitor is written as question marks, and no session as null`() {
        assertEquals(listOf("screens" to null), screenEnvironmentLines(null))
        assertEquals(
            "id=? bounds=? insets=? default=?bpp alpha=?",
            screenEnvironmentText(ScreenEnvironmentEntry(null, null, null, null, null))
        )
    }

    /** O `environment.txt` passa pelo saneamento linha a linha; o id e os números têm de sobreviver. */
    @Test
    fun `the monitor lines survive the redaction of the package`() {
        withDirs { diagnostics, output ->
            val line = "screen.0=" + screenEnvironmentText(
                ScreenEnvironmentEntry("\\Display1", java.awt.Rectangle(-1920, 0, 1920, 1080), java.awt.Insets(0, 0, 40, 0), 32, true)
            )
            val zip = DiagnosticsExport.export(diagnosticsDir = diagnostics, outputDir = output, environmentReport = { "$line\n" })

            ZipFile(zip).use { file ->
                assertEquals(line, file.getInputStream(file.getEntry("environment.txt")).reader().readText().trim())
            }
        }
    }

    @Test
    fun `the linux update log comes from the installation root, not from the data root`() {
        withDirs { diagnostics, output ->
            val installation = File(output.parentFile, "installation-diagnostics").apply { mkdirs() }
            File(diagnostics, "linux-update.log").writeText("da raiz de dados\n")
            File(installation, "linux-update.log").writeText("da instalacao\n")
            File(diagnostics, "app.log").writeText("app\n")

            val zip = DiagnosticsExport.export(
                diagnosticsDir = diagnostics,
                installationDiagnosticsDir = installation,
                outputDir = output,
                environmentReport = { "" }
            )

            ZipFile(zip).use { file ->
                assertEquals("da instalacao", file.getInputStream(file.getEntry("linux-update.log")).reader().readText().trim())
                assertEquals("app", file.getInputStream(file.getEntry("app.log")).reader().readText().trim())
            }
        }
    }

    private fun withDirs(block: (File, File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("diagnostics-export-test").toFile()
        try {
            val diagnostics = File(tempDir, "diagnostics").apply { mkdirs() }
            val output = File(tempDir, "out").apply { mkdirs() }
            block(diagnostics, output)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
