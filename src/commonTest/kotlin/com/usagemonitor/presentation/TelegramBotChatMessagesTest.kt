package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TelegramChat
import kotlin.test.assertTrue
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
    fun `team lists working, online and away members by nickname only`() {
        fun member(alias: String, device: String, online: Boolean, working: Boolean, sessions: Int = 0, lastSeen: String? = null) = TeamMemberPresence(
            member = TeamMemberUsage(deviceId = device, alias = alias, hostName = "PC-SECRETO", lastSeenAt = lastSeen?.let(Instant::parse)),
            isOnline = online, isWorkingNow = working, activeSessionCount = sessions
        )
        val members = listOf(
            member("joão", "d3", online = false, working = false, lastSeen = "2026-10-13T23:10:00Z"),
            member("marina", "d2", online = true, working = false),
            member("edi", "d1", online = true, working = true, sessions = 2)
        )

        val text = TelegramBotChatMessages.team(true, Result.success(members), now, AppLanguage.PT)

        assertEquals(
            "<b>👥 Time agora</b>\n⚡ <b>edi</b> — trabalhando · 2 sessão(ões)\n🟢 <b>marina</b> — online, parado\n⚪ <b>joão</b> — visto há 2 h",
            text
        )
        assertTrue("PC-SECRETO" !in text)
        assertTrue(TelegramBotChatMessages.team(false, null, now, AppLanguage.PT).startsWith("🔒"))
        assertEquals("👥 Nenhuma conta participa de um time neste app.", TelegramBotChatMessages.team(true, null, now, AppLanguage.PT))
        assertEquals("👥 O servidor do time não respondeu agora.", TelegramBotChatMessages.team(true, Result.failure(IllegalStateException()), now, AppLanguage.PT))
        // #494: falha classificada diz onde parou, com a trilha em emoji e de quem é o conserto.
        val handshake = javax.net.ssl.SSLHandshakeException("(certificate_expired) PKIX path validation failed")
        handshake.initCause(java.security.cert.CertificateExpiredException("NotAfter"))
        assertEquals(
            "👥 O servidor do time não respondeu.\n" +
                "<b>Parou em Certificado — venceu</b>\n" +
                "✅ Endereço › ✅ Conexão › ⛔ Certificado › ▫️ Servidor › ▫️ Chave\n" +
                "O certificado do servidor venceu: quem administra o servidor precisa renová-lo. A chave não chegou a ser testada.",
            TelegramBotChatMessages.team(true, Result.failure(handshake), now, AppLanguage.PT)
        )
        assertTrue(
            TelegramBotChatMessages.team(true, Result.failure(java.net.SocketTimeoutException("timeout")), now, AppLanguage.EN)
                .contains("❔ Address › ⛔ Connection")
        )
    }

    @Test
    fun `machine signature goes at the end only with a known name`() {
        assertEquals("<b>Alerta</b>\n<i>💻 NOTE-&lt;EDI&gt;</i>", TelegramBotChatMessages.withMachine("<b>Alerta</b>", "NOTE-<EDI>"))
        assertEquals("<b>Alerta</b>", TelegramBotChatMessages.withMachine("<b>Alerta</b>", null))
        assertEquals("<b>Alerta</b>", TelegramBotChatMessages.withMachine("<b>Alerta</b>", " "))
    }

    @Test
    fun `chats lists the paired chats and marks the current one`() {
        val text = TelegramBotChatMessages.chats(listOf(TelegramChat(1, "@edi"), TelegramChat(2, "Time <Infra>")), 2, AppLanguage.PT)

        assertEquals(
            "<b>Conversas pareadas</b>\n• @edi\n• Time &lt;Infra&gt; — esta\n<i>/sair desfaz o pareamento desta conversa.</i>",
            text
        )
    }

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
