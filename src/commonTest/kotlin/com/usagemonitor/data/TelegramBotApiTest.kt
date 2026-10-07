package com.usagemonitor.data

import com.usagemonitor.data.datasource.TelegramBadRequestException
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.data.datasource.TelegramRateLimitedException
import com.usagemonitor.data.datasource.TelegramUnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TelegramBotApiTest {

    private fun api(status: HttpStatusCode, body: String, capture: (String, String?) -> Unit = { _, _ -> }): TelegramBotApi {
        val engine = MockEngine { request ->
            capture(request.url.toString(), (request.body as? TextContent)?.text)
            respond(body, status)
        }
        return TelegramBotApi(HttpClient(engine) { install(HttpTimeout) })
    }

    @Test
    fun `updates keep only text messages with chat identity`() = runTest {
        val body = """{"ok":true,"result":[
            {"update_id":10,"message":{"chat":{"id":51,"username":"edilson"},"text":"/status"}},
            {"update_id":11,"edited_message":{"chat":{"id":51}}},
            {"update_id":12,"message":{"chat":{"id":77,"first_name":"Ana"},"text":"oi"}}]}"""
        var url = ""
        val updates = api(HttpStatusCode.OK, body) { u, _ -> url = u }.getUpdates("123:abc", offset = 10, timeoutSeconds = 25)

        assertEquals(listOf(10L, 12L), updates.map { it.updateId })
        assertEquals("@edilson", updates[0].chatName)
        assertEquals("Ana", updates[1].chatName)
        assertTrue(url.contains("offset=10") && url.contains("timeout=25"))
    }

    @Test
    fun `send message posts chat id and text as json`() = runTest {
        var sent: String? = null
        api(HttpStatusCode.OK, """{"ok":true}""") { _, body -> sent = body }.sendMessage("123:abc", 51L, "Olá")

        assertEquals("""{"chat_id":51,"text":"Olá"}""", sent)
    }

    @Test
    fun `html message declares the parse mode`() = runTest {
        var sent: String? = null
        api(HttpStatusCode.OK, """{"ok":true}""") { _, body -> sent = body }.sendMessage("123:abc", 51L, "<b>Olá</b>", html = true)

        assertEquals("""{"chat_id":51,"text":"<b>Olá</b>","parse_mode":"HTML"}""", sent)
    }

    @Test
    fun `button tap arrives with its data and the message that carries it`() = runTest {
        val body = """{"ok":true,"result":[
            {"update_id":20,"callback_query":{"id":"cb1","data":"refresh","message":{"message_id":700,"chat":{"id":51,"username":"ed"},"text":"x"}}}]}"""
        var url = ""
        val update = api(HttpStatusCode.OK, body) { u, _ -> url = u }.getUpdates("123:abc", offset = 20, timeoutSeconds = 25).single()

        assertEquals("cb1", update.callbackId)
        assertEquals("refresh", update.callbackData)
        assertEquals(700L, update.messageId)
        assertEquals(51L, update.chatId)
        assertEquals(null, update.text)
        assertTrue(url.contains("callback_query"))
    }

    @Test
    fun `buttons go as inline keyboard rows and empty rows are dropped`() = runTest {
        var sent: String? = null
        api(HttpStatusCode.OK, """{"ok":true}""") { _, body -> sent = body }
            .sendMessage(
                "123:abc", 51L, "x", html = true,
                buttons = listOf(listOf(TelegramButton("A", "refresh"), TelegramButton("B", "snooze")), emptyList(), listOf(TelegramButton("C", "acc:0")))
            )

        assertEquals(
            """{"chat_id":51,"text":"x","parse_mode":"HTML","reply_markup":{"inline_keyboard":[[{"text":"A","callback_data":"refresh"},{"text":"B","callback_data":"snooze"}],[{"text":"C","callback_data":"acc:0"}]]}}""",
            sent
        )
    }

    /** #398: o painel fixado edita a mesma mensagem, então o envio devolve o id dela. */
    @Test
    fun `send message returns the message id and silent skips the sound`() = runTest {
        var sent: String? = null
        val id = api(HttpStatusCode.OK, """{"ok":true,"result":{"message_id":812,"chat":{"id":51}}}""") { _, body -> sent = body }
            .sendMessage("123:abc", 51L, "x", silent = true)

        assertEquals(812L, id)
        assertEquals("""{"chat_id":51,"text":"x","disable_notification":true}""", sent)
    }

    @Test
    fun `pin and unpin target chat and message without notifying`() = runTest {
        val calls = mutableListOf<Pair<String, String?>>()
        val client = api(HttpStatusCode.OK, """{"ok":true,"result":true}""") { u, body -> calls += u to body }
        client.pinChatMessage("123:abc", 51L, 812L)
        client.unpinChatMessage("123:abc", 51L, 812L)

        assertTrue(calls[0].first.endsWith("/pinChatMessage"))
        assertEquals("""{"chat_id":51,"message_id":812,"disable_notification":true}""", calls[0].second)
        assertTrue(calls[1].first.endsWith("/unpinChatMessage"))
        assertEquals("""{"chat_id":51,"message_id":812}""", calls[1].second)
    }

    /** O 400 é tipado: "not modified" não é erro do painel, "not found" pede mensagem nova. */
    @Test
    fun `bad request carries the telegram description`() = runTest {
        val notModified = assertFailsWith<TelegramBadRequestException> {
            api(HttpStatusCode.BadRequest, """{"ok":false,"error_code":400,"description":"Bad Request: message is not modified"}""")
                .editMessageText("123:abc", 51L, 700L, "y")
        }
        assertTrue(notModified.isNotModified)
        val gone = assertFailsWith<TelegramBadRequestException> {
            api(HttpStatusCode.BadRequest, """{"ok":false,"error_code":400,"description":"Bad Request: message to edit not found"}""")
                .editMessageText("123:abc", 51L, 700L, "y")
        }
        assertTrue(gone.isMessageGone)
    }

    @Test
    fun `photo goes as multipart with the png and an html caption`() = runTest {
        var url = ""
        var contentType: String? = null
        val engine = MockEngine { request ->
            url = request.url.toString()
            contentType = request.body.contentType?.toString()
            respond("""{"ok":true}""", HttpStatusCode.OK)
        }
        TelegramBotApi(HttpClient(engine)).sendPhoto("123:abc", 51L, byteArrayOf(1, 2, 3), "<b>24 h</b>")

        assertTrue(url.endsWith("/bot123:abc/sendPhoto"))
        assertTrue(contentType.orEmpty().startsWith("multipart/form-data"))
    }

    @Test
    fun `edit message targets chat and message id`() = runTest {
        var url = ""
        var sent: String? = null
        api(HttpStatusCode.OK, """{"ok":true}""") { u, body -> url = u; sent = body }.editMessageText("123:abc", 51L, 700L, "y")

        assertTrue(url.endsWith("/editMessageText"))
        assertEquals("""{"chat_id":51,"message_id":700,"text":"y","parse_mode":"HTML"}""", sent)
    }

    @Test
    fun `command menu posts every command with its description`() = runTest {
        var url = ""
        var sent: String? = null
        api(HttpStatusCode.OK, """{"ok":true,"result":true}""") { u, body -> url = u; sent = body }
            .setMyCommands("123:abc", listOf("status" to "Cotas"))

        assertTrue(url.endsWith("/bot123:abc/setMyCommands"))
        assertEquals("""{"commands":[{"command":"status","description":"Cotas"}]}""", sent)
    }

    @Test
    fun `rate limit carries retry after and bad token is typed`() = runTest {
        val limited = assertFailsWith<TelegramRateLimitedException> {
            api(HttpStatusCode.TooManyRequests, """{"ok":false,"error_code":429,"parameters":{"retry_after":7}}""")
                .sendMessage("123:abc", 51L, "x")
        }
        assertEquals(7L, limited.retryAfterSeconds)
        assertFailsWith<TelegramUnauthorizedException> {
            api(HttpStatusCode.Unauthorized, """{"ok":false,"error_code":401}""").deleteWebhook("bad")
        }
    }
}
