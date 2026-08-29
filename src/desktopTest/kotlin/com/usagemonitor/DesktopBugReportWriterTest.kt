package com.usagemonitor

import com.usagemonitor.presentation.viewmodel.BugReportSaveRequest
import com.usagemonitor.presentation.viewmodel.bugReportFileName
import kotlinx.datetime.Instant
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBugReportWriterTest {

    @Test
    fun `the json is written where the user chose`() {
        withTempDir { dir ->
            val target = File(dir, "relatorio.json")

            val result = writeBugReportFiles(
                target = target,
                request = BugReportSaveRequest(
                    suggestedFileName = "relatorio.json",
                    json = "{\"ok\":true}"
                )
            )

            assertEquals(target.absolutePath, result.jsonPath)
            assertEquals("{\"ok\":true}", target.readText())
            assertNull(result.screenshotPath)
        }
    }

    /**
     * A imagem vai com o mesmo nome base: nomes independentes fariam o usuário
     * arrastar para a issue dois arquivos sem relação visível entre si.
     */
    @Test
    fun `the screenshot lands beside the json with the same base name`() {
        withTempDir { dir ->
            val target = File(dir, "relatorio.json")

            val result = writeBugReportFiles(
                target = target,
                request = BugReportSaveRequest(
                    suggestedFileName = "relatorio.json",
                    json = "{}",
                    screenshotPng = byteArrayOf(7, 7, 7)
                )
            )

            val screenshot = File(dir, "relatorio.png")
            assertTrue(screenshot.exists())
            assertEquals(screenshot.absolutePath, result.screenshotPath)
            assertEquals(listOf<Byte>(7, 7, 7), screenshot.readBytes().toList())
        }
    }

    /**
     * `:` não é caractere válido em nome de arquivo no Windows, e o ISO cru daria
     * um nome que o próprio diálogo de salvar recusa.
     */
    @Test
    fun `the suggested name has no character the filesystem refuses`() {
        val name = bugReportFileName(Instant.parse("2026-08-29T14:05:09Z"))

        assertEquals("usage-monitor-bug-report-20260829140509.json", name)
        assertFalse(name.contains(':'), name)
    }

    private fun withTempDir(block: (File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("bug-report-writer-test").toFile()
        try {
            block(tempDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
