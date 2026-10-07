package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.BotButton
import com.usagemonitor.domain.entity.BotTap
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
        assertEquals(BotCommand.Accounts, parseBotCommand("/accounts@usage_monitor_bot"))
        assertEquals(BotCommand.Refresh, parseBotCommand("/atualizar"))
        assertEquals(BotCommand.Sources, parseBotCommand("/api"))
        assertEquals(BotCommand.QuietMenu, parseBotCommand("/silencio"))
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
}
