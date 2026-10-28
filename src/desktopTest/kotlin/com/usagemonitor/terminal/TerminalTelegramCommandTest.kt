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
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** O Telegram de mentira: o token "ruim" é recusado; [onGetMe] faz o papel de quem pareia. */
private class FakeTerminalTelegramApi(
    private val onGetMe: () -> Unit = {},
    /** A falha do `sendMessage`; `null` entrega. */
    private val sendFailure: Throwable? = null
) : TelegramBotApi(HttpClient(MockEngine { respond("") })) {
    val sent = CopyOnWriteArrayList<Pair<Long, String>>()

    override suspend fun getMe(token: String): String? {
        if (token == "ruim") throw TelegramUnauthorizedException()
        if (token == "sem-rede") throw IOException("api.telegram.org inacessível")
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
        sendFailure?.let { failure -> throw failure }
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

    @Test
    fun `setup with nothing typed or with Telegram out of reach changes nothing`() {
        val api = FakeTerminalTelegramApi()
        TerminalConfigHarness(telegramApi = { api }).use { harness ->
            assertEquals(1 to "▲ Nada foi digitado, e nada mudou.\n", harness.run("telegram", "setup", secret = "   "))

            val (code, output) = harness.run("telegram", "setup", secret = "sem-rede")
            assertEquals(1, code)
            assertEquals("▲ Não deu para conferir o token agora, e nada mudou: api.telegram.org inacessível\n", output)
            assertEquals("", harness.telegram().load().botToken)
        }
    }

    @Test
    fun `test refuses without a token or a paired chat and repeats why the message did not go`() {
        val failing = FakeTerminalTelegramApi(sendFailure = IOException("chat not found"))
        TerminalConfigHarness(telegramApi = { failing }).use { harness ->
            assertEquals(
                1 to "▲ O bot ainda não tem token. Configure com:\n    usage-monitor telegram setup\n",
                harness.run("telegram", "test")
            )

            harness.telegram().save(harness.telegram().load().copy(botToken = token, enabled = true))
            assertEquals(
                1 to "▲ Nenhuma conversa pareada. Pareie com:\n    usage-monitor telegram pair\n",
                harness.run("telegram", "test")
            )

            harness.telegram().save(harness.telegram().load().copy(authorizedChats = listOf(TelegramChat(51L, "@ed"))))
            assertEquals(1 to "▲ A mensagem de teste não saiu: chat not found\n", harness.run("telegram", "test"))
        }
    }

    @Test
    fun `pairing with a collector but no token points to setup`() = TerminalConfigHarness(collector = CollectorKind.SERVICE).use { harness ->
        val (code, output) = harness.run("telegram", "pair")

        assertEquals(1, code)
        assertEquals("▲ O bot ainda não tem token. Configure com:\n    usage-monitor telegram setup\n", output)
    }

    @Test
    fun `pairing stops at the timeout when no chat shows up`() {
        val api = FakeTerminalTelegramApi()
        TerminalConfigHarness(collector = CollectorKind.SERVICE, telegramApi = { api }).use { harness ->
            assertEquals(0, harness.run("telegram", "setup", secret = token).first)

            val (code, output) = harness.run("telegram", "pair", "--timeout", "1s")

            assertEquals(1, code, output)
            assertTrue(output.contains("aguardando… (o código vale até "), output)
            assertTrue(output.trimEnd().endsWith("▲ O código venceu sem nenhuma conversa pareada. Rode de novo para um código novo."), output)
        }
    }

    @Test
    fun `the interactive mode opens the code without waiting for the chat`() {
        val api = FakeTerminalTelegramApi()
        val profile = TerminalProfile(TerminalPalette.NONE, unicode = true)
        TerminalConfigHarness(telegramApi = { api }).use { harness ->
            val refused = harness.runner().output(TelegramCommand.Pair(), AppLanguage.PT, profile)
            assertEquals(1, refused.exitCode)
            assertTrue(refused.lines.joinToString("\n") { line -> line.plainText }.endsWith("usage-monitor service run"))
        }
        TerminalConfigHarness(collector = CollectorKind.SERVICE, telegramApi = { api }).use { harness ->
            assertEquals(0, harness.run("telegram", "setup", secret = token).first)

            val opened = harness.runner().output(TelegramCommand.Pair(), AppLanguage.PT, profile)

            assertEquals(0, opened.exitCode)
            val text = opened.lines.map { line -> line.plainText }
            assertTrue(text.first().startsWith("No Telegram, abra o seu bot (@uso_bot) e envie: /start "), text.toString())
            assertTrue(text.any { line -> line.contains("https://t.me/uso_bot?start=") }, text.toString())
        }
    }
}
