package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.data.datasource.TelegramReplyKeyboard
import com.usagemonitor.data.datasource.TelegramUnauthorizedException
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.parseCommandLine
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** O Telegram de mentira: o token "ruim" é recusado; [onGetMe] faz o papel de quem pareia. */
private class FakeTerminalTelegramApi(private val onGetMe: () -> Unit = {}) : TelegramBotApi(HttpClient(MockEngine { respond("") })) {
    val sent = CopyOnWriteArrayList<Pair<Long, String>>()

    override suspend fun getMe(token: String): String? {
        if (token == "ruim") throw TelegramUnauthorizedException()
        onGetMe()
        return "uso_bot"
    }

    override suspend fun sendMessage(
        token: String,
        chatId: Long,
        text: String,
        html: Boolean,
        buttons: List<List<TelegramButton>>,
        silent: Boolean,
        replyKeyboard: TelegramReplyKeyboard?
    ): Long {
        sent += chatId to text
        return 1L
    }
}

/** `usage-monitor telegram …` (#440, seção do bot e 5.2 do protótipo). */
class TerminalTelegramCommandTest {

    private val token = "123456:segredo-do-bot"

    private fun TerminalConfigHarness.telegram() = LocalTelegramSettingsDataSource(file("telegram.json"))

    @Test
    fun `telegram parses the actions and never takes the token as an argument`() {
        assertEquals(CommandLineRequest.Terminal(TelegramCommand.Pair(5.minutes)), parseCommandLine(arrayOf("telegram", "pair", "--timeout", "5m")))
        assertEquals(CommandLineRequest.Terminal(TelegramCommand.ReadOnly("@ed", true)), parseCommandLine(arrayOf("telegram", "readonly", "@ed", "on")))
        for (arguments in listOf(arrayOf("telegram", "setup", token), arrayOf("telegram", "remove"), arrayOf("telegram", "readonly", "@ed"), arrayOf("telegram"))) {
            val request = parseCommandLine(arguments)
            assertTrue(request is CommandLineRequest.Invalid, arguments.joinToString(" "))
            assertFalse(request.message.contains(token))
        }
    }

    @Test
    fun `setup checks the token with Telegram first and saves it with the bot on`() {
        val api = FakeTerminalTelegramApi()
        TerminalConfigHarness(telegramApi = { api }).use { harness ->
            val (rejected, rejectedOutput) = harness.run("telegram", "setup", secret = "ruim")
            assertEquals(1 to "▲ O Telegram recusou o token, e nada mudou.\n", rejected to rejectedOutput)
            assertEquals("", harness.telegram().load().botToken)

            val (code, output) = harness.run("telegram", "setup", secret = token)
            assertEquals(0, code)
            assertEquals(
                "✓ bot @uso_bot configurado e ligado · vale quando o app ou o serviço abrir\n  Pareie uma conversa com:\n    usage-monitor telegram pair\n",
                output
            )
            assertEquals(token, harness.telegram().load().botToken)
            assertTrue(harness.telegram().load().enabled)
            assertFalse(output.contains(token))
        }
    }

    @Test
    fun `pairing needs whoever collects, because that is who answers the bot`() = TerminalConfigHarness().use { harness ->
        val (code, output) = harness.run("telegram", "pair")
        assertEquals(1, code)
        assertTrue(output.endsWith("    usage-monitor service run\n"), output)
    }

    @Test
    fun `pairing asks the collector for a code and waits for the chat to show up`() {
        lateinit var harness: TerminalConfigHarness
        // Quem pareia: assim que o terminal mostra o código, a conversa aparece no arquivo.
        val api = FakeTerminalTelegramApi(onGetMe = {
            val data = harness.telegram()
            val current = data.load()
            if (current.pairingCode != null) data.save(current.copy(authorizedChats = listOf(TelegramChat(51L, "@ed")), pairingCode = null))
        })
        harness = TerminalConfigHarness(collector = CollectorKind.SERVICE, telegramApi = { api })
        harness.use {
            // O token chega ao coletor pelo próprio terminal, como num servidor de verdade.
            assertEquals(0, harness.run("telegram", "setup", secret = token).first)
            val (code, output) = harness.run("telegram", "pair")

            assertEquals(0, code, output)
            assertTrue(output.contains("No Telegram, abra o seu bot (@uso_bot) e envie: /start "), output)
            assertTrue(output.contains("https://t.me/uso_bot?start="), output)
            assertTrue(output.trimEnd().endsWith("✓ pareado com @ed"), output)
        }
    }

    @Test
    fun `test, status, read only and remove work on the paired chats`() {
        val api = FakeTerminalTelegramApi()
        TerminalConfigHarness(telegramApi = { api }).use { harness ->
            harness.telegram().save(harness.telegram().load().copy(botToken = token, enabled = true, authorizedChats = listOf(TelegramChat(51L, "@ed"))))

            assertEquals(0 to "✓ mensagem de teste enviada para 1 conversa\n", harness.run("telegram", "test"))
            assertEquals(listOf(51L), api.sent.map { (chat, _) -> chat })

            val status = harness.run("telegram", "status").second
            assertTrue(status.contains("@ed (controle)"), status)
            assertTrue(status.contains("••••"), status)
            assertFalse(status.contains(token))

            assertEquals(0 to "✓ @ed só lê · vale quando o app ou o serviço abrir\n", harness.run("telegram", "readonly", "ed", "on"))
            assertTrue(harness.telegram().load().authorizedChats.single().readOnly)
            assertEquals(0 to "✓ @ed não é mais atendida · vale quando o app ou o serviço abrir\n", harness.run("telegram", "remove", "51"))
            assertTrue(harness.telegram().load().authorizedChats.isEmpty())
            assertEquals(1, harness.run("telegram", "remove", "@ninguem").first)
        }
    }
}
