package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.data.datasource.TelegramReplyKeyboard
import com.usagemonitor.data.datasource.TelegramIncomingMessage
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.presentation.ui.TelegramChart
import com.usagemonitor.presentation.ui.TelegramChartLine
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.botAccountTapData
import com.usagemonitor.domain.entity.TelegramBotPreferences
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.parseBotCommand
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

    val silentSends = Collections.synchronizedList(mutableListOf<String>())
    val pins = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
    val unpins = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
    val editedTexts = Collections.synchronizedList(mutableListOf<String>())

    val replyKeyboards = Collections.synchronizedList(mutableListOf<TelegramReplyKeyboard>())

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
        if (replyKeyboard != null) replyKeyboards += replyKeyboard
        keyboards += buttons.flatten()
        if (silent) silentSends += text
        return nextMessageId.incrementAndGet().toLong()
    }

    val photos = Collections.synchronizedList(mutableListOf<Pair<Long, Int>>())
    val documents = Collections.synchronizedList(mutableListOf<Pair<String, String>>())

    override suspend fun sendDocument(token: String, chatId: Long, bytes: ByteArray, fileName: String, contentType: String, caption: String) {
        documents += fileName to bytes.decodeToString()
    }

    override suspend fun sendPhoto(token: String, chatId: Long, png: ByteArray, caption: String) {
        photos += chatId to png.size
    }

    override suspend fun pinChatMessage(token: String, chatId: Long, messageId: Long) {
        pins += chatId to messageId
    }

    override suspend fun unpinChatMessage(token: String, chatId: Long, messageId: Long) {
        unpins += chatId to messageId
    }

    override suspend fun editMessageText(token: String, chatId: Long, messageId: Long, text: String, buttons: List<List<TelegramButton>>) {
        edits += chatId to messageId
        editedTexts += text
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
    private val focusRequests = AtomicInteger()
    private val applied = AtomicInteger()
    @Volatile private var readyVersion: String? = null
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
        toggleSource = { source, on -> enabledSources.value = if (on) enabledSources.value + source else enabledSources.value - source },
        chartProvider = { range ->
            if (range != HistoryRange.LAST_24_HOURS) {
                null
            } else {
                TelegramChart(0L, 3_600_000L, listOf(TelegramChartLine("Anthropic · Sessão 5h", listOf(0L to 10f, 3_600_000L to 20f), emptyList(), 20)))
            }
        },
        panelIntervalMillis = 50L,
        readings = TelegramBotReadings(
            focusHud = { focusRequests.incrementAndGet(); true },
            machineName = { "NOTE-EDI" },
            exportUsage = { since, format -> "$format desde $since" },
            readyUpdate = { readyVersion },
            applyUpdate = { applied.incrementAndGet() }
        ),
        updateCheckMillis = 50L,
        alertBatchMillis = 300L
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
        val names = api.menus.first().map { it.first }
        assertEquals("status", names.first())
        assertEquals("ajuda", names.last())
        // Todo item do menu é um comando que o bot conhece: nenhum cai no "Não entendi. Uso: /ajuda".
        names.forEach { name -> assertFalse(parseBotCommand("/$name") == BotCommand.Invalid("/ajuda"), name) }
        assertEquals(names.size, names.toSet().size)
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

    /** #408, AA22: `/sair` tira a conversa da lista, desafixa o painel dela e responde por último. */
    @Test
    fun `leave unpairs the chat, unpins its panel and answers once more`() {
        settings.value = settings.value.copy(
            authorizedChats = listOf(TelegramChat(51L, "@ed"), TelegramChat(52L, "@outra")),
            panelMessages = mapOf(51L to 900L)
        )
        service.start()
        api.pending += message(1, chat = 51, text = "/sair")

        waitUntil { !settings.value.isAuthorized(51L) }
        assertTrue(settings.value.isAuthorized(52L))
        assertEquals(emptyMap(), settings.value.panelMessages)
        waitUntil { api.sent.any { it.first == 51L && it.second.startsWith("👋 Pareamento desfeito.") } }
        assertTrue(51L to 900L in api.unpins)
    }

    /** #408, AA21: conversa só de leitura lê, mas não muda alertas nem silencia pelo botão. */
    @Test
    fun `read only chat reads but cannot change alerts`() {
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "Time", readOnly = true)))
        service.start()
        api.pending += message(1, chat = 51, text = "/alertas off")
        api.pending += tap(2, chat = 51, data = "snooze")
        api.pending += message(3, chat = 51, text = "/reinicios")

        waitUntil { api.sent.any { it.second.startsWith("🔒 Esta conversa é <b>só leitura</b>") } }
        waitUntil { "cb2" in api.answered }
        waitUntil { api.sent.size >= 2 }
        assertTrue(alertSettings.value.quotaAlertsEnabled)
        assertEquals(null, alertSettings.value.snoozedUntilEpochMillis)
    }

    /** #408, AA24: `/idioma en` vale só para esta conversa — o alerta sai em inglês nela e em português na outra. */
    @Test
    fun `chat language changes replies and alerts of that chat only`() {
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed"), TelegramChat(52L, "@outra")))
        service.start()
        api.pending += message(1, chat = 51, text = "/idioma en")

        waitUntil { api.sent.any { it.first == 51L && it.second == "🌐 This chat now speaks English." } }
        assertEquals(AppLanguage.EN, settings.value.authorizedChats.first { it.id == 51L }.language)
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }

        waitUntil { api.sent.any { it.first == 51L && it.second.startsWith("<b>CLI session saturated</b>") } }
        waitUntil { api.sent.any { it.first == 52L && it.second.startsWith("<b>Sessão CLI saturada</b>") } }
    }

    /** #408, AA30: `/teclado on` manda o teclado fixo, e tocar um botão dele é o comando. */
    @Test
    fun `keyboard command shows the persistent keyboard and its labels run commands`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/teclado on")

        waitUntil { api.replyKeyboards.isNotEmpty() }
        val shown = api.replyKeyboards.first() as TelegramReplyKeyboard.Show
        assertEquals(listOf(listOf("📊 Status", "👤 Conta"), listOf("📈 Gráfico", "⏳ Quando")), shown.rows)

        api.pending += message(2, chat = 51, text = "⏳ Quando")
        waitUntil { api.sent.any { it.second.startsWith("Sem leitura ainda.") } }
        api.pending += message(3, chat = 51, text = "/teclado off")
        waitUntil { TelegramReplyKeyboard.Remove in api.replyKeyboards }
    }

    /** #408, AA29: `/hud` só pede a HUD com "Permitir ações no PC" ligado. */
    @Test
    fun `hud command needs the pc actions permission`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/hud")
        waitUntil { api.sent.any { it.second.startsWith("🔒 Ações no PC pelo bot estão desligadas.") } }
        assertEquals(0, focusRequests.get())

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(allowPcActions = true))
        api.pending += message(2, chat = 51, text = "/hud")
        waitUntil { api.sent.any { it.second == "🖥 HUD trazida para a frente no PC." } }
        assertEquals(1, focusRequests.get())
    }

    /** #408, AA20: com a opção ligada, alerta e resposta dizem de que PC saíram; desligada, não. */
    @Test
    fun `machine name signs alerts and replies only when turned on`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/ping")
        waitUntil { api.sent.any { it.second.startsWith("🏓") } }
        assertTrue(api.sent.none { "💻" in it.second })

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(showMachineName = true))
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }
        waitUntil { api.sent.any { it.second.startsWith("<b>Sessão CLI saturada</b>") && it.second.endsWith("<i>💻 NOTE-EDI</i>") } }
        api.pending += message(2, chat = 51, text = "/reinicios")
        waitUntil { api.sent.count { it.second.endsWith("<i>💻 NOTE-EDI</i>") } >= 2 }
    }

    private fun quotaAlert(percent: Int) = UsageAlert.QuotaThreshold(
        target = UsageTargetKey(ApiSource.CODEX),
        targetLabel = "Padrão",
        quotaLabel = "Sessão 5h",
        thresholdPercent = percent,
        actualPercent = percent,
        periodEndAt = Instant.parse("2026-10-06T15:00:00Z"),
        hasKnownResetAt = true
    )

    /** #408, AA26: com "Só alerta crítico toca" (padrão), só a cota em 90% ou mais sai com som; desligado, todos tocam. */
    @Test
    fun `only critical alerts ring by default`() {
        paired()
        service.start()
        runBlocking { alerts.emit(quotaAlert(75)) }
        waitUntil { api.sent.size == 1 }
        assertEquals(1, api.silentSends.size)

        runBlocking { alerts.emit(quotaAlert(92)) }
        waitUntil { api.sent.size == 2 }
        assertEquals(1, api.silentSends.size)

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(soundOnlyOnCritical = false))
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }
        waitUntil { api.sent.size == 3 }
        assertEquals(1, api.silentSends.size)
    }

    /** #408, AA1: a repetição do mesmo projeto no dia edita a primeira mensagem; desligado, abre outra. */
    @Test
    fun `repeated alert edits the first message with a counter`() {
        paired()
        service.start()
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }
        waitUntil { api.sent.size == 1 }
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s2", "alem-das-sombras")) }

        waitUntil { api.editedTexts.any { "<b>2×</b> desde 09:00" in it } }
        assertEquals(1, api.sent.size)
        assertEquals(51L to 801L, api.edits.single())

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(groupRepeatedAlerts = false))
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s3", "alem-das-sombras")) }
        waitUntil { api.sent.size == 2 }
        assertEquals(1, api.edits.size)
    }

    /** #408, AA25: com o lote (padrão), os não críticos saem juntos numa mensagem; o crítico sai na hora. */
    @Test
    fun `non critical alerts arrive in one batch and critical goes out at once`() {
        paired()
        settings.value = settings.value.copy(preferences = TelegramBotPreferences(batchAlerts = true))
        service.start()
        runBlocking {
            alerts.emit(quotaAlert(75))
            alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras"))
            alerts.emit(UsageAlert.SessionSaturated("s2", "alem-das-sombras"))
            alerts.emit(quotaAlert(92))
        }

        waitUntil { api.sent.size == 2 }
        assertTrue(api.sent.first().second.contains("92%"))
        val batch = api.sent.last().second
        assertTrue(batch.startsWith("<b>🔔 3 alertas · 09:00–09:00</b>\n"))
        assertTrue("<b>Sessão CLI saturada</b> (2×)" in batch)
        assertEquals(1, batch.lines().count { it.startsWith("• <b>Padrão · Sessão 5h</b>:") })
        assertTrue(batch in api.silentSends)
    }

    /** #408, AA8: `/foco 2h` grava o prazo nas preferências de alerta e responde até quando; só leitura não pode. */
    @Test
    fun `focus command sets the deadline and answers until when`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/foco 2h")

        waitUntil { alertSettings.value.focusUntilEpochMillis != null }
        assertEquals(Instant.parse("2026-10-06T14:00:00Z").toEpochMilliseconds(), alertSettings.value.focusUntilEpochMillis)
        waitUntil { api.sent.any { it.second.startsWith("🎧 <b>Foco até 11:00</b>") } }
        api.pending += message(2, chat = 51, text = "/foco off")
        waitUntil { alertSettings.value.focusUntilEpochMillis == null }
    }

    /** #408, AA23: `/exportar 30d json` manda o arquivo com nome de data e recorte. */
    @Test
    fun `export sends the usage file with the period and format`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/exportar json 30d")

        waitUntil { api.documents.isNotEmpty() }
        val (name, content) = api.documents.single()
        assertEquals("usage-monitor-2026-10-06-30d.json", name)
        // 2026-10-06T12:00Z menos 30 dias.
        assertEquals("JSON desde ${Instant.parse("2026-09-06T12:00:00Z").toEpochMilliseconds()}", content)
    }

    /** #408, AA17: versão pronta é avisada uma vez com o botão; o toque reinicia só a versão avisada. */
    @Test
    fun `ready update is announced once and the tap restarts only with the permission`() {
        paired()
        readyVersion = "42.1.0"
        service.start()
        Thread.sleep(200)
        assertTrue(api.sent.none { it.second.startsWith("⬆") })

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(allowRemoteUpdate = true))
        waitUntil { api.sent.any { it.second.startsWith("⬆ <b>Usage Monitor 42.1.0 pronto para instalar</b>") } }
        assertEquals("upd:42.1.0", api.keyboards.last { it.isNotEmpty() }.single().data)
        assertEquals("42.1.0", settings.value.preferences.announcedUpdateVersion)
        Thread.sleep(200)
        assertEquals(1, api.sent.count { it.second.startsWith("⬆") })

        api.pending += tap(1, chat = 51, data = "upd:41.0.0")
        waitUntil { "cb1" in api.answered }
        assertEquals(0, applied.get())
        api.pending += tap(2, chat = 51, data = "upd:42.1.0")
        waitUntil { applied.get() == 1 }
    }

    /** #408, AA9: `/alertas` sem argumento abre um interruptor por tipo; o toque muda só aquele e redesenha o teclado. */
    @Test
    fun `alert menu toggles one kind and redraws its keyboard`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/alertas")

        waitUntil { api.keyboards.any { row -> row.any { it.data == "alt:session" } } }
        assertTrue(api.keyboards.last { it.isNotEmpty() }.any { it.text == "✅ Sessão saturada" })
        api.pending += tap(2, chat = 51, data = "alt:session")

        waitUntil { !alertSettings.value.sessionAlertsEnabled }
        assertTrue(alertSettings.value.quotaAlertsEnabled)
        waitUntil { api.edits.isNotEmpty() }
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

    /** #398, Y1: com a hora já passada, o resumo sai uma vez e a data fica gravada. */
    @Test
    fun `daily summary goes out once when its hour has passed`() {
        // O relógio do serviço marca 09:00 BRT (12:00 UTC); resumo às 08:00.
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed")), dailySummaryHour = 8)
        service.start()

        waitUntil { settings.value.lastSummaryDate == "2026-10-06" }
        waitUntil { api.sent.any { it.first == 51L && it.second.startsWith("Sem leitura ainda") } }
        assertEquals(1, api.sent.count { it.second.startsWith("Sem leitura ainda") })
    }

    @Test
    fun `summary command answers on demand`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/resumo")

        waitUntil { api.sent.any { it.first == 51L && it.second.startsWith("Sem leitura ainda") } }
    }

    /**
     * #398, Y5: ligado, o painel sai em silêncio e é fixado; com leitura nova é
     * editado; desligado, é desafixado e esquecido.
     */
    @Test
    fun `live panel is pinned, edited on a new reading and unpinned when turned off`() {
        fun reading(collectedAt: String, percent: Int) = UsageSnapshot(
            generatedAt = Instant.parse("2026-10-06T12:00:00Z"),
            accounts = listOf(
                UsageSnapshotAccount(
                    ApiSource.ANTHROPIC, "Claude — Edi", active = false,
                    fetchedAt = Instant.parse(collectedAt),
                    quotas = listOf(
                        UsageSnapshotQuota("Sessão 5h", PeriodType.INTERVAL, UsageUnit.PERCENTAGE, percent.toLong(), 100L, percent, null, "USD")
                    )
                )
            )
        )
        snapshot = reading("2026-10-06T11:58:00Z", 42)
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed")), livePanelEnabled = true)
        service.start()

        waitUntil { api.pins.isNotEmpty() && settings.value.panelMessages.isNotEmpty() }
        val messageId = settings.value.panelMessages.getValue(51L)
        assertEquals(51L to messageId, api.pins.single())
        assertTrue(api.silentSends.single().contains("42%"))
        // #408, AA15: o painel nasce em uma linha.
        assertTrue(api.silentSends.single().lines().size <= 2)

        snapshot = reading("2026-10-06T11:59:00Z", 57)
        waitUntil { api.editedTexts.any { it.contains("57%") } }
        assertEquals(1, api.pins.size)

        settings.value = settings.value.copy(livePanelEnabled = false)
        waitUntil { api.unpins.isNotEmpty() && settings.value.panelMessages.isEmpty() }
        assertEquals(51L to messageId, api.unpins.single())
    }

    /** #398, Y6: com histórico sai uma foto; sem histórico, a resposta é texto. */
    @Test
    fun `chart command sends a photo or says there is no history`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/grafico")
        api.pending += message(2, chat = 51, text = "/grafico 7d")

        waitUntil { api.photos.isNotEmpty() && api.sent.any { it.second.startsWith("Sem histórico no intervalo") } }
        assertTrue(api.photos.single().second > 0)
    }
}
