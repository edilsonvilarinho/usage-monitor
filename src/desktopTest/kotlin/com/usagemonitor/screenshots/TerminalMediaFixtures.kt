package com.usagemonitor.screenshots

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.ConfigReadSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.WebAccessSettings
import com.usagemonitor.presentation.terminal.TerminalAccount
import com.usagemonitor.presentation.terminal.TerminalAccountRow
import com.usagemonitor.presentation.terminal.TerminalGlyphs
import com.usagemonitor.presentation.terminal.TerminalSourceRow
import com.usagemonitor.presentation.terminal.TuiAccount
import com.usagemonitor.presentation.terminal.TuiActiveSession
import com.usagemonitor.presentation.terminal.TuiData
import com.usagemonitor.presentation.terminal.TuiHistory
import com.usagemonitor.presentation.terminal.TuiHistoryFixtures
import com.usagemonitor.presentation.terminal.TuiSession
import com.usagemonitor.presentation.terminal.TuiSessionPeriod
import com.usagemonitor.presentation.terminal.TuiSessions
import com.usagemonitor.presentation.terminal.TuiSettings
import com.usagemonitor.presentation.terminal.TuiSpark
import com.usagemonitor.presentation.terminal.TuiTeam
import com.usagemonitor.presentation.terminal.TuiTeamMember
import com.usagemonitor.terminal.TuiSettingsInputs
import com.usagemonitor.terminal.TuiSettingsReader
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Os dados sintéticos do GIF do modo terminal (`generateTerminalMedia`): contas, sessões,
 * histórico, time e configurações inventados, desenhados pelo mesmo código da TUI. Nenhum
 * dado real de ninguém.
 */
internal class TerminalMediaFixtures(private val language: AppLanguage) {
    private fun text(pt: String, en: String) = if (language == AppLanguage.PT) pt else en

    /** Quarta, 21/10/2026, 14:23 BRT. */
    val now: Instant = Instant.parse("2026-10-21T17:23:00Z")

    private fun percent(label: String, value: Int, resetsIn: kotlin.time.Duration, type: PeriodType = PeriodType.INTERVAL) =
        UsageSnapshotQuota(label, type, UsageUnit.PERCENTAGE, value.toLong(), 100, value, now + resetsIn, "USD")

    private val work = TerminalAccount(
        ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", text("trabalho", "work"), now - 1.minutes, listOf(70, 90),
        listOf(percent("Claude 5h", 62, 2.hours + 17.minutes), percent("Claude 7d", 41, 51.hours, PeriodType.WEEKLY)),
        plan = "Max"
    )
    private val personal = TerminalAccount(
        ApiSource.ANTHROPIC, "ANTHROPIC:default", "Anthropic", text("pessoal", "personal"), now - 1.minutes, listOf(70, 90),
        listOf(percent("Claude 5h", 18, 3.hours + 40.minutes), percent("Claude 7d", 9, 96.hours, PeriodType.WEEKLY)),
        plan = "Pro"
    )
    private val codex = TerminalAccount(
        ApiSource.CODEX, "CODEX", "Codex", null, now - 1.minutes, listOf(70, 90),
        listOf(percent("Codex 5h", 33, 1.hours + 5.minutes), percent(text("Codex semanal", "Codex weekly"), 74, 70.hours, PeriodType.WEEKLY))
    )
    private val go = TerminalAccount(
        ApiSource.OPENCODE_GO, "OPENCODE_GO", "OpenCode Go", null, now - 1.minutes, listOf(70, 90),
        listOf(percent("Go 5h", 12, 4.hours), percent(text("Go mensal", "Go monthly"), 26, 330.hours, PeriodType.MONTHLY))
    )
    private val deepseek = TerminalAccount(
        ApiSource.DEEPSEEK, "DEEPSEEK", "DeepSeek", null, now - 1.minutes, listOf(70, 90),
        listOf(UsageSnapshotQuota(text("Saldo", "Balance"), PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 0, 782, null, null, "USD"))
    )

    val accounts: List<TerminalAccount> = listOf(work, personal, codex, go, deepseek)

    /** Uma trilha de meia em meia hora que sobe até [peak], com a janela reiniciando em [resets]. */
    private fun spark(peak: Int, resets: Set<Int>, cells: Int = 48): TuiSpark {
        val values = (0 until cells).map { index ->
            val sinceReset = index - (resets.filter { reset -> reset <= index }.maxOrNull() ?: 0)
            (sinceReset * peak / 12).coerceIn(1, peak)
        }
        return TuiSpark(values, resets, now - 24.hours, 30.minutes.inWholeMilliseconds)
    }

    private val session = TuiActiveSession("usage-monitor", "claude-opus-5-5", 72.minutes.inWholeMilliseconds, 9_400_000, false, OutputThroughput(38_000, 1_000_000))

