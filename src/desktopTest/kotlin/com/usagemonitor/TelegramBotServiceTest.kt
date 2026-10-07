package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramIncomingMessage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** API falsa: entrega cada lote de mensagens uma vez e grava o que foi enviado. */
private class FakeTelegramApi : TelegramBotApi(HttpClient(MockEngine { respond("") })) {
    val pending = Collections.synchronizedList(mutableListOf<TelegramIncomingMessage>())
    val sent = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())

    override suspend fun deleteWebhook(token: String) = Unit

    override suspend fun getUpdates(token: String, offset: Long?, timeoutSeconds: Int): List<TelegramIncomingMessage> {
        if (offset == -1L) return emptyList()
        delay(20)
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        return batch
    }

    val menus = Collections.synchronizedList(mutableListOf<List<Pair<String, String>>>())

    override suspend fun sendMessage(token: String, chatId: Long, text: String, html: Boolean) {
        sent += chatId to text
    }

    override suspend fun setMyCommands(token: String, commands: List<Pair<String, String>>) {
        menus += commands
    }
}

class TelegramBotServiceTest {

    private val api = FakeTelegramApi()
    private val settings = MutableStateFlow(
        TelegramBotSettings(enabled = true, botToken = "123:abc", pairingCode = "AB12CD", pairingExpiresAtMillis = Long.MAX_VALUE)
    )
    private val alertSettings = MutableStateFlow(UsageAlertSettings())
    private val alerts = MutableSharedFlow<UsageAlert>(extraBufferCapacity = 4)
    private val service = TelegramBotService(
        api = api,
        settingsFlow = settings,
        saveSettings = {},
        alertSettingsFlow = alertSettings,
        saveAlertSettings = { updated -> alertSettings.value = updated },
        alerts = alerts,
        snapshotProvider = { null },
        languageProvider = { AppLanguage.PT },
        clock = object : Clock { override fun now() = Instant.parse("2026-10-06T12:00:00Z") }
    )

    @AfterTest
    fun tearDown() = service.onDestroy()

    private fun message(id: Long, chat: Long, text: String) = TelegramIncomingMessage(id, chat, "@chat$chat", text)

    private fun waitUntil(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) delay(20) }
    }

    @Test
    fun `pairing code authorizes the chat and stranger commands are ignored`() {
        service.start()
        api.pending += message(1, chat = 99, text = "/status")
        api.pending += message(2, chat = 51, text = "/start ab12cd")

        waitUntil { settings.value.isAuthorized(51L) }

        assertFalse(settings.value.isAuthorized(99L))
        assertEquals(null, settings.value.pairingCode)
        waitUntil { api.sent.any { it.first == 51L } }
        assertTrue(api.sent.none { it.first == 99L })
    }

    @Test
    fun `authorized chat changes alert settings by command`() {
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed")))
        service.start()
        api.pending += message(1, chat = 51, text = "/alertas off")

        waitUntil { !alertSettings.value.quotaAlertsEnabled }
        waitUntil { api.sent.any { it.second == "🔕 Alertas desligados." } }
    }

    @Test
    fun `connecting registers the command menu in the app language`() {
        service.start()

        waitUntil { api.menus.isNotEmpty() }
        assertEquals(listOf("status", "alertas", "silencio", "limiar", "ajuda"), api.menus.first().map { it.first })
    }
}
