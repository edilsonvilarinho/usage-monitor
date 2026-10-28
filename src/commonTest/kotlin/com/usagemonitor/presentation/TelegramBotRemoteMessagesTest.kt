package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SourceHealthEvent
import com.usagemonitor.presentation.ui.TelegramBotRemoteMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Controle remoto do bot (#398, #408): fontes, silêncio, foco, saúde de fonte e limiar por conta. */
class TelegramBotRemoteMessagesTest {

    // 22:30 BRT de terça (6/10/2026).
    private val now = Instant.parse("2026-10-07T01:30:00Z")

    @Test
    fun `without permission the source list shows each state and where to turn it on, in both languages`() {
        val pt = TelegramBotRemoteMessages.sources(setOf(ApiSource.ANTHROPIC), allowed = false, AppLanguage.PT)
        val en = TelegramBotRemoteMessages.sources(setOf(ApiSource.ANTHROPIC), allowed = false, AppLanguage.EN)

        assertTrue(pt.startsWith("<b>Fontes monitoradas</b>\n✅ "), pt)
        assertTrue(pt.endsWith("<i>Mudar fontes pelo bot está desligado em Configurações › Alertas › Bot do Telegram.</i>"), pt)
        assertTrue(en.startsWith("<b>Monitored sources</b>\n✅ "), en)
        assertTrue(en.endsWith("<i>Changing sources from the bot is off in Settings › Alerts › Telegram bot.</i>"), en)
        assertEquals(ApiSource.entries.size - 1, en.lines().count { line -> line.startsWith("⬜ ") })
        assertEquals("<b>Monitored sources</b>\nTap to turn on or off.", TelegramBotRemoteMessages.sources(emptySet(), allowed = true, AppLanguage.EN))
    }

    @Test
    fun `a toggled source says whether it is on and when the first reading comes`() {
        assertEquals("✅ <b>Codex</b> ligada. A primeira leitura chega na próxima coleta.", TelegramBotRemoteMessages.sourceToggled(ApiSource.CODEX, true, AppLanguage.PT))
        assertEquals("✅ <b>Codex</b> on. Its first reading comes with the next collection.", TelegramBotRemoteMessages.sourceToggled(ApiSource.CODEX, true, AppLanguage.EN))
        assertEquals("⬜ <b>Codex</b> desligada.", TelegramBotRemoteMessages.sourceToggled(ApiSource.CODEX, false, AppLanguage.PT))
        assertEquals("⬜ <b>Codex</b> off.", TelegramBotRemoteMessages.sourceToggled(ApiSource.CODEX, false, AppLanguage.EN))
    }

    @Test
    fun `the mute confirmation names tomorrow only when the time is not today`() {
        val sameNight = Instant.parse("2026-10-07T02:30:00Z").toEpochMilliseconds()
        val nextMorning = Instant.parse("2026-10-07T11:00:00Z").toEpochMilliseconds()

        assertEquals(
            "🔕 Alerts muted until <b>23:30</b>.\nTo resume earlier: <code>/alerts on</code>",
            TelegramBotRemoteMessages.snoozedUntil(sameNight, now.toEpochMilliseconds(), AppLanguage.EN)
        )
        assertEquals(
            "🔕 Alertas silenciados até <b>08:00</b> de amanhã.\nPara voltar antes: <code>/alertas on</code>",
            TelegramBotRemoteMessages.snoozedUntil(nextMorning, now.toEpochMilliseconds(), AppLanguage.PT)
        )
        assertTrue(TelegramBotRemoteMessages.snoozedUntil(nextMorning, now.toEpochMilliseconds(), AppLanguage.EN).contains("<b>08:00</b> tomorrow."))
        assertEquals("🔕 Mute alerts for how long?\nFixed hours: <code>/quiet 22-07</code>", TelegramBotRemoteMessages.quietMenu(AppLanguage.EN))
        assertTrue(TelegramBotRemoteMessages.alertMenu(AppLanguage.EN).startsWith("<b>Alerts</b> — tap to turn each kind on or off."))
    }

    @Test
    fun `focus says until when only critical alerts come through`() {
        val until = Instant.parse("2026-10-07T03:00:00Z").toEpochMilliseconds()

        assertEquals(
            "🎧 <b>Focus until 00:00</b>\nOnly critical alerts (≥ 90%) come through. <code>/focus off</code> ends it.",
            TelegramBotRemoteMessages.focus(until, AppLanguage.EN)
        )
        assertEquals("🔔 Focus ended. What was held back arrives with the next collection.", TelegramBotRemoteMessages.focus(null, AppLanguage.EN))
    }

    @Test
    fun `a broken source says since when, why and how old the shown numbers are`() {
        val broken = SourceHealthEvent.Broken("Anthropic · <Max>", "HTTP 529", failingSince = now, lastReadingAt = Instant.parse("2026-10-07T01:10:00Z"))

        assertEquals(
            "⚠ <b>Anthropic · &lt;Max&gt; stopped updating</b>\nFailing since 22:30: HTTP 529\nThe numbers shown are from 22:10.",
            TelegramBotRemoteMessages.sourceHealth(broken, AppLanguage.EN)
        )
        assertTrue(
            TelegramBotRemoteMessages.sourceHealth(broken.copy(lastReadingAt = null), AppLanguage.PT).endsWith("\nNão há leitura guardada desta conta.")
        )
        assertTrue(
            TelegramBotRemoteMessages.sourceHealth(broken.copy(lastReadingAt = null), AppLanguage.EN).endsWith("\nThere is no stored reading for this account.")
        )
    }

    @Test
    fun `the threshold question and answer cover the general one and each account`() {
        assertEquals(
            "<b>General</b> threshold (now 75%, 90%): reply with the numbers, e.g. <code>75,90</code>.",
            TelegramBotRemoteMessages.thresholdAsk(null, listOf(75, 90), AppLanguage.EN)
        )
        assertEquals(
            "Limiar <b>geral</b> (hoje nenhum): responda com os números, ex.: <code>75,90</code>.",
            TelegramBotRemoteMessages.thresholdAsk(null, emptyList(), AppLanguage.PT)
        )
        assertEquals(
            "Threshold for <b>Max &amp; co</b>: reply with the numbers, e.g. <code>50,80</code> — or <code>general</code> to follow the general one (none).",
            TelegramBotRemoteMessages.thresholdAsk("Max & co", emptyList(), AppLanguage.EN)
        )
        assertEquals(
            "<b>Max</b>: alerts at 50% and 80%.\nThe others follow 75%, 90%.",
            TelegramBotRemoteMessages.thresholdSet("Max", listOf(50, 80), listOf(75, 90), AppLanguage.EN)
        )
        assertEquals("<b>Max</b> follows the general threshold again (75%).", TelegramBotRemoteMessages.thresholdSet("Max", null, listOf(75), AppLanguage.EN))
        assertEquals("General threshold: <b>none</b>.", TelegramBotRemoteMessages.thresholdSet(null, null, emptyList(), AppLanguage.EN))
    }
}
