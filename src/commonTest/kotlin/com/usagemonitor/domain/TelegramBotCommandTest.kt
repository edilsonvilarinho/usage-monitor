package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AlertKind
import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.isOn
import com.usagemonitor.domain.entity.toggled
import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotButton
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.botAccountTapData
import com.usagemonitor.domain.entity.data
import com.usagemonitor.domain.entity.nextMorningMillis
import com.usagemonitor.domain.entity.parseBotTap
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.TELEGRAM_SNOOZE_MILLIS
import com.usagemonitor.domain.entity.snoozeAlerts
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.applyBotCommand
import com.usagemonitor.domain.entity.focusAlerts
import com.usagemonitor.domain.entity.parseThresholdPercents
import com.usagemonitor.domain.entity.changesApp
import com.usagemonitor.domain.entity.parseKeyboardLabel
import com.usagemonitor.domain.entity.telegramKeyboardRows
import com.usagemonitor.domain.entity.TelegramKeyboardPage
import com.usagemonitor.domain.entity.parseBotCommand
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramBotCommandTest {

    @Test
    fun `commands accept portuguese, english and the bot suffix`() {
        assertEquals(BotCommand.Status, parseBotCommand("/status@usage_monitor_bot"))
        assertEquals(BotCommand.Alerts(false), parseBotCommand("/alertas off"))
        assertEquals(BotCommand.Alerts(true), parseBotCommand("/alerts on"))
        assertEquals(BotCommand.Quiet(QuietHours(22, 7)), parseBotCommand("/silencio 22-07"))
        assertEquals(BotCommand.Quiet(null), parseBotCommand("/quiet off"))
        assertEquals(BotCommand.Threshold(listOf(75, 90)), parseBotCommand("/limiar 90,75"))
        assertEquals(BotCommand.Start("AB12CD"), parseBotCommand("/start AB12CD"))
        assertEquals(BotCommand.Accounts, parseBotCommand("/conta"))
        assertEquals(BotCommand.Resets, parseBotCommand("/reinícios"))
        assertEquals(BotCommand.Resets, parseBotCommand("/resets"))
        assertEquals(BotCommand.Exhaustion, parseBotCommand("/quando"))
        assertEquals(BotCommand.BestAccount, parseBotCommand("/qual"))
        assertEquals(BotCommand.Pace, parseBotCommand("/ritmo"))
        assertEquals(BotCommand.Ping, parseBotCommand("/ping"))
        assertEquals(BotCommand.Spike, parseBotCommand("/pico"))
        assertEquals(BotCommand.Throughput, parseBotCommand("/vazão"))
        assertEquals(BotCommand.Week, parseBotCommand("/semana"))
        assertEquals(BotCommand.Windows(weekly = false), parseBotCommand("/janelas"))
        assertEquals(BotCommand.Compare(7), parseBotCommand("/comparar"))
        assertEquals(BotCommand.StatusImage, parseBotCommand("/status img"))
        assertEquals(BotCommand.Chats, parseBotCommand("/conversas"))
        assertTrue(BotCommand.Alerts(false).changesApp())
        assertEquals(BotCommand.Keyboard(true), parseBotCommand("/teclado"))
        assertEquals(BotCommand.FocusHud, parseBotCommand("/hud"))
        assertEquals(BotCommand.Team, parseBotCommand("/time"))
        assertEquals(BotCommand.AlertMenu, parseBotCommand("/alertas"))
        AlertKind.entries.forEach { kind ->
            assertEquals(BotTap.ToggleAlert(kind), parseBotTap(BotTap.ToggleAlert(kind).data()))
            assertEquals(!UsageAlertSettings().isOn(kind), UsageAlertSettings().toggled(kind).isOn(kind))
        }
        assertEquals(BotTap.UpdateNow("42.1.0"), parseBotTap(BotTap.UpdateNow("42.1.0").data()))
        assertEquals(null, parseBotTap("upd:"))
        assertEquals(BotCommand.Export(7, UsageExportFormat.CSV), parseBotCommand("/exportar"))
        assertEquals(BotCommand.Export(30, UsageExportFormat.JSON), parseBotCommand("/export json 30d"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/exportar pdf"))
        assertTrue(BotCommand.FocusHud.changesApp())
        assertEquals(BotCommand.Keyboard(false), parseBotCommand("/keyboard off"))
        assertEquals(BotCommand.Accounts, parseKeyboardLabel("👤 Account"))
        assertEquals(null, parseKeyboardLabel("oi"))
        // Rodada AE, AE4: as duas páginas e os rótulos antigos da AA30.
        assertEquals(BotCommand.KeyboardPage(TelegramKeyboardPage.MORE), parseKeyboardLabel("☰ More"))
        assertEquals(BotCommand.KeyboardPage(TelegramKeyboardPage.MAIN), parseKeyboardLabel("‹ Voltar"))
        assertEquals(BotCommand.Status, parseKeyboardLabel("📊 Status"))
        assertEquals(BotCommand.Chart(HistoryRange.LAST_24_HOURS), parseKeyboardLabel("📈 Gráfico"))
        assertFalse(BotCommand.KeyboardPage(TelegramKeyboardPage.MORE).changesApp())
        val everyLabel = (telegramKeyboardRows(AppLanguage.PT, TelegramKeyboardPage.MAIN) +
            telegramKeyboardRows(AppLanguage.EN, TelegramKeyboardPage.MORE, allowPcActions = true, showTeam = true)).flatten()
        assertTrue(everyLabel.all { label -> parseKeyboardLabel(label) != null }, everyLabel.toString())
        assertTrue(telegramKeyboardRows(AppLanguage.EN, TelegramKeyboardPage.MAIN).all { row -> row.size == 3 })
        assertFalse(BotCommand.Leave.changesApp())
        assertTrue(TelegramBotSettings(authorizedChats = listOf(TelegramChat(1, "a", readOnly = true))).isReadOnly(1))
        assertFalse(TelegramBotSettings(authorizedChats = listOf(TelegramChat(1, "a", readOnly = true))).isReadOnly(2))
        assertEquals(BotCommand.Leave, parseBotCommand("/sair"))
        // O idioma do app manda (análise da sessão, B02): o `/idioma` por conversa saiu.
        assertEquals(BotCommand.Invalid("/ajuda"), parseBotCommand("/idioma en"))
        assertEquals(BotCommand.Status, parseBotCommand("/status qualquer"))
        assertEquals(BotCommand.Compare(30), parseBotCommand("/compare 30d"))
        assertEquals(BotCommand.Windows(weekly = true), parseBotCommand("/windows 7d"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/janelas 3h"))
        assertEquals(BotCommand.Accounts, parseBotCommand("/accounts@usage_monitor_bot"))
        assertEquals(BotCommand.Refresh, parseBotCommand("/atualizar"))
        assertEquals(BotCommand.Sources, parseBotCommand("/api"))
        assertEquals(BotCommand.QuietMenu, parseBotCommand("/silencio"))
        assertEquals(BotCommand.Chart(HistoryRange.LAST_24_HOURS), parseBotCommand("/grafico"))
        assertEquals(BotCommand.Chart(HistoryRange.LAST_7_DAYS), parseBotCommand("/chart 7d"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/grafico 30d"))
    }

    @Test
    fun `plain text is ignored and bad arguments explain the usage`() {
        assertNull(parseBotCommand("oi, tudo bem?"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/silencio 25-07"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/limiar abc"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/alertas talvez"))
    }

    @Test
    fun `commands change only the alert settings they name`() {
        val base = UsageAlertSettings()

        val off = applyBotCommand(base, BotCommand.Alerts(false))!!
        assertFalse(off.quotaAlertsEnabled)
        assertFalse(off.sessionAlertsEnabled)
        assertEquals(base.quotaPercents, off.quotaPercents)
        assertEquals(QuietHours(12, 13), applyBotCommand(base, BotCommand.Quiet(QuietHours(12, 13)))!!.quietHours)
        assertEquals(listOf(80), applyBotCommand(base, BotCommand.Threshold(listOf(80)))!!.quotaPercents)
        assertNull(applyBotCommand(base, BotCommand.Status))
    }

    @Test
    fun `snooze lasts one hour and turning alerts on ends it`() {
        val snoozed = snoozeAlerts(UsageAlertSettings(), nowMillis = 1_000L)

        assertEquals(1_000L + TELEGRAM_SNOOZE_MILLIS, snoozed.snoozedUntilEpochMillis)
        assertNull(applyBotCommand(snoozed, BotCommand.Alerts(true))!!.snoozedUntilEpochMillis)
        assertEquals(BotButton.SNOOZE, BotButton.fromData("snooze"))
        assertNull(BotButton.fromData("forged"))
    }

    @Test
    fun `pairing code is case insensitive and expires`() {
        val settings = TelegramBotSettings(pairingCode = "AB12CD", pairingExpiresAtMillis = 1_000L)

        assertTrue(settings.acceptsPairing("ab12cd", nowMillis = 999L))
        assertFalse(settings.acceptsPairing("AB12CD", nowMillis = 1_000L))
        assertFalse(settings.acceptsPairing("XX", nowMillis = 0L))
    }

    @Test
    fun `token never shows up in toString`() {
        val settings = TelegramBotSettings(botToken = "123:secret", authorizedChats = listOf(TelegramChat(1L, "@ed")))

        assertFalse(settings.toString().contains("secret"))
    }

    /** #398, Y4: o toque da conta volta com fonte e resumo do rótulo, nunca o rótulo. */
    @Test
    fun `account tap data is short, stable and never carries the label`() {
        val account = UsageSnapshotAccount(ApiSource.ANTHROPIC, "edi@example.com", active = false, quotas = emptyList())
        val data = botAccountTapData(account)

        assertTrue(data.startsWith("acc:ANTHROPIC:"))
        assertFalse("edi@example.com" in data)
        assertTrue(data.encodeToByteArray().size <= 64)
        assertEquals(data, botAccountTapData(account.copy(active = true)))
        assertEquals(BotTap.Account(data.removePrefix("acc:")), parseBotTap(data))
        assertNull(parseBotTap("refresh"))
        assertNull(parseBotTap("acc:"))
    }

    /** #398, Y8: os toques com parâmetro vão e voltam iguais, e dado forjado é recusado. */
    @Test
    fun `remote control taps round trip and reject forged data`() {
        val taps = listOf(BotTap.Source(ApiSource.DEEPSEEK), BotTap.SnoozeFor(240), BotTap.SnoozeUntilMorning)
        taps.forEach { tap -> assertEquals(tap, parseBotTap(tap.data())) }
        assertNull(parseBotTap("api:NOPE"))
        assertNull(parseBotTap("snz:0"))
        assertNull(parseBotTap("snz:99999"))
    }

    /** "Até 08:00" é a próxima 08:00 em BRT: hoje antes dela, amanhã depois. */
    @Test
    fun `next morning is the next 08h in Sao Paulo`() {
        // 22:30 BRT de terça → 08:00 BRT de quarta (11:00 UTC).
        val night = Instant.parse("2026-10-07T01:30:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-10-07T11:00:00Z").toEpochMilliseconds(), nextMorningMillis(night))
        // 06:00 BRT → 08:00 BRT do mesmo dia.
        val dawn = Instant.parse("2026-10-07T09:00:00Z").toEpochMilliseconds()
        assertEquals(Instant.parse("2026-10-07T11:00:00Z").toEpochMilliseconds(), nextMorningMillis(dawn))
        // Exatamente 08:00 já passou: vai para o dia seguinte.
        assertEquals(Instant.parse("2026-10-08T11:00:00Z").toEpochMilliseconds(), nextMorningMillis(Instant.parse("2026-10-07T11:00:00Z").toEpochMilliseconds()))
    }

    /** #408, AA8: `/foco 2h` liga por duas horas, `/foco off` encerra; fora do teto, o uso. */
    @Test
    fun `focus command parses hours and off and sets the deadline`() {
        assertEquals(BotCommand.Focus(2), parseBotCommand("/foco 2h"))
        assertEquals(BotCommand.Focus(3), parseBotCommand("/focus 3"))
        assertEquals(BotCommand.Focus(null), parseBotCommand("/foco off"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/foco 13h"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/foco"))
        assertTrue(BotCommand.Focus(2).changesApp())

        val focused = focusAlerts(UsageAlertSettings(), BotCommand.Focus(2), nowMillis = 1_000L)
        assertEquals(1_000L + 2 * 3_600_000L, focused.focusUntilEpochMillis)
        assertEquals(null, focusAlerts(focused, BotCommand.Focus(null), nowMillis = 5_000L).focusUntilEpochMillis)
    }

    /** #408, AA4: `/avise 30` muda a antecedência, `/avise off` desliga; fora da faixa, o uso. */
    @Test
    fun `remind command parses minutes and off`() {
        assertEquals(BotCommand.Remind(30), parseBotCommand("/avise 30"))
        assertEquals(BotCommand.Remind(15), parseBotCommand("/remind 15min"))
        assertEquals(BotCommand.Remind(null), parseBotCommand("/avise off"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/avise 2"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/avise"))
        assertEquals(30, applyBotCommand(UsageAlertSettings(), BotCommand.Remind(30))?.resetReminderMinutes)
        assertTrue(BotCommand.Remind(null).changesApp())
    }

    /** #408, AA10: `/limiar` sem números pergunta a conta; o toque vai e volta com a chave ou "todas". */
    @Test
    fun `threshold without numbers asks for the account`() {
        assertEquals(BotCommand.ThresholdMenu, parseBotCommand("/limiar"))
        assertEquals(BotCommand.Threshold(listOf(75, 90)), parseBotCommand("/limiar 90,75"))
        assertTrue(BotCommand.ThresholdMenu.changesApp())
        for (tap in listOf(BotTap.ThresholdAccount("ANTHROPIC:1a2b3c"), BotTap.ThresholdAccount(null))) {
            assertEquals(tap, parseBotTap(tap.data()))
        }
        assertEquals(listOf(50, 80), parseThresholdPercents("80, 50"))
        assertEquals(null, parseThresholdPercents("geral"))
    }

    /** Rodada AB: `/analise` com fonte, placar e vista em qualquer ordem; os toques vão e voltam. */
    @Test
    fun `analysis command reads source, scoreboard and view`() {
        assertEquals(BotCommand.Analysis(null, null, false), parseBotCommand("/analise"))
        assertEquals(BotCommand.Analysis(AnalysisView.HOURS, ApiSource.CODEX, false), parseBotCommand("/analise horas codex"))
        assertEquals(BotCommand.Analysis(null, null, true), parseBotCommand("/analysis all"))
        assertEquals(BotCommand.Analysis(AnalysisView.CURVE, null, false), parseBotCommand("/analise 5h img"))
        assertIs<BotCommand.Invalid>(parseBotCommand("/analise xyz"))
        assertFalse(BotCommand.Analysis(null, null, false).changesApp())
        for (tap in listOf(BotTap.AnalysisAccount(null), BotTap.AnalysisAccount("CODEX:9f"), BotTap.AnalysisShow("ANTHROPIC:1a2b", AnalysisView.REPORT))) {
            assertEquals(tap, parseBotTap(tap.data()))
        }
    }
}
