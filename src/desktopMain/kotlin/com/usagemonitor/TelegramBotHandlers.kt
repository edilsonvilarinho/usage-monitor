package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramButton
import com.usagemonitor.data.datasource.TelegramIncomingMessage
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.nextMorningMillis
import com.usagemonitor.domain.entity.snoozeAlertsUntil
import com.usagemonitor.presentation.ui.TelegramBotRemoteMessages
import com.usagemonitor.domain.entity.BotButton
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.BotTap
import com.usagemonitor.domain.entity.parseBotTap
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.applyBotCommand
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
    private val toggleSource: (ApiSource, Boolean) -> Unit = { _, _ -> }
) {

    suspend fun handle(token: String, message: TelegramIncomingMessage) {
        val callbackId = message.callbackId
        if (callbackId != null) {
            // Toque de quem não está pareado não recebe nem o "fechar carregando".
            if (settingsFlow.value.isAuthorized(message.chatId)) scope.launch { handleButton(token, message, callbackId) }
            return
        }
        val command = message.text?.let(::parseBotCommand) ?: return
        val language = languageProvider()
        val settings = settingsFlow.value
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
        if (command == BotCommand.Status) {
            val snapshot = snapshotProvider()
            api.sendMessage(token, message.chatId, TelegramBotMessages.status(snapshot, language), html = true, buttons = statusButtons(language, snapshot))
            return
        }
        if (command == BotCommand.Refresh) {
            requestRefresh()
            val snapshot = snapshotProvider()
            api.sendMessage(token, message.chatId, TelegramBotMessages.status(snapshot, language), html = true, buttons = statusButtons(language, snapshot))
            return
        }
        if (command == BotCommand.Sources) {
            val allowed = settings.allowSourceControl
            val enabled = enabledSources()
            val buttons = if (allowed) rows(TelegramBotRemoteMessages.sourceButtons(enabled, language)) else emptyList()
            api.sendMessage(token, message.chatId, TelegramBotRemoteMessages.sources(enabled, allowed, language), html = true, buttons = buttons)
            return
        }
        if (command == BotCommand.QuietMenu) {
            api.sendMessage(token, message.chatId, TelegramBotRemoteMessages.quietMenu(language), html = true, buttons = rows(TelegramBotRemoteMessages.quietButtons(language)))
            return
        }
        if (command == BotCommand.Accounts) {
            val snapshot = snapshotProvider()
            api.sendMessage(token, message.chatId, TelegramBotMessages.accountPicker(snapshot, language), html = true, buttons = accountRows(snapshot))
            return
        }
        val reply = when (command) {
            BotCommand.Help -> TelegramBotMessages.help(language)
            is BotCommand.Invalid -> TelegramBotMessages.invalid(command.usage, language)
            else -> {
                val updated = applyBotCommand(alertSettingsFlow.value, command)
                if (updated != null) saveAlertSettings(updated)
                TelegramBotMessages.applied(command, language)
            }
        }
        api.sendMessage(token, message.chatId, reply, html = true)
    }

    private suspend fun handleButton(token: String, message: TelegramIncomingMessage, callbackId: String) {
        val language = languageProvider()
        val tap = parseBotTap(message.callbackData)
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
                    val text = TelegramBotMessages.status(snapshot, language)
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

    /** Falha de um toque fica no toque: o polling segue. Cancelamento continua subindo. */
    private suspend fun runTelegram(block: suspend () -> Unit) {
        runCatching { block() }.onFailure { error -> if (error is CancellationException) throw error }
    }
}
