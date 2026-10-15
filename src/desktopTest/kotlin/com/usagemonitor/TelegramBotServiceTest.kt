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
import com.usagemonitor.domain.entity.SourceHealthObservation
import com.usagemonitor.domain.entity.botAccountTapData
import com.usagemonitor.domain.entity.TelegramBotPreferences
import com.usagemonitor.domain.entity.TELEGRAM_KEYBOARD_LAYOUT
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramPendingRestart
import com.usagemonitor.domain.entity.ReleaseNotes
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * API falsa: entrega cada lote de mensagens uma vez e grava o que foi enviado.
 *
 * As listas são `CopyOnWriteArrayList`, e não `Collections.synchronizedList`: o teste as
 * percorre (`api.sent.any { … }`) enquanto o serviço grava em outra thread, e percorrer a
 * lista sincronizada sem segurar o lock dava `ConcurrentModificationException` no CI.
 *
 * [pending] é fila com retirada atômica (#412): o lote antigo era `toList()` seguido de
 * `clear()`, e o `+=` do teste não passava pelo lock — mensagem enfileirada entre os dois
 * era apagada sem ser entregue, e o `waitUntil` seguinte estourava.
 */
private class FakeTelegramApi : TelegramBotApi(HttpClient(MockEngine { respond("") })) {
    val pending = ConcurrentLinkedQueue<TelegramIncomingMessage>()
    val sent = CopyOnWriteArrayList<Pair<Long, String>>()
    val keyboards = CopyOnWriteArrayList<List<TelegramButton>>()
    private val nextMessageId = AtomicInteger(800)
    val edits = CopyOnWriteArrayList<Pair<Long, Long>>()
    val answered = CopyOnWriteArrayList<String>()

    override suspend fun deleteWebhook(token: String) = Unit

    override suspend fun getMe(token: String): String = "usage_monitor_bot"

    override suspend fun getUpdates(token: String, offset: Long?, timeoutSeconds: Int): List<TelegramIncomingMessage> {
        if (offset == -1L) return emptyList()
        delay(20)
        return generateSequence { pending.poll() }.toList()
    }

    val menus = CopyOnWriteArrayList<List<Pair<String, String>>>()

    val silentSends = CopyOnWriteArrayList<String>()
    val pins = CopyOnWriteArrayList<Pair<Long, Long>>()
    val unpins = CopyOnWriteArrayList<Pair<Long, Long>>()
    val editedTexts = CopyOnWriteArrayList<String>()

    val replyKeyboards = CopyOnWriteArrayList<TelegramReplyKeyboard>()

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

    val photos = CopyOnWriteArrayList<Pair<Long, Int>>()
    val documents = CopyOnWriteArrayList<Pair<String, String>>()

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
        TelegramBotSettings(
            enabled = true, botToken = "123:abc", pairingCode = "AB12CD", pairingExpiresAtMillis = Long.MAX_VALUE,
            // O teclado da rodada AE já entregue: o envio único tem teste próprio e não entra nas contas dos outros.
            preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT)
        )
    )
    private val alertSettings = MutableStateFlow(UsageAlertSettings())
    private val alerts = MutableSharedFlow<UsageAlert>(extraBufferCapacity = 4)
    private val refreshes = AtomicInteger()
    private val focusRequests = AtomicInteger()
    private val applied = AtomicInteger()
    @Volatile private var lastSaved: TelegramBotSettings? = null
    /** O reinício pendente já gravado no instante em que o app fecharia (#417). */
    @Volatile private var pendingSavedAtApply: TelegramPendingRestart? = null
    @Volatile private var runningVersion = "42.0.0"
    /** A janela e o ACK (#417): o bot só confirma o reinício depois deles. */
    private val anchored = MutableStateFlow(true)
    @Volatile private var releaseNotesResult: Result<ReleaseNotes?> = Result.success(null)
    private val releaseNotesRequests = CopyOnWriteArrayList<Pair<String, String?>>()
    @Volatile private var readyVersion: String? = null
    @Volatile private var snapshot: UsageSnapshot? = null
    @Volatile private var appLanguage = AppLanguage.PT
    @Volatile private var sourceHealth: List<SourceHealthObservation>? = null
    private val enabledSources = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
    private val service = TelegramBotService(
        api = api,
        settingsFlow = settings,
        saveSettings = { saved -> lastSaved = saved },
        alertSettingsFlow = alertSettings,
        saveAlertSettings = { updated -> alertSettings.value = updated },
        alerts = alerts,
        snapshotProvider = { snapshot },
        languageProvider = { appLanguage },
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
            applyUpdate = {
                pendingSavedAtApply = lastSaved?.preferences?.pendingRestart
                applied.incrementAndGet()
            },
            currentVersion = { runningVersion },
            appReady = { anchored.first { ready -> ready } },
            releaseNotes = { version, previous ->
                releaseNotesRequests += version to previous
                releaseNotesResult
            },
            sourceHealth = { sourceHealth },
            teamPresence = {
                Result.success(
                    listOf(
                        TeamMemberPresence(
                            member = TeamMemberUsage(deviceId = "d1", alias = "joão", lastSeenAt = Instant.parse("2026-10-06T10:00:00Z")),
                            isOnline = false,
                            isWorkingNow = false,
                            activeSessionCount = 0
                        )
                    )
                )
            },
            // O servidor está 1 h atrás deste PC.
            teamClockOffsetMillis = { -3_600_000L },
            sessionAnalysis = { key -> if (key == "ANTHROPIC:work") com.usagemonitor.presentation.AnalysisFixture.analysis else null }
        ),
        updateCheckMillis = 50L,
        alertBatchMillis = 300L,
        sourceHealthCheckMillis = 50L,
        sourceBrokenAfterMillis = 0L
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

    /**
     * Liga o serviço e só devolve com o coletor de alertas inscrito (#412).
     *
     * `start()` lança o `collect` numa corrotina de `Dispatchers.IO`, e o fluxo
     * de alertas não tem `replay`: emitir antes da inscrição descarta o alerta, e
     * o `waitUntil` seguinte estoura sem dizer por quê. Todo teste que emite
     * alerta liga o serviço por aqui.
     */
    private fun startAndAwaitAlertCollector() {
        service.start()
        waitUntil { alerts.subscriptionCount.value > 0 }
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

    /** B02: o idioma do app manda — trocá-lo registra o menu de novo, e os alertas seguem o idioma novo. */
    @Test
    fun `menu and alerts follow the app language when it changes`() {
        settings.value = settings.value.copy(authorizedChats = listOf(TelegramChat(51L, "@ed"), TelegramChat(52L, "@outra")))
        startAndAwaitAlertCollector()
        waitUntil { api.menus.isNotEmpty() }
        assertEquals("teclado", api.menus.first()[1].first)

        appLanguage = AppLanguage.EN
        waitUntil { api.menus.size >= 2 }
        assertEquals("keyboard", api.menus.last()[1].first)
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }
        waitUntil { api.sent.count { it.second.startsWith("<b>CLI session saturated</b>") } == 2 }
    }

    /** #408, AA30 + rodada AE, AE4: `/teclado on` manda a página principal, e tocar um botão dele é o comando. */
    @Test
    fun `keyboard command shows the persistent keyboard and its labels run commands`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/teclado on")

        waitUntil { api.replyKeyboards.isNotEmpty() }
        val shown = api.replyKeyboards.first() as TelegramReplyKeyboard.Show
        assertEquals(listOf(listOf("📊 Cotas", "👤 Conta", "🔄 Coletar"), listOf("🔬 Análise", "🔔 Alertas", "☰ Mais")), shown.rows)

        api.pending += message(2, chat = 51, text = "📊 Cotas")
        waitUntil { api.sent.any { it.second.startsWith("Sem leitura ainda.") } }
        api.pending += message(3, chat = 51, text = "/teclado off")
        waitUntil { TelegramReplyKeyboard.Remove in api.replyKeyboards }
    }

    /** Rodada AE, AE4: "☰ Mais" troca a página; HUD e Time só aparecem com a permissão; "‹ Voltar" volta. */
    @Test
    fun `more and back switch the keyboard page and permissions decide the extra keys`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "☰ Mais")

        waitUntil { api.replyKeyboards.isNotEmpty() }
        assertEquals(
            listOf(listOf("⏰ Reinícios", "⏳ Quando", "🏁 Qual"), listOf("📏 Ritmo", "📉 Gráfico", "📝 Resumo"), listOf("‹ Voltar")),
            (api.replyKeyboards.first() as TelegramReplyKeyboard.Show).rows
        )
        assertTrue(api.sent.any { it.second == "☰ Mais atalhos." })

        settings.value = settings.value.copy(preferences = settings.value.preferences.copy(allowPcActions = true, showTeam = true))
        api.pending += message(2, chat = 51, text = "☰ Mais")
        waitUntil { api.replyKeyboards.size == 2 }
        assertEquals(listOf("🖥 HUD", "👥 Time", "‹ Voltar"), (api.replyKeyboards[1] as TelegramReplyKeyboard.Show).rows.last())

        api.pending += message(3, chat = 51, text = "‹ Voltar")
        waitUntil { api.replyKeyboards.size == 3 }
        assertEquals("📊 Cotas", (api.replyKeyboards[2] as TelegramReplyKeyboard.Show).rows.first().first())
        // O teclado da AA30 ainda no celular continua valendo.
        api.pending += message(4, chat = 51, text = "📊 Status")
        waitUntil { api.sent.any { it.second.startsWith("Sem leitura ainda.") } }
    }

    /** Rodada AE, AE4: o pareamento já entrega o teclado; nasce ligado. */
    @Test
    fun `pairing sends the keyboard with the confirmation`() {
        service.start()
        api.pending += message(1, chat = 51, text = "/start ab12cd")

        waitUntil { api.replyKeyboards.isNotEmpty() }
        assertTrue(api.sent.single().second.startsWith("✅ <b>Conversa pareada.</b>"), api.sent.toString())
        assertEquals("📊 Cotas", (api.replyKeyboards.single() as TelegramReplyKeyboard.Show).rows.first().first())
    }

    /**
     * Rodada AE, AE4: conversa pareada antes do teclado novo o recebe uma vez, em silêncio,
     * e a marca é gravada antes do envio — reconectar não repete.
     */
    @Test
    fun `chats paired before the new keyboard receive it once`() {
        settings.value = settings.value.copy(
            authorizedChats = listOf(TelegramChat(51L, "@ed"), TelegramChat(52L, "@time")),
            preferences = settings.value.preferences.copy(keyboardLayout = 0)
        )
        service.start()

        waitUntil { api.replyKeyboards.size == 2 }
        assertEquals(TELEGRAM_KEYBOARD_LAYOUT, settings.value.preferences.keyboardLayout)
        assertEquals(TELEGRAM_KEYBOARD_LAYOUT, lastSaved?.preferences?.keyboardLayout)
        assertEquals(setOf(51L, 52L), api.sent.map { it.first }.toSet())
        assertTrue(api.silentSends.all { it.startsWith("⌨ <b>Atalhos novos</b>") } && api.silentSends.size == 2)

        settings.value = settings.value.copy(botToken = "456:def")
        waitUntil { api.menus.size == 2 }
        assertEquals(2, api.replyKeyboards.size)
    }

    /**
     * `lastSeenAt` é carimbo do servidor: o "visto há" conta do agora do servidor
     * (relógio local 12:00 menos 1 h de desvio = 11:00), não do relógio local.
     */
    @Test
    fun `team command measures last seen against the server clock`() {
        paired()
        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, showTeam = true))
        service.start()
        api.pending += message(1, chat = 51, text = "/time")
        waitUntil { api.sent.any { it.second.startsWith("<b>👥 Time agora</b>") } }
        assertTrue(api.sent.any { it.second.endsWith("⚪ <b>joão</b> — visto há 1 h") })
    }

    /** #408, AA29: `/hud` só pede a HUD com "Permitir ações no PC" ligado. */
    @Test
    fun `hud command needs the pc actions permission`() {
        paired()
        service.start()
        api.pending += message(1, chat = 51, text = "/hud")
        waitUntil { api.sent.any { it.second.startsWith("🔒 Ações no PC pelo bot estão desligadas.") } }
        assertEquals(0, focusRequests.get())

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, allowPcActions = true))
        api.pending += message(2, chat = 51, text = "/hud")
        waitUntil { api.sent.any { it.second == "🖥 HUD trazida para a frente no PC." } }
        assertEquals(1, focusRequests.get())
    }

    /** #408, AA20: com a opção ligada, alerta e resposta dizem de que PC saíram; desligada, não. */
    @Test
    fun `machine name signs alerts and replies only when turned on`() {
        paired()
        startAndAwaitAlertCollector()
        api.pending += message(1, chat = 51, text = "/ping")
        waitUntil { api.sent.any { it.second.startsWith("🏓") } }
        assertTrue(api.sent.none { "💻" in it.second })

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, showMachineName = true))
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
        startAndAwaitAlertCollector()
        runBlocking { alerts.emit(quotaAlert(75)) }
        waitUntil { api.sent.size == 1 }
        assertEquals(1, api.silentSends.size)

        runBlocking { alerts.emit(quotaAlert(92)) }
        waitUntil { api.sent.size == 2 }
        assertEquals(1, api.silentSends.size)

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, soundOnlyOnCritical = false))
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }
        waitUntil { api.sent.size == 3 }
        assertEquals(1, api.silentSends.size)
    }

    /** #408, AA1: a repetição do mesmo projeto no dia edita a primeira mensagem; desligado, abre outra. */
    @Test
    fun `repeated alert edits the first message with a counter`() {
        paired()
        startAndAwaitAlertCollector()
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s1", "alem-das-sombras")) }
        waitUntil { api.sent.size == 1 }
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s2", "alem-das-sombras")) }

        waitUntil { api.editedTexts.any { "<b>2×</b> desde 09:00" in it } }
        assertEquals(1, api.sent.size)
        assertEquals(51L to 801L, api.edits.single())

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, groupRepeatedAlerts = false))
        runBlocking { alerts.emit(UsageAlert.SessionSaturated("s3", "alem-das-sombras")) }
        waitUntil { api.sent.size == 2 }
        assertEquals(1, api.edits.size)
    }

    /** #408, AA25: com o lote (padrão), os não críticos saem juntos numa mensagem; o crítico sai na hora. */
    @Test
    fun `non critical alerts arrive in one batch and critical goes out at once`() {
        paired()
        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, batchAlerts = true))
        startAndAwaitAlertCollector()
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

    /** #408, AA18: a fonte quebrada avisa uma vez com o motivo escapado; ao voltar, avisa que voltou. */
    @Test
    fun `broken source warns once and again when it is back`() {
        paired()
        service.start()
        sourceHealth = listOf(SourceHealthObservation("cursor", "Cursor", failure = "HTTP 401 <recusado>", lastReadingAt = Instant.parse("2026-10-06T11:50:00Z")))

        waitUntil { api.sent.any { it.second.startsWith("⚠ <b>Cursor parou de atualizar</b>") } }
        val broken = api.sent.single { it.second.startsWith("⚠") }.second
        assertTrue("HTTP 401 &lt;recusado&gt;" in broken)
        assertTrue(broken.endsWith("Os números mostrados são de 08:50."))
        Thread.sleep(200)
        assertEquals(1, api.sent.count { it.second.startsWith("⚠") })

        sourceHealth = listOf(SourceHealthObservation("cursor", "Cursor", failure = null))
        waitUntil { api.sent.any { it.second == "✅ <b>Cursor voltou</b> — coleta 09:00." } }
    }

    /** #408, AA10: `/limiar` → toque na conta → "50,80" grava o limiar só dela; "geral" a devolve. */
    @Test
    fun `threshold per account asks the account and takes the numbers`() {
        paired()
        snapshot = UsageSnapshot(
            generatedAt = Instant.parse("2026-10-06T12:00:00Z"),
            accounts = listOf(
                UsageSnapshotAccount(
                    ApiSource.ANTHROPIC, "Anthropic · Trabalho", active = false,
                    quotas = listOf(UsageSnapshotQuota("Sessão 5h", PeriodType.INTERVAL, UsageUnit.PERCENTAGE, 40, 100, 40, null, "USD")),
                    targetKey = "ANTHROPIC:work"
                )
            )
        )
        service.start()
        api.pending += message(1, chat = 51, text = "/limiar")
        waitUntil { api.keyboards.any { row -> row.any { it.text == "Anthropic · Trabalho" } } }
        val data = api.keyboards.last { it.isNotEmpty() }.first().data

        api.pending += tap(2, chat = 51, data = data)
        waitUntil { api.sent.any { it.second.startsWith("Limiar de <b>Anthropic · Trabalho</b>") } }
        api.pending += message(3, chat = 51, text = "50,80")
        waitUntil { alertSettings.value.quotaPercentsByTarget.isNotEmpty() }
        assertEquals(listOf(50, 80), alertSettings.value.quotaPercentsByTarget.getValue("ANTHROPIC:work").percents)
        assertEquals(UsageAlertSettings().quotaPercents, alertSettings.value.quotaPercents)

        api.pending += message(4, chat = 51, text = "geral")
        Thread.sleep(200)
        assertEquals(1, alertSettings.value.quotaPercentsByTarget.size)
        api.pending += tap(5, chat = 51, data = data)
        waitUntil { api.sent.count { it.second.startsWith("Limiar de <b>Anthropic · Trabalho</b>") } == 2 }
        api.pending += message(6, chat = 51, text = "geral")
        waitUntil { alertSettings.value.quotaPercentsByTarget.isEmpty() }
    }

    /** Rodada AB: com uma conta analisável, `/analise` abre o painel com as abas; a aba edita a mesma mensagem. */
    @Test
    fun `analysis opens the panel with tabs and a tab edits the message`() {
        paired()
        snapshot = UsageSnapshot(
            generatedAt = Instant.parse("2026-10-06T12:00:00Z"),
            accounts = listOf(
                UsageSnapshotAccount(
                    ApiSource.ANTHROPIC, "Anthropic · Trabalho", active = false,
                    quotas = listOf(UsageSnapshotQuota("Claude 5h", PeriodType.INTERVAL, UsageUnit.PERCENTAGE, 62, 100, 62, null, "USD")),
                    targetKey = "ANTHROPIC:work"
                ),
                UsageSnapshotAccount(ApiSource.DEEPSEEK, "DeepSeek", active = false, quotas = emptyList())
            )
        )
        service.start()
        api.pending += message(1, chat = 51, text = "/analise")

        waitUntil { api.sent.any { it.second.startsWith("<b>📐 Anthropic · &lt;Padrão&gt;</b>") } }
        val tab = api.keyboards.last { it.isNotEmpty() }.first()
        assertEquals("• Painel •", tab.text)
        api.pending += tap(2, chat = 51, data = tab.data)
        waitUntil { api.editedTexts.any { it.startsWith("<b>📐") } }

        // B19: o `/status` traz o atalho "📐 Análise", e ele abre a mesma análise.
        api.pending += message(3, chat = 51, text = "/status")
        waitUntil { api.keyboards.any { row -> row.any { it.data == "anp" } } }
        val before = api.sent.count { it.second.startsWith("<b>📐 Anthropic") }
        api.pending += tap(4, chat = 51, data = "anp")
        waitUntil { api.sent.count { it.second.startsWith("<b>📐 Anthropic") } == before + 1 }
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

        settings.value = settings.value.copy(preferences = TelegramBotPreferences(keyboardLayout = TELEGRAM_KEYBOARD_LAYOUT, allowRemoteUpdate = true))
        waitUntil { api.sent.any { it.second.startsWith("⬆ <b>Usage Monitor 42.1.0 pronto para instalar</b>") } }
        assertEquals("upd:42.1.0", api.keyboards.last { it.isNotEmpty() }.single().data)
        assertEquals("42.1.0", settings.value.preferences.announcedUpdateVersion)
        Thread.sleep(200)
        assertEquals(1, api.sent.count { it.second.startsWith("⬆") })

        api.pending += tap(1, chat = 51, data = "upd:41.0.0")
        waitUntil { "cb1" in api.answered }
        assertEquals(0, applied.get())
        // Toque que não reinicia não deixa reinício pendente (#417).
        assertNull(settings.value.preferences.pendingRestart)
        api.pending += tap(2, chat = 51, data = "upd:42.1.0")
        waitUntil { applied.get() == 1 }
        // Gravado antes de o app fechar: é o app reaberto quem responde (#417).
        assertEquals(TelegramPendingRestart(targetVersion = "42.1.0", fromVersion = "42.0.0"), pendingSavedAtApply)
    }

    private fun pendingRestart() {
        paired()
        val pending = TelegramPendingRestart(targetVersion = "42.1.0", fromVersion = "42.0.0")
        settings.value = settings.value.copy(preferences = settings.value.preferences.copy(pendingRestart = pending))
    }

    private fun notesWith(count: Int) = ReleaseNotes(
        version = "42.1.0",
        previousVersion = "42.0.0",
        publishedAt = null,
        releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v42.1.0",
        items = (1..count).map { index -> "item $index" }
    )

    /** #417: o app reaberto confirma o reinício pedido pelo bot, só depois do ACK, uma vez, com as novidades. */
    @Test
    fun `a restart requested from the bot is confirmed after the ack with what is new`() {
        pendingRestart()
        runningVersion = "42.1.0"
        anchored.value = false
        releaseNotesResult = Result.success(notesWith(12))
        service.start()
        Thread.sleep(200)
        assertTrue(api.sent.none { it.second.startsWith("✅") })
        assertEquals("42.1.0", settings.value.preferences.pendingRestart?.targetVersion)

        anchored.value = true
        waitUntil { api.sent.any { it.second.startsWith("✅ <b>Usage Monitor 42.1.0 instalado</b>") } }
        val text = api.sent.single { it.second.startsWith("✅") }.second
        assertTrue("<b>Novidades</b>\n• item 1\n" in text, text)
        assertTrue("• item 10\n" in text, text)
        assertFalse("item 11" in text, text)
        assertTrue(">+2 na página da versão</a>" in text, text)
        assertEquals(listOf("42.1.0" to "42.0.0"), releaseNotesRequests.toList())
        assertNull(settings.value.preferences.pendingRestart)
        assertNull(lastSaved?.preferences?.pendingRestart)
    }

    /** #417: o app que reabre na versão de origem diz que a atualização não foi aplicada, sem buscar novidades. */
    @Test
    fun `a restart that reopens on the old version says the update was not applied`() {
        pendingRestart()
        service.start()

        waitUntil { api.sent.any { it.second.startsWith("⚠ <b>A atualização para 42.1.0 não foi aplicada</b>") } }
        assertTrue(api.sent.single { it.second.startsWith("⚠") }.second.endsWith("na versão 42.0.0."))
        assertTrue(releaseNotesRequests.isEmpty())
    }

    /** #417: sem novidades por falha de rede, a confirmação sai e aponta a janela do app. */
    @Test
    fun `a failed release notes fetch still confirms the restart`() {
        pendingRestart()
        runningVersion = "42.1.0"
        releaseNotesResult = Result.failure(IllegalStateException("offline"))
        service.start()

        waitUntil { api.sent.any { it.second.startsWith("✅") } }
        assertTrue(api.sent.single { it.second.startsWith("✅") }.second.endsWith("Novidades na janela do app."))
    }

    /** #417: com o bot desligado a marca é limpa sem mensagem — a resposta de dias depois não descreveria nada. */
    @Test
    fun `a disabled bot clears the pending restart without sending`() {
        pendingRestart()
        settings.value = settings.value.copy(enabled = false)
        runningVersion = "42.1.0"
        service.start()

        waitUntil { settings.value.preferences.pendingRestart == null }
        Thread.sleep(100)
        assertTrue(api.sent.isEmpty())
    }

    /** #417: sem permissão o toque nem reinicia nem deixa reinício pendente. */
    @Test
    fun `a refused update tap leaves no pending restart`() {
        paired()
        readyVersion = "42.1.0"
        service.start()
        api.pending += tap(1, chat = 51, data = "upd:42.1.0")

        waitUntil { "cb1" in api.answered }
        assertEquals(0, applied.get())
        assertNull(settings.value.preferences.pendingRestart)
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
