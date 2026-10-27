package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.CollectorSourceHealth
import com.usagemonitor.domain.entity.CollectorTeamHealth
import com.usagemonitor.domain.entity.CollectorTelegramHealth
import com.usagemonitor.domain.entity.CollectorWebHealth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** `usage-monitor service status`: cabeçalho por quem coleta, uma linha por parte, código de saída. */
class TerminalServiceViewTest {

    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val profile = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private fun render(input: TerminalServiceStatus, language: AppLanguage = AppLanguage.EN): Pair<List<String>, Int> {
        val output = TerminalServiceView.status(input, now, TerminalServiceStatusLabels(language), profile)
        return output.lines.map { line -> line.plainText } to output.exitCode
    }

    private val base = TerminalServiceStatus(unitInstalled = false, unitActive = false, collector = null, version = "42.1.0")

    @Test
    fun `without a collector the header says why and the exit code is 1`() {
        val (installed, installedCode) = render(base.copy(unitInstalled = true, collectorPid = 99L, memoryBytes = 1L))
        val (missing, missingCode) = render(base)

        assertTrue(installed.first().endsWith(" stopped"), installed.first())
        // Parado não carrega pid nem memória: seriam de um processo que não existe.
        assertTrue("pid" !in installed.first())
        assertEquals(1, installedCode)
        assertTrue(missing.first().endsWith(" service not installed"), missing.first())
        assertEquals(1, missingCode)
        assertTrue(missing.any { line -> "no reading yet" in line })
    }

    @Test
    fun `foreground service and open app are both collecting`() {
        val (service, code) = render(
            base.copy(collector = CollectorKind.SERVICE, collectorSince = now - 5.minutes, collectorPid = 42L, memoryBytes = 50L * 1024 * 1024)
        )
        val (app, _) = render(base.copy(collector = CollectorKind.APP))

        assertTrue(service.first().contains("collecting in the foreground (service run) for "), service.first())
        assertTrue(service.first().contains("pid 42") && service.first().contains("50 MB"), service.first())
        assertEquals(0, code)
        assertTrue(app.first().endsWith(" the open app is collecting"), app.first())
    }

    @Test
    fun `collecting rows show sources, history, bot, team and web`() {
        val (lines, _) = render(
            base.copy(
                collector = CollectorKind.SERVICE,
                lastCollectedAt = now - 1.minutes,
                nextCollectionAt = now + 4.minutes,
                sources = listOf(
                    CollectorSourceHealth(ApiSource.ANTHROPIC, "Anthropic", ok = true),
                    CollectorSourceHealth(ApiSource.CODEX, "Codex", ok = false)
                ),
                historyPath = "/home/a/.usage-monitor/history.db",
                historyBytes = 3L * 1024 * 1024,
                telegram = CollectorTelegramHealth(state = "token_rejected"),
                team = CollectorTeamHealth(active = true, server = "https://team.example.com/api", lastFailure = "401"),
                web = CollectorWebHealth(state = "failed", failure = "port in use"),
                installKind = TerminalInstallKind.SCRIPT
            )
        )
        val text = lines.joinToString("\n")

        assertTrue("Anthropic" in text && "Codex" in text, text)
        assertTrue("/home/a/.usage-monitor/history.db · 3 MB" in text, text)
        assertTrue("token rejected by Telegram" in text, text)
        assertTrue("failed: 401" in text, text)
        assertTrue("failed: port in use" in text, text)
        assertTrue("installed by the script" in text, text)
    }

    @Test
    fun `bot, team and web states in their quiet forms`() {
        val (connecting, _) = render(
            base.copy(
                collector = CollectorKind.SERVICE,
                telegram = CollectorTelegramHealth(state = "connecting"),
                team = CollectorTeamHealth(active = true),
                web = CollectorWebHealth()
            )
        )
        val (failed, _) = render(
            base.copy(
                collector = CollectorKind.SERVICE,
                telegram = CollectorTelegramHealth(state = "failed"),
                team = CollectorTeamHealth(active = false)
            ),
            AppLanguage.PT
        )

        val text = connecting.joinToString("\n")
        assertTrue("connecting" in text, text)
        assertTrue("on, nothing sent yet" in text, text)
        assertTrue(text.lines().any { line -> line.trimEnd().endsWith(" off") }, text)
        val pt = failed.joinToString("\n")
        assertTrue("falhou" in pt, pt)
        assertTrue("desligado" in pt, pt)
    }

    @Test
    fun `old health warns that the collector may be stopped`() {
        val (lines, _) = render(base.copy(collector = CollectorKind.SERVICE, healthUpdatedAt = now - 5.minutes))

        assertTrue(lines.last().contains("the collector may be stopped"), lines.last())
        val (fresh, _) = render(base.copy(collector = CollectorKind.SERVICE, healthUpdatedAt = now - 1.minutes))
        assertTrue(fresh.none { line -> "may be stopped" in line })
    }
}
