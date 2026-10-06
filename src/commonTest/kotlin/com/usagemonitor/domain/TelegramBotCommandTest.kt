package com.usagemonitor.domain

import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.applyBotCommand
import com.usagemonitor.domain.entity.parseBotCommand
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
}
