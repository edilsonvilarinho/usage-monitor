package com.usagemonitor.data

import com.usagemonitor.StartupDiagnostics
import com.usagemonitor.data.diagnostics.LocalBreadcrumbRecorder
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalBreadcrumbRecorderTest {

    @Test
    fun `each step becomes one json line with its wire category`() {
        withTempFile { file ->
            val recorder = recorderOn(file, startMillis = 1_700_000_000_000L)

            recorder.record(BreadcrumbCategory.NAVIGATION, "abriu as Configurações")
            recorder.record(BreadcrumbCategory.API_CALL, "Anthropic: falhou com 401")

            val lines = file.readLines()
            assertEquals(2, lines.size)
            assertTrue(lines[0].contains("\"category\":\"navigation\""), lines[0])
            assertTrue(lines[0].contains("\"ts\":\"2023-11-14T22:13:20Z\""), lines[0])
            assertTrue(lines[1].contains("\"category\":\"api-call\""), lines[1])
        }
    }

    /**
     * O arquivo é JSONL. Uma mensagem multilinha que passasse crua partiria a
     * linha em duas e o leitor veria dois passos onde houve um.
     */
    @Test
    fun `a multiline message is collapsed before it reaches the file`() {
        withTempFile { file ->
            recorderOn(file).record(BreadcrumbCategory.ERROR, "falhou\nna segunda linha")

            assertEquals(1, file.readLines().size)
            assertTrue(file.readText().contains("falhou na segunda linha"), file.readText())
        }
    }

    /**
     * F2: o recorder tinha um caminho de escrita próprio, que só normalizava. A
     * redação precisa valer **no disco**, e não só na fábrica do domain — uma
     * defesa que existe em dois lugares é uma a mais para alguém esquecer.
     */
    @Test
    fun `the file on disk never carries the user name or the account e-mail`() {
        withTempFile { file ->
            recorderOn(file).record(
                BreadcrumbCategory.API_CALL,
                "ANTHROPIC: falhou — Credenciais não encontradas para o perfil " +
                    "'edilson.messias@example.com': C:\\Users\\edilson\\.claude\\.credentials.json."
            )

            val written = file.readText()
            assertTrue(!written.contains("edilson", ignoreCase = true), written)
            assertTrue(!written.contains("example.com"), written)
            assertTrue(written.contains("<caminho>/.credentials.json"), written)
        }
    }

    /**
     * Mesmo corte do registro de arranque, lido das constantes daquele arquivo:
     * dois cortes para o mesmo tipo de arquivo seriam dois donos da decisão.
     */
    @Test
    fun `the file is trimmed to the most recent lines once it grows past the cap`() {
        withTempFile { file ->
            file.parentFile?.mkdirs()
            file.writeText(
                (1..250).joinToString(separator = "\n", postfix = "\n") { index ->
                    """{"ts":"2023-11-14T22:13:20Z","category":"navigation","message":"passo $index"}"""
                }
            )

            recorderOn(file).record(BreadcrumbCategory.CRASH, "caiu")

            val lines = file.readLines()
            assertEquals(StartupDiagnostics.KEPT_LINES + 1, lines.size)
            assertTrue(lines.first().contains("\"message\":\"passo 151\""), lines.first())
            assertTrue(lines.last().contains("\"category\":\"crash\""), lines.last())
        }
    }

    @Test
    fun `reading gives back the last steps, oldest first`() {
        withTempFile { file ->
            val recorder = recorderOn(file, startMillis = 1_700_000_000_000L)
            repeat(5) { index -> recorder.record(BreadcrumbCategory.USE_CASE, "passo $index") }

            val read = recorder.read(limit = 3)

            assertEquals(listOf("passo 2", "passo 3", "passo 4"), read.map(Breadcrumb::message))
            assertEquals(BreadcrumbCategory.USE_CASE, read.first().category)
        }
    }

    @Test
    fun `reading a file that does not exist gives an empty trail, not a failure`() {
        withTempFile { file ->
            assertEquals(emptyList(), recorderOn(file).read(limit = 30))
        }
    }

    /**
     * Um arquivo com linha truncada por desligamento abrupto é exatamente o caso
     * em que o relatório mais importa: a linha ruim é pulada, as boas ficam.
     */
    @Test
    fun `an unreadable line is skipped instead of aborting the read`() {
        withTempFile { file ->
            file.parentFile?.mkdirs()
            file.writeText(
                """{"ts":"2023-11-14T22:13:20Z","category":"navigation","message":"bom"}""" + "\n" +
                    """{"ts":"2023-11-14T22:13:2""" + "\n" +
                    """{"ts":"2023-11-14T22:13:21Z","category":"telemetry","message":"futuro"}""" + "\n" +
                    """{"ts":"2023-11-14T22:13:22Z","category":"crash","message":"caiu"}""" + "\n"
            )

            val read = recorderOn(file).read(limit = 30)

            // A linha truncada e a de categoria desconhecida saem; inventar um
            // valor para a segunda seria afirmar algo que não foi gravado.
            assertEquals(listOf("bom", "caiu"), read.map(Breadcrumb::message))
        }
    }

    /**
     * `record` é chamado de dentro de `catch` e do handler de exceção não
     * tratada: uma falha ao anotar o passo não pode virar a segunda falha.
     */
    @Test
    fun `a write that cannot happen does not throw`() {
        withTempFile { file ->
            // O pai do arquivo é um arquivo comum, então `mkdirs` e o append
            // falham -- e o recorder tem de engolir a falha.
            file.parentFile?.mkdirs()
            val blocked = File(file.parentFile, "bloqueado")
            blocked.writeText("nao sou um diretorio")

            recorderOn(File(blocked, "breadcrumbs.jsonl")).record(BreadcrumbCategory.ERROR, "x")
        }
    }

    private fun recorderOn(file: File, startMillis: Long = 1_700_000_000_000L): LocalBreadcrumbRecorder {
        var current = startMillis
        return LocalBreadcrumbRecorder(
            breadcrumbsFile = file,
            nowMillis = {
                val value = current
                current += 1_000L
                value
            }
        )
    }

    private fun withTempFile(block: (File) -> Unit) {
        val tempDir = kotlin.io.path.createTempDirectory("breadcrumb-recorder-test").toFile()
        try {
            block(File(tempDir, "diagnostics/breadcrumbs.jsonl"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
