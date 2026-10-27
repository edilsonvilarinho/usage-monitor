package com.usagemonitor

import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TELEGRAM_PAIRING_TTL_MILLIS
import com.usagemonitor.domain.entity.TelegramBotPreferences
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.presentation.ui.components.AppTone
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class TelegramBotActionsTest {

    private val root: File = Files.createTempDirectory("telegram-actions-").toFile()
    private val file = File(root, "telegram.json")
    private val dataSource = LocalTelegramSettingsDataSource(file)
    private val flow = MutableStateFlow(TelegramBotSettings())
    private val requests = CopyOnWriteArrayList<String>()
    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = this@TelegramBotActionsTest.now
    }
    private val service = TelegramBotService(
        api = TelegramBotApi(
            HttpClient(
                MockEngine { request ->
                    requests += request.url.encodedPath
                    respond(
                        """{"ok":true,"result":{"message_id":1}}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            )
        ),
        settingsFlow = flow,
        saveSettings = {},
        alertSettingsFlow = MutableStateFlow(UsageAlertSettings.DEFAULT),
        saveAlertSettings = {},
        alerts = MutableSharedFlow(),
        snapshotProvider = { null },
        languageProvider = { AppLanguage.PT }
    )
    private val actions = TelegramBotActions(dataSource, flow, service, clock)

    @AfterTest
    fun cleanUp() {
        service.onDestroy()
        root.deleteRecursively()
    }

    @Test
    fun `every change is published and written to the secret file`() {
        actions.setEnabled(true)
        actions.changeToken("  123:abc  ")
        actions.setAllowSourceControl(true)
        actions.setLivePanel(true)
        actions.setPreferences(TelegramBotPreferences(showTeam = true))

        val saved = dataSource.load()
        assertTrue(flow.value.enabled)
        assertEquals("123:abc", flow.value.botToken)
        assertTrue(saved.enabled)
        assertEquals("123:abc", saved.botToken)
        assertTrue(saved.allowSourceControl)
        assertTrue(saved.livePanelEnabled)
        assertTrue(saved.preferences.showTeam)
    }

    @Test
    fun `a pairing code is opened for the pairing TTL from now`() {
        actions.startPairing()

        val code = flow.value.pairingCode
        assertEquals(6, code?.length)
        assertEquals(now.toEpochMilliseconds() + TELEGRAM_PAIRING_TTL_MILLIS, flow.value.pairingExpiresAtMillis)
        assertEquals(code, dataSource.load().pairingCode)
    }

    @Test
    fun `chats are removed and switched between control and read only by id`() {
        flow.value = TelegramBotSettings(authorizedChats = listOf(TelegramChat(1, "a"), TelegramChat(2, "b")))

        actions.setChatControl(chatId = 2, control = false)
        assertEquals(listOf(false, true), flow.value.authorizedChats.map { chat -> chat.readOnly })
        actions.setChatControl(chatId = 2, control = true)
        assertFalse(flow.value.authorizedChats[1].readOnly)

        actions.removeChat(1)
        assertEquals(listOf(2L), flow.value.authorizedChats.map { chat -> chat.id })
    }

    @Test
    fun `changing the summary hour re-arms today's summary`() {
        flow.value = TelegramBotSettings(dailySummaryHour = 8, lastSummaryDate = "2026-10-27")

        actions.setDailySummaryHour(18)

        assertEquals(18, flow.value.dailySummaryHour)
        assertNull(flow.value.lastSummaryDate)
    }

    @Test
    fun `a write failure still updates the screen`() {
        // O "arquivo" é uma pasta: a gravação falha e a mudança vale até o reinício.
        file.mkdirs()

        actions.setEnabled(true)

        assertTrue(flow.value.enabled)
    }

    @Test
    fun `the test message goes to every paired chat`() {
        flow.value = TelegramBotSettings(enabled = true, botToken = "123:abc", authorizedChats = listOf(TelegramChat(42, "me")))

        actions.sendTest()

        val deadline = System.currentTimeMillis() + 5_000
        while (requests.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
        }
        assertEquals(listOf("/bot123:abc/sendMessage"), requests.toList())
    }

    @Test
    fun `the section shows each bot status with its tone`() {
        fun model(status: TelegramBotStatus, language: AppLanguage = AppLanguage.PT) =
            telegramBotSectionModel(TelegramBotSettings(), status, null, language, actions, nowMillis = 0)

        assertEquals("Desligado" to AppTone.NEUTRAL, model(TelegramBotStatus.Off).let { it.statusLabel to it.statusTone })
        assertEquals("Connecting" to AppTone.INFO, model(TelegramBotStatus.Connecting, AppLanguage.EN).let { it.statusLabel to it.statusTone })
        assertEquals(
            "Conectado como @meubot · sem conversa pareada" to AppTone.WARNING,
            model(TelegramBotStatus.Connected(chatCount = 0, botUsername = "meubot")).let { it.statusLabel to it.statusTone }
        )
        assertEquals(
            "Connected · 2 chat(s)" to AppTone.OK,
            model(TelegramBotStatus.Connected(chatCount = 2), AppLanguage.EN).let { it.statusLabel to it.statusTone }
        )
        assertEquals("Falha: timeout" to AppTone.CRITICAL, model(TelegramBotStatus.Failed("timeout")).let { it.statusLabel to it.statusTone })
        assertEquals(
            "Failed: the Telegram bot token was rejected." to AppTone.CRITICAL,
            model(TelegramBotStatus.TokenRejected, AppLanguage.EN).let { it.statusLabel to it.statusTone }
        )
        assertTrue(model(TelegramBotStatus.Connected(chatCount = 1, botUsername = "x")).connected)
        assertEquals("x", model(TelegramBotStatus.Connected(chatCount = 1, botUsername = "x")).botUsername)
        assertFalse(model(TelegramBotStatus.Off).connected)
    }

    @Test
    fun `an open pairing code shows the minutes left and an expired one disappears`() {
        val expiresAt = 10 * 60_000L
        val settings = TelegramBotSettings(pairingCode = "ABC234", pairingExpiresAtMillis = expiresAt)

        val open = telegramBotSectionModel(settings, TelegramBotStatus.Off, null, AppLanguage.PT, actions, nowMillis = 30_000L)
        assertEquals("ABC234", open.pairingCode)
        assertEquals("Vale por mais 10 min.", open.pairingHint)
        val english = telegramBotSectionModel(settings, TelegramBotStatus.Off, null, AppLanguage.EN, actions, nowMillis = expiresAt - 1)
        assertEquals("Valid for 1 more min.", english.pairingHint)

        val expired = telegramBotSectionModel(settings, TelegramBotStatus.Off, null, AppLanguage.PT, actions, nowMillis = expiresAt)
        assertNull(expired.pairingCode)
        assertNull(expired.pairingHint)
    }

    @Test
    fun `the section callbacks are the actions`() {
        val model = telegramBotSectionModel(
            TelegramBotSettings(), TelegramBotStatus.Off, null, AppLanguage.PT, actions, nowMillis = 0, machineName = "pc-1"
        )

        model.onEnabledChange(true)
        model.onTokenChange(" t ")
        model.onAllowSourceControlChange(true)
        model.onDailySummaryHourChange(9)
        model.onLivePanelChange(true)

        assertEquals("pc-1", model.machineName)
        assertTrue(flow.value.enabled)
        assertEquals("t", flow.value.botToken)
        assertTrue(flow.value.allowSourceControl)
        assertEquals(9, flow.value.dailySummaryHour)
        assertTrue(flow.value.livePanelEnabled)
    }
}
