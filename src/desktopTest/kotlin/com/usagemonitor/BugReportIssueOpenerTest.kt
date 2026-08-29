package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BugReportEnvelope
import com.usagemonitor.domain.entity.BugReportMachineInfo
import kotlinx.datetime.Instant
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BugReportIssueOpenerTest {

    @Test
    fun `the title is the first line of the description, prefixed`() {
        val title = bugReportIssueTitle("o card do Codex ficou em branco\ne depois voltou")

        assertEquals("Relatório de bug: o card do Codex ficou em branco", title)
    }

    /**
     * Cortar aqui é escolher **onde** o título é cortado, em vez de deixar a
     * decisão para a largura da tela de quem lê a lista de issues.
     */
    @Test
    fun `a long first line is capped with a marker`() {
        val title = bugReportIssueTitle("x".repeat(200))

        assertEquals("Relatório de bug: ".length + MAX_ISSUE_TITLE_SUMMARY, title.length)
        assertTrue(title.endsWith("..."), title)
    }

    @Test
    fun `an empty description still produces a title`() {
        assertEquals("Relatório de bug: sem descrição", bugReportIssueTitle("   "))
    }

    @Test
    fun `the url points at the upstream new issue page and carries both fields`() {
        val url = bugReportIssueUrl(envelope())

        assertTrue(url.startsWith("$BUG_REPORT_REPOSITORY_URL/issues/new?title="), url)
        assertTrue(url.contains("&body="), url)
        // Percent-encoding aplicado: espaço e acento não podem viajar crus.
        assertFalse(url.contains(' '), url)
        assertTrue(url.contains("Relat%C3%B3rio+de+bug"), url)
    }

    @Test
    fun `the browser receives the url when the native browse handles it`() {
        val opened = mutableListOf<URI>()
        val opener = BugReportIssueOpener(
            desktopBrowser = { uri ->
                opened += uri
                true
            }
        )

        val result = opener.open("$BUG_REPORT_REPOSITORY_URL/issues/new?title=a&body=b")

        assertTrue(result.isSuccess, result.exceptionOrNull()?.message.orEmpty())
        assertEquals(1, opened.size)
    }

    /**
     * O que vai para o navegador é montado com texto do usuário. O validador na
     * saída é o que garante que nenhuma montagem futura aponte para outro lugar.
     */
    @Test
    fun `a url outside the upstream new issue page is refused`() {
        val opener = BugReportIssueOpener(desktopBrowser = { true })

        assertTrue(opener.open("https://example.com/issues/new").isFailure)
        assertTrue(opener.open("http://github.com/edilsonvilarinho/usage-monitor/issues/new").isFailure)
        assertTrue(opener.open("https://github.com/outro/repo/issues/new").isFailure)
    }

    private fun envelope() = BugReportEnvelope(
        description = "o card do Codex ficou em branco",
        machineInfo = BugReportMachineInfo(
            osName = "Windows 11",
            osVersion = "10.0.26200",
            osArch = "amd64",
            javaVersion = "21.0.4",
            appVersion = "38.0.2",
            language = AppLanguage.PT,
            uiScalePercent = 115
        ),
        capturedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
        breadcrumbs = emptyList()
    )
}
