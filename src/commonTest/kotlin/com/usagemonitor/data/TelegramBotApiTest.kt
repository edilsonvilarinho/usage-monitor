package com.usagemonitor.data

import com.usagemonitor.data.datasource.TelegramBotApi
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
