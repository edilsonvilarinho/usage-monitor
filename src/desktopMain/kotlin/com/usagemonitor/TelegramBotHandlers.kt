package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.data.datasource.TelegramIncomingMessage
import com.usagemonitor.data.datasource.TelegramReplyKeyboard
import com.usagemonitor.domain.entity.parseKeyboardLabel
import com.usagemonitor.domain.entity.telegramKeyboardRows
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.nextMorningMillis
import com.usagemonitor.domain.entity.snoozeAlertsUntil
import com.usagemonitor.presentation.ui.TelegramBotChatMessages
import com.usagemonitor.presentation.ui.TelegramBotInsightMessages
import com.usagemonitor.presentation.ui.TelegramBotRemoteMessages
import com.usagemonitor.presentation.ui.TelegramBotUsageMessages
import com.usagemonitor.presentation.ui.buildTelegramHeatmap
import com.usagemonitor.presentation.ui.buildTelegramRings
import com.usagemonitor.domain.entity.BotButton
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.presentation.ui.TelegramChart
import com.usagemonitor.presentation.ui.TelegramChartMessages
import com.usagemonitor.domain.entity.parseBotTap
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.applyBotCommand
import com.usagemonitor.domain.entity.changesApp
import com.usagemonitor.domain.entity.parseBotCommand
import com.usagemonitor.domain.entity.snoozeAlerts
import com.usagemonitor.presentation.ui.TelegramBotMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * O que o bot faz com cada update recebido (#387, #396, #398): comando de texto
 * ou toque num botão inline. Saiu do [TelegramBotService], que fica com o laço de
 * polling e o repasse de alertas — as funções novas da #398 cresceriam o serviço
 * além do que um arquivo deve carregar.
 *
 * Só conversa pareada é atendida; o `/start` com código é a única exceção.
 */
internal class TelegramBotHandlers(
    private val api: TelegramBotApi,
    /** Onde o toque roda: fora do laço de polling, porque a coleta pode levar segundos. */
    private val scope: CoroutineScope,
    private val settingsFlow: MutableStateFlow<TelegramBotSettings>,
    private val saveSettings: (TelegramBotSettings) -> Unit,
    private val alertSettingsFlow: StateFlow<UsageAlertSettings>,
    private val saveAlertSettings: (UsageAlertSettings) -> Unit,
    private val snapshotProvider: () -> UsageSnapshot?,
    private val languageProvider: () -> AppLanguage,
    private val requestRefresh: suspend () -> Unit,
    private val clock: Clock,
    /** Fontes ligadas agora (#398, Y8). */
    private val enabledSources: () -> Set<ApiSource> = { emptySet() },
    /** Liga ou desliga uma fonte pelo mesmo caminho das Configurações. */
    private val toggleSource: (ApiSource, Boolean) -> Unit = { _, _ -> },
    /** O texto do resumo diário (#398, Y1), montado por quem tem o gasto. */
    private val summaryText: suspend (AppLanguage) -> String = { language -> TelegramBotMessages.noReading(language == AppLanguage.PT) },
    /** O gráfico do intervalo (#398, Y6); `null` sem histórico — a resposta vira texto. */
    private val chartProvider: suspend (HistoryRange) -> TelegramChart? = { null },
    /** Leituras das funções da #408. */
    private val readings: TelegramBotReadings = TelegramBotReadings()
) {

    suspend fun handle(token: String, message: TelegramIncomingMessage) {
        val callbackId = message.callbackId
        if (callbackId != null) {
            // Toque de quem não está pareado não recebe nem o "fechar carregando".
            if (settingsFlow.value.isAuthorized(message.chatId)) scope.launch { handleButton(token, message, callbackId) }
            return
        }
        val text = message.text ?: return
        val command = parseBotCommand(text) ?: parseKeyboardLabel(text) ?: return
        val settings = settingsFlow.value
        val language = settings.languageOf(message.chatId, languageProvider())
        if (command is BotCommand.Start) {
            if (!settings.isAuthorized(message.chatId) && settings.acceptsPairing(command.code, clock.now().toEpochMilliseconds())) {
                val paired = settings.copy(
                    authorizedChats = settings.authorizedChats + TelegramChat(message.chatId, message.chatName),
                    pairingCode = null,
                    pairingExpiresAtMillis = null
                )
                runCatching { saveSettings(paired) }
                settingsFlow.value = paired
                api.sendMessage(token, message.chatId, TelegramBotMessages.paired(language), html = true)
            }
            return
        }
        // Conversa não pareada não recebe resposta: o bot não confirma que existe.
        if (!settings.isAuthorized(message.chatId)) return
        if (settings.isReadOnly(message.chatId) && command.changesApp()) {
            send(token, message.chatId, TelegramBotChatMessages.readOnly(language))
            return
        }
        reply(token, message.chatId, command, language)
    }

    /**
     * Um comando já autorizado. `when` exaustivo de propósito: comando novo em
     * [BotCommand] não compila sem dizer o que o bot responde.
     */
    private suspend fun reply(token: String, chatId: Long, command: BotCommand, language: AppLanguage) {
        when (command) {
            is BotCommand.Start -> Unit
            BotCommand.Status -> sendStatus(token, chatId, language)
            BotCommand.StatusImage -> {
                val rings = buildTelegramRings(snapshotProvider(), language)
                if (rings.rings.isEmpty()) {
                    send(token, chatId, TelegramBotUsageMessages.ringsEmpty(language))
                } else {
                    api.sendPhoto(token, chatId, TelegramRingsRenderer.render(rings), TelegramBotUsageMessages.ringsCaption(rings.collectedAt, language))
                }
            }
            BotCommand.Refresh -> {
                requestRefresh()
                sendStatus(token, chatId, language)
            }
            BotCommand.Sources -> sendSources(token, chatId, language)
            is BotCommand.Chart -> sendChart(token, chatId, command.range, language)
            BotCommand.Summary -> api.sendMessage(token, chatId, summaryText(language), html = true)
            BotCommand.QuietMenu -> api.sendMessage(
                token, chatId, TelegramBotRemoteMessages.quietMenu(language), html = true,
                buttons = rows(TelegramBotRemoteMessages.quietButtons(language))
            )
            BotCommand.Accounts -> {
                val snapshot = snapshotProvider()
                api.sendMessage(token, chatId, TelegramBotMessages.accountPicker(snapshot, language), html = true, buttons = accountRows(snapshot))
            }
            BotCommand.Resets -> send(token, chatId, TelegramBotInsightMessages.resets(snapshotProvider(), language))
            BotCommand.Exhaustion -> send(token, chatId, TelegramBotInsightMessages.exhaustion(snapshotProvider(), language))
            BotCommand.BestAccount -> send(token, chatId, TelegramBotInsightMessages.bestAccount(snapshotProvider(), language))
            BotCommand.Pace -> send(token, chatId, TelegramBotInsightMessages.pace(snapshotProvider(), language))
            BotCommand.Ping -> send(token, chatId, TelegramBotChatMessages.ping(readings.ping(), snapshotProvider(), clock.now(), language))
            BotCommand.Spike -> send(token, chatId, TelegramBotInsightMessages.spikes(readings.spikes(), alertSettingsFlow.value.effectiveSpikeFactor, language))
            BotCommand.Throughput -> {
                val since = clock.now().toEpochMilliseconds() - DAY_MILLIS
                send(token, chatId, TelegramBotUsageMessages.throughput(readings.comparison(since), language))
            }
            BotCommand.Week -> send(token, chatId, TelegramBotUsageMessages.week(readings.accountHistory(HistoryRange.LAST_7_DAYS), language))
            is BotCommand.Compare -> sendHeatmap(token, chatId, command.days, language)
            BotCommand.Chats -> send(token, chatId, TelegramBotChatMessages.chats(settingsFlow.value.authorizedChats, chatId, language))
            BotCommand.Leave -> leave(token, chatId, language)
            is BotCommand.Keyboard -> api.sendMessage(
                token, chatId, TelegramBotChatMessages.keyboard(command.on, language), html = true,
                replyKeyboard = if (command.on) TelegramReplyKeyboard.Show(telegramKeyboardRows(language)) else TelegramReplyKeyboard.Remove
            )
            is BotCommand.ChatLanguage -> {
                val current = settingsFlow.value
                val next = current.copy(authorizedChats = current.authorizedChats.map { chat -> if (chat.id == chatId) chat.copy(language = command.language) else chat })
                runCatching { saveSettings(next) }
                settingsFlow.value = next
                send(token, chatId, TelegramBotChatMessages.languageSet(command.language, next.languageOf(chatId, languageProvider())))
            }
            is BotCommand.Windows -> {
                // Semanais pedem o mês: em 7 dias cabem uma ou duas janelas de 7 d.
                val range = if (command.weekly) HistoryRange.LAST_30_DAYS else HistoryRange.LAST_7_DAYS
                send(token, chatId, TelegramBotUsageMessages.windows(readings.accountHistory(range), command.weekly, language))
            }
            BotCommand.Help -> api.sendMessage(token, chatId, TelegramBotMessages.help(language), html = true)
            is BotCommand.Invalid -> api.sendMessage(token, chatId, TelegramBotMessages.invalid(command.usage, language), html = true)
            is BotCommand.Alerts, is BotCommand.Quiet, is BotCommand.Threshold -> {
                val updated = applyBotCommand(alertSettingsFlow.value, command)
                if (updated != null) saveAlertSettings(updated)
                api.sendMessage(token, chatId, TelegramBotMessages.applied(command, language), html = true)
            }
        }
    }

    private suspend fun send(token: String, chatId: Long, text: String) {
        api.sendMessage(token, chatId, text, html = true)
    }

    private suspend fun sendStatus(token: String, chatId: Long, language: AppLanguage) {
        val snapshot = snapshotProvider()
        api.sendMessage(token, chatId, statusText(snapshot, language), html = true, buttons = statusButtons(language, snapshot))
    }

    /**
     * `/sair` (#408, AA22): a conversa sai da lista antes da resposta — a confirmação
     * é a última mensagem dela. Sair não pede permissão: é a conversa decidindo por si.
     * O painel fixado dela é desafixado aqui, porque o laço do painel só cuida de
     * conversas que ainda estão na lista.
     */
    private suspend fun leave(token: String, chatId: Long, language: AppLanguage) {
        val current = settingsFlow.value
        val panel = current.panelMessages[chatId]
        val next = current.withoutChat(chatId)
        runCatching { saveSettings(next) }
        settingsFlow.value = next
        if (panel != null) runTelegram { api.unpinChatMessage(token, chatId, panel) }
        send(token, chatId, TelegramBotChatMessages.left(language))
    }

    /** `/comparar` (#408, AA6): índice ilegível ou recorte vazio respondem em texto. */
    private suspend fun sendHeatmap(token: String, chatId: Long, days: Int, language: AppLanguage) {
        val rows = readings.comparison(clock.now().toEpochMilliseconds() - days * DAY_MILLIS)
        if (rows == null) {
            send(token, chatId, TelegramBotUsageMessages.throughput(null, language))
            return
        }
        val heatmap = buildTelegramHeatmap(rows, language)
        if (heatmap.isEmpty) {
            send(token, chatId, TelegramBotUsageMessages.compareEmpty(language))
            return
        }
        api.sendPhoto(token, chatId, TelegramHeatmapRenderer.render(heatmap), TelegramBotUsageMessages.compareCaption(days, language))
    }

    /** O `/status` com a sparkline das últimas 6 h de cada cota (#408, AA13). */
    private suspend fun statusText(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val now = snapshot?.generatedAt ?: return TelegramBotMessages.status(null, language)
        val sparklines = TelegramBotUsageMessages.sparklines(readings.accountHistory(HistoryRange.LAST_24_HOURS), now)
        return TelegramBotMessages.status(snapshot, language, sparklines)
    }

    private suspend fun sendSources(token: String, chatId: Long, language: AppLanguage) {
        val allowed = settingsFlow.value.allowSourceControl
        val enabled = enabledSources()
        val buttons = if (allowed) rows(TelegramBotRemoteMessages.sourceButtons(enabled, language)) else emptyList()
        api.sendMessage(token, chatId, TelegramBotRemoteMessages.sources(enabled, allowed, language), html = true, buttons = buttons)
    }

    private suspend fun sendChart(token: String, chatId: Long, range: HistoryRange, language: AppLanguage) {
        val chart = chartProvider(range)
        if (chart == null || chart.isEmpty) {
            api.sendMessage(token, chatId, TelegramChartMessages.empty(language), html = true)
            return
        }
        api.sendPhoto(token, chatId, TelegramChartRenderer.render(chart), TelegramChartMessages.caption(range, chart, language))
    }

    private suspend fun handleButton(token: String, message: TelegramIncomingMessage, callbackId: String) {
        val language = settingsFlow.value.languageOf(message.chatId, languageProvider())
        val tap = parseBotTap(message.callbackData)
        // O papel é relido no toque: um teclado enviado antes de a conversa virar só leitura não vale mais.
        if (settingsFlow.value.isReadOnly(message.chatId) && tapChangesApp(tap, BotButton.fromData(message.callbackData))) {
            runTelegram { api.answerCallbackQuery(token, callbackId, TelegramBotChatMessages.readOnlyTap(language)) }
            return
        }
        if (tap != null) {
            runTelegram { handleTap(token, message, callbackId, tap, language) }
            return
        }
        val button = BotButton.fromData(message.callbackData)
        runTelegram {
            when (button) {
                BotButton.REFRESH -> {
                    api.answerCallbackQuery(token, callbackId, TelegramBotMessages.refreshing(language))
                    requestRefresh()
                    val messageId = message.messageId
                    val snapshot = snapshotProvider()
                    val text = statusText(snapshot, language)
                    // Sem mudança o Telegram responde 400 "message is not modified": o `runTelegram` o engole.
                    if (messageId != null) api.editMessageText(token, message.chatId, messageId, text, statusButtons(language, snapshot))
                }
                BotButton.SNOOZE -> {
                    val snoozed = snoozeAlerts(alertSettingsFlow.value, clock.now().toEpochMilliseconds())
                    saveAlertSettings(snoozed)
                    api.answerCallbackQuery(token, callbackId)
                    api.sendMessage(token, message.chatId, TelegramBotMessages.snoozed(snoozed.snoozedUntilEpochMillis ?: 0L, language), html = true)
                }
                BotButton.THRESHOLDS -> {
                    api.answerCallbackQuery(token, callbackId)
                    api.sendMessage(token, message.chatId, TelegramBotMessages.thresholds(alertSettingsFlow.value, language), html = true)
                }
                null -> api.answerCallbackQuery(token, callbackId)
            }
        }
    }

    private suspend fun handleTap(token: String, message: TelegramIncomingMessage, callbackId: String, tap: BotTap, language: AppLanguage) {
        when (tap) {
            is BotTap.Account -> {
                api.answerCallbackQuery(token, callbackId)
                api.sendMessage(token, message.chatId, TelegramBotMessages.account(snapshotProvider(), tap.key, language), html = true)
            }
            is BotTap.Source -> toggleFromTap(token, message, callbackId, tap.source, language)
            is BotTap.SnoozeFor -> snoozeFromTap(token, message.chatId, callbackId, clock.now().toEpochMilliseconds() + tap.minutes * 60_000L, language)
            BotTap.SnoozeUntilMorning -> snoozeFromTap(token, message.chatId, callbackId, nextMorningMillis(clock.now().toEpochMilliseconds()), language)
        }
    }

    /** Toques que mudam o app (#408, AA21): ligar fonte e silenciar. Ver conta e atualizar só leem. */
    private fun tapChangesApp(tap: BotTap?, button: BotButton?): Boolean = when (tap) {
        is BotTap.Source, is BotTap.SnoozeFor, BotTap.SnoozeUntilMorning -> true
        is BotTap.Account -> false
        null -> button == BotButton.SNOOZE
    }

    /**
     * A permissão é relida no toque, e não só no envio do teclado: desligada
     * depois, o teclado antigo não pode continuar mudando fontes.
     */
    private suspend fun toggleFromTap(token: String, message: TelegramIncomingMessage, callbackId: String, source: ApiSource, language: AppLanguage) {
        if (!settingsFlow.value.allowSourceControl) {
            api.answerCallbackQuery(token, callbackId, TelegramBotRemoteMessages.sourceControlOff(language))
            return
        }
        val turnOn = source !in enabledSources()
        toggleSource(source, turnOn)
        api.answerCallbackQuery(token, callbackId)
        val messageId = message.messageId
        val enabled = enabledSources()
        if (messageId != null) {
            api.editMessageText(
                token, message.chatId, messageId,
                TelegramBotRemoteMessages.sources(enabled, allowed = true, language),
                rows(TelegramBotRemoteMessages.sourceButtons(enabled, language))
            )
        }
        api.sendMessage(token, message.chatId, TelegramBotRemoteMessages.sourceToggled(source, turnOn, language), html = true)
    }

    private suspend fun snoozeFromTap(token: String, chatId: Long, callbackId: String, untilMillis: Long, language: AppLanguage) {
        saveAlertSettings(snoozeAlertsUntil(alertSettingsFlow.value, untilMillis))
        api.answerCallbackQuery(token, callbackId)
        api.sendMessage(token, chatId, TelegramBotRemoteMessages.snoozedUntil(untilMillis, clock.now().toEpochMilliseconds(), language), html = true)
    }

    private fun rows(labelled: List<List<Pair<String, String>>>): List<List<TelegramButton>> =
        labelled.map { row -> row.map { (label, data) -> TelegramButton(label, data) } }

    /** Ações do `/status` numa linha e, embaixo, um botão por conta (#398, Y4). */
    private fun statusButtons(language: AppLanguage, snapshot: UsageSnapshot?): List<List<TelegramButton>> =
        listOf(TelegramBotMessages.statusButtons(language).map { (label, button) -> TelegramButton(label, button.data) }) +
            accountRows(snapshot)

    private fun accountRows(snapshot: UsageSnapshot?): List<List<TelegramButton>> = rows(TelegramBotMessages.accountButtons(snapshot))

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    }

    /** Falha de um toque fica no toque: o polling segue. Cancelamento continua subindo. */
    private suspend fun runTelegram(block: suspend () -> Unit) {
        runCatching { block() }.onFailure { error -> if (error is CancellationException) throw error }
    }
}
