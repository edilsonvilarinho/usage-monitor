package com.usagemonitor.data

import com.usagemonitor.data.diagnostics.AppLogFile
import com.usagemonitor.data.diagnostics.AppLogLevel
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogFileTest {

    @Test
    fun `each entry becomes one json line with level source and thread`() {
        withTempFile { file ->
            val log = logOn(file)

            log.log(AppLogLevel.WARN, "edt-watchdog", "EDT sem resposta há 5 s", threadName = "watchdog")

            val line = file.readLines().single()
            assertTrue(line.contains("\"ts\":\"2023-11-14T22:13:20Z\""), line)
            assertTrue(line.contains("\"pid\":42"), line)
            assertTrue(line.contains("\"version\":\"9.9.9\""), line)
            assertTrue(line.contains("\"level\":\"warn\""), line)
            assertTrue(line.contains("\"source\":\"edt-watchdog\""), line)
            assertTrue(line.contains("\"thread\":\"watchdog\""), line)
            // Sem exceção, os campos dela não aparecem — ausência é "não houve", não vazio.
            assertFalse(line.contains("\"stack\""), line)
        }
    }

    @Test
    fun `an error carries its class, redacted message, stack and causes`() {
        withTempFile { file ->
            val log = logOn(file)
            val cause = IOException("sem acesso a C:\\Users\\fulano\\.claude\\.credentials.json")
            val error = IllegalStateException("índice indisponível", cause)

            log.error("coroutine", "falha não tratada", error)

            val line = file.readLines().single()
            assertTrue(line.contains("\"error\":\"IllegalStateException: índice indisponível\""), line)
            assertTrue(line.contains("\"stack\":[\""), line)
            assertTrue(line.contains("AppLogFileTest"), line)
            assertTrue(line.contains("\"causes\":[\"IOException: sem acesso a <caminho>/.credentials.json\"]"), line)
            assertFalse(line.contains("fulano"), line)
        }
    }

    @Test
    fun `the message goes through the breadcrumb redaction`() {
        withTempFile { file ->
            val log = logOn(file)

            log.info("stderr", "perfil pessoa@example.com em /home/fulano/.config/x.json")

            val line = file.readLines().single()
            assertTrue(line.contains("<e-mail>"), line)
            assertTrue(line.contains("<caminho>/x.json"), line)
            assertFalse(line.contains("fulano"), line)
            assertFalse(line.contains("pessoa@example.com"), line)
        }
    }

    @Test
    fun `passing the size limit rotates and keeps only the configured files`() {
        withTempFile { file ->
            val log = logOn(file, maxBytes = 200, keptFiles = 3)

            repeat(12) { index -> log.info("teste", "linha $index") }

            val files = log.files()
            assertEquals(listOf("app.log", "app.1.log", "app.2.log"), files.map(File::getName))
            assertFalse(File(file.parentFile, "app.3.log").exists())
            // A linha mais nova está sempre no arquivo em uso.
            assertTrue(file.readLines().last().contains("linha 11"))
        }
    }

    @Test
    fun `a write failure never reaches the caller`() {
        withTempFile { file ->
            // Um diretório no lugar do arquivo faz o append lançar.
            file.mkdirs()
            val log = logOn(file)

            log.error("teste", "não pode lançar", IllegalStateException("x"))
        }
    }

    private fun logOn(file: File, maxBytes: Long = AppLogFile.DEFAULT_MAX_BYTES, keptFiles: Int = 3) = AppLogFile(
        logFile = file,
        maxBytes = maxBytes,
        keptFiles = keptFiles,
        nowMillis = { 1_700_000_000_000L },
        pid = 42,
        version = "9.9.9"
    )

    private fun withTempFile(block: (File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("app-log-test").toFile()
        try {
            block(File(tempDir, "diagnostics/app.log"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