    private fun tuiSession(id: String, project: String, model: String, minutes: Int, tokens: Long, cost: Long?, incomplete: Boolean = false, source: ApiSource = ApiSource.ANTHROPIC) =
        TuiSession(
            id = id, source = source, project = project, model = model, startedAt = now - (minutes + 9).minutes, lastAt = now - 1.minutes,
            durationMillis = minutes.minutes.inWholeMilliseconds, turnCount = minutes + 15, inputTokens = tokens / 16, outputTokens = tokens / 32,
            cacheReadTokens = tokens * 13 / 16, cacheWriteTokens = tokens / 16, costMicros = cost, costIncomplete = incomplete,
            throughput = OutputThroughput(38_000, 1_000_000), contextTokens = 136_000, contextWindow = 200_000
        )

    private val sessions = TuiSessions(
        TuiSessionPeriod.TODAY,
        listOf(
            tuiSession("a", "usage-monitor", "claude-opus-5-5", 72, 4_800_000, 9_400_000),
            tuiSession("b", "api-gateway", "claude-sonnet-5-5", 22, 1_100_000, 1_220_000),
            tuiSession("c", "usage-monitor", "gpt-5.3-codex", 48, 2_300_000, null, source = ApiSource.CODEX),
            tuiSession("d", "dotfiles", "claude-haiku-4-5", 6, 200_000, 40_000),
            tuiSession("e", text("notas", "notes"), "minimax-m3", 15, 600_000, 310_000, incomplete = true)
        )
    )

    // A aba 3 (#463, AI4): janelas sintéticas cortadas pelo mesmo domínio do app.
    private val history = TuiHistory(
        HistoryRange.LAST_24_HOURS,
        listOf(
            TuiHistoryFixtures.row(work, work.quotas[0], listOf(100, 96, 83), 5.hours, now),
            TuiHistoryFixtures.row(work, work.quotas[1], emptyList(), (7 * 24).hours, now),
            TuiHistoryFixtures.row(personal, personal.quotas[0], listOf(41), 5.hours, now),
            TuiHistoryFixtures.row(codex, codex.quotas[0], listOf(70, 100), 5.hours, now),
            TuiHistoryFixtures.row(go, go.quotas[0], emptyList(), 5.hours, now)
        )
    )

    private fun member(alias: String, working: Boolean, online: Boolean, sessions: Int, tokens: Long, cost: Long, lastSeenAgo: kotlin.time.Duration = 1.minutes) =
        TuiTeamMember(alias, online, working, if (working) 1 else 0, now - 3.minutes, now - lastSeenAgo, sessions, tokens, cost, costIncomplete = false)

    private val team = TuiTeam(
        accounts = listOf(text("trabalho", "work")),
        accountIndex = 0,
        members = listOf(
            member("Ana", true, true, 3, 4_800_000, 12_400_000),
            member("Carla", true, true, 5, 7_100_000, 18_100_000),
            member("Bruno", false, true, 2, 2_300_000, 4_950_000),
            member("Diego", false, false, 1, 400_000, 800_000, lastSeenAgo = 2.hours + 10.minutes)
        ),
        referenceNow = now,
        readAt = now
    )

    /** As configurações pelo leitor de verdade da aba 5, sobre preferências temporárias. */
    fun settings(resetAlerts: Boolean): TuiSettings {
        val node = Preferences.userRoot().node("com.usagemonitor.media.${UUID.randomUUID()}")
        try {
            val inputs = TuiSettingsInputs(
                sources = {
                    listOf(
                        TerminalSourceRow(ApiSource.ANTHROPIC, "anthropic", enabled = true, needsKey = false, hasKey = false),
                        TerminalSourceRow(ApiSource.CODEX, "codex", enabled = true, needsKey = false, hasKey = false),
                        TerminalSourceRow(ApiSource.OPENCODE_GO, "opencode_go", enabled = true, needsKey = true, hasKey = true),
                        TerminalSourceRow(ApiSource.DEEPSEEK, "deepseek", enabled = true, needsKey = true, hasKey = true)
                    )
                },
                accounts = { listOf(TerminalAccountRow(ApiSource.ANTHROPIC, text("trabalho", "work"), "default", "~/.claude", monitored = true, ready = true)) },
                values = {
                    ConfigReadSource(
                        PreferencesSettings(node),
                        UsageAlertSettings(quotaPercents = listOf(70, 90), quotaResetAlertsEnabled = resetAlerts, resetReminderMinutes = 15),
                        ProxySettings(),
                        TelegramBotSettings()
                    )
                },
                telegram = { TelegramBotSettings() },
                team = { null },
                teamAccounts = { _ -> emptyList() },
                web = { WebAccessSettings() },
                autostartAvailable = true
            )
            return TuiSettingsReader(inputs).load(language, TerminalGlyphs.UNICODE)
        } finally {
            runCatching { node.removeNode() }
        }
    }

    fun data(resetAlerts: Boolean = true) = TuiData(
        now = now,
        collector = CollectorKind.SERVICE,
        lastCollectedAt = now - 1.minutes,
        teamConfigured = true,
        accounts = accounts.mapIndexed { index, account ->
            TuiAccount(
                account,
                activeSession = if (index == 0) session else null,
                last24h = account.quotas.firstOrNull()?.percent?.let { peak -> spark(peak, if (index == 0) setOf(10, 20, 34) else setOf(24)) }
            )
        },
        sessions = sessions,
        history = history,
        team = team,
        settings = settings(resetAlerts)
    )
}
