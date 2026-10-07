package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.data.datasource.TelegramIncomingMessage
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.botAccountTapData
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
import java.util.concurrent.atomic.AtomicInteger
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
    val keyboards = Collections.synchronizedList(mutableListOf<List<TelegramButton>>())
    private val nextMessageId = AtomicInteger(800)
    val edits = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
    val answered = Collections.synchronizedList(mutableListOf<String>())

    override suspend fun deleteWebhook(token: String) = Unit

    override suspend fun getMe(token: String): String = "usage_monitor_bot"

    override suspend fun getUpdates(token: String, offset: Long?, timeoutSeconds: Int): List<TelegramIncomingMessage> {
        if (offset == -1L) return emptyList()
        delay(20)
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        return batch
    }

    val menus = Collections.synchronizedList(mutableListOf<List<Pair<String, String>>>())

    override suspend fun sendMessage(token: String, chatId: Long, text: String, html: Boolean, buttons: List<List<TelegramButton>>, silent: Boolean): Long {
        sent += chatId to text
        keyboards += buttons.flatten()
        return nextMessageId.incrementAndGet().toLong()
    }

    override suspend fun editMessageText(token: String, chatId: Long, messageId: Long, text: String, buttons: List<List<TelegramButton>>) {
        edits += chatId to messageId
    }

    override suspend fun answerCallbackQuery(token: String, callbackId: String, text: String?) {
        answered += callbackId
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
    private val refreshes = AtomicInteger()
    @Volatile private var snapshot: UsageSnapshot? = null
    private val enabledSources = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
    private val service = TelegramBotService(
        api = api,
        settingsFlow = settings,
        saveSettings = {},
        alertSettingsFlow = alertSettings,
        saveAlertSettings = { updated -> alertSettings.value = updated },
        alerts = alerts,
        snapshotProvider = { snapshot },
        languageProvider = { AppLanguage.PT },
        requestRefresh = { refreshes.incrementAndGet() },
        clock = object : Clock { override fun now() = Instant.parse("2026-10-06T12:00:00Z") },
        enabledSources = { enabledSources.value },
        toggleSource = { source, on -> enabledSources.value = if (on) enabledSources.value + source else enabledSources.value - source }
    )

    @AfterTest
    fun tearDown() = service.onDestroy()

    private fun message(id: Long, chat: Long, text: String) = TelegramIncomingMessage(id, chat, "@chat$chat", text)

    private fun tap(id: Long, chat: Long, data: String) =
        TelegramIncomingMessage(id, chat, "@chat$chat", text = null, callbackId = "cb$id", callbackData = data, messageId = 700L)

    private fun paired() {
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed")))
    }

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
        assertEquals(listOf("status", "conta", "atualizar", "api", "alertas", "silencio", "limiar", "ajuda"), api.menus.first().map { it.first })
    }

    @Test
    fun `status reply carries the refresh, mute and thresholds buttons`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/status")

        waitUntil { api.keyboards.any { it.isNotEmpty() } }
        assertEquals(listOf("refresh", "snooze", "thresholds"), api.keyboards.first { it.isNotEmpty() }.map { it.data })
    }

    /** #398, Y4: o `/conta` oferece uma conta por botão, e o toque manda só ela. */
    @Test
    fun `account command lists the accounts and a tap shows only that one`() {
        val reading = UsageSnapshot(
            generatedAt = Instant.parse("2026-10-06T12:00:00Z"),
            accounts = listOf(
                UsageSnapshotAccount(ApiSource.ANTHROPIC, "Claude — Edi", active = false, quotas = emptyList()),
                UsageSnapshotAccount(ApiSource.CODEX, "Codex", active = false, quotas = emptyList())
            )
        )
        snapshot = reading
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/conta")

        waitUntil { api.sent.any { it.second == "Qual conta?" } }
        val codexTap = botAccountTapData(reading.accounts[1])
        assertEquals(listOf(botAccountTapData(reading.accounts[0]), codexTap), api.keyboards.last().map { it.data })

        api.pending += tap(2, chat = 51, data = codexTap)
        waitUntil { api.sent.any { it.second.startsWith("<b>Codex</b>") } }
        assertTrue(api.sent.none { it.second.startsWith("<b>Claude — Edi</b>") })
    }

    @Test
    fun `mute button silences alerts for one hour`() {
        paired()
        service.start()
        api.pending += tap(1, chat = 51, data = "snooze")

        waitUntil { alertSettings.value.snoozedUntilEpochMillis != null }
        assertEquals(Instant.parse("2026-10-06T13:00:00Z").toEpochMilliseconds(), alertSettings.value.snoozedUntilEpochMillis)
        waitUntil { "cb1" in api.answered && api.sent.any { it.second.startsWith("🔕 Alertas silenciados até") } }
    }

    @Test
    fun `refresh button collects and edits the same message`() {
        paired()
        service.start()
        api.pending += tap(1, chat = 51, data = "refresh")

        waitUntil { api.edits.isNotEmpty() }
        assertEquals(1, refreshes.get())
        assertEquals(51L to 700L, api.edits.single())
    }

    @Test
    fun `a stranger tapping a button gets nothing`() {
        service.start()
        api.pending += tap(1, chat = 99, data = "snooze")
        api.pending += message(2, chat = 51, text = "/start ab12cd")

        waitUntil { settings.value.isAuthorized(51L) }
        assertTrue(api.answered.isEmpty())
        assertEquals(null, alertSettings.value.snoozedUntilEpochMillis)
    }

    @Test
    fun `connected status carries the bot username for the deep link`() {
        service.start()

        waitUntil { (service.status.value as? TelegramBotStatus.Connected)?.botUsername == "usage_monitor_bot" }
    }

    /** #398, Y8: sem a permissão o `/api` só lista, e um toque antigo não muda nada. */
    @Test
    fun `api lists without buttons and refuses taps while source control is off`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/api")
        api.pending += tap(2, chat = 51, data = "api:DEEPSEEK")

        waitUntil { api.sent.any { it.second.startsWith("<b>Fontes monitoradas</b>") } && "cb2" in api.answered }
        assertTrue(api.keyboards.last().isEmpty())
        assertEquals(setOf(ApiSource.ANTHROPIC), enabledSources.value)
    }

    @Test
    fun `api tap toggles the source when the user allowed it`() {
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed")), allowSourceControl = true)
        service.start()
        api.pending += tap(1, chat = 51, data = "api:DEEPSEEK")

        waitUntil { ApiSource.DEEPSEEK in enabledSources.value }
        waitUntil { api.edits.isNotEmpty() && api.sent.any { it.second.contains("DeepSeek</b> ligada") } }
    }

    /** `/silencio` sem argumento oferece durações; "4 h" grava o silêncio a partir do toque. */
    @Test
    fun `quiet menu offers durations and a tap mutes for that long`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/silencio")
        waitUntil { api.keyboards.any { row -> row.map { it.data } == listOf("snz:60", "snz:240", "snz:am") } }

        api.pending += tap(2, chat = 51, data = "snz:240")
        waitUntil { alertSettings.value.snoozedUntilEpochMillis != null }
        assertEquals(Instant.parse("2026-10-06T16:00:00Z").toEpochMilliseconds(), alertSettings.value.snoozedUntilEpochMillis)
    }

    @Test
    fun `refresh command collects and answers with the status`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/atualizar")

        waitUntil { refreshes.get() == 1 && api.sent.any { it.second.startsWith("Sem leitura ainda") } }
    }
}
