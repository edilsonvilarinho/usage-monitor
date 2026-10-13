package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramPing
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.presentation.ui.TelegramBotChatMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** Textos sobre o app e a conversa (#408). */
class TelegramBotChatMessagesTest {

    // 22:10 BRT de terça (13/10/2026).
    private val now = Instant.parse("2026-10-14T01:10:00Z")

    @Test
    fun `ping says version, uptime, next collection and each account's last reading`() {
        val ping = TelegramPing("42.0.0", Instant.parse("2026-10-13T18:58:00Z"), Instant.parse("2026-10-14T01:11:00Z"))
        val snapshot = UsageSnapshot(
            generatedAt = now,
            accounts = listOf(
                UsageSnapshotAccount(ApiSource.ANTHROPIC, "Anthropic · Padrão", false, emptyList(), Instant.parse("2026-10-14T01:09:00Z")),
                UsageSnapshotAccount(ApiSource.MINIMAX, "MiniMax", false, emptyList())
            )
        )

        assertEquals(
            "<b>🏓 Usage Monitor 42.0.0</b>\n" +
                "Aberto há 6 h 12 min\n" +
                "Próxima coleta: 22:11 BRT\n" +
                "\n" +
                "<b>Anthropic · Padrão</b> — coleta 22:09 · há 1 min\n" +
                "<b>MiniMax</b> — sem coleta ainda",
            TelegramBotChatMessages.ping(ping, snapshot, now, AppLanguage.PT)
        )
    }
}
