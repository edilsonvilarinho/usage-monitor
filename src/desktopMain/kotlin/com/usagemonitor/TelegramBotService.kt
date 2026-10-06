package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramIncomingMessage
import com.usagemonitor.data.datasource.TelegramRateLimitedException
import com.usagemonitor.data.datasource.TelegramUnauthorizedException
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.BotCommand
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.applyBotCommand
import com.usagemonitor.domain.entity.parseBotCommand
import com.usagemonitor.presentation.ui.TelegramBotMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Estado do bot para a seção das Configurações. */
internal sealed interface TelegramBotStatus {
    data object Off : TelegramBotStatus
    data object Connecting : TelegramBotStatus
    data class Connected(val chatCount: Int) : TelegramBotStatus
    data class Failed(val message: String) : TelegramBotStatus
}

/**
 * Bot do Telegram (#387): mão dupla, só HTTPS de saída.
 *
 * - **Envio**: cada alerta que a bandeja mostra (já deduplicado e respeitando o
 *   silêncio, em `UsageAlertViewModel`) vai também para as conversas pareadas.
 * - **Recebimento**: long polling (`getUpdates`); só conversa pareada por código
 *   é atendida — mensagem de estranho é ignorada sem resposta.
 *
 * O polling **começa do agora**: o Telegram guarda updates por 24 h, e sem pular
 * o acumulado um `/alertas off` antigo seria reaplicado a cada reinício do app.
 * Mesmo ciclo de vida do `TeamSyncService`: escopo próprio, `onDestroy` no
 * encerramento único de `AppViewModels`.
 */
internal class TelegramBotService(
    private val api: TelegramBotApi,
    private val settingsFlow: MutableStateFlow<TelegramBotSettings>,
    private val saveSettings: (TelegramBotSettings) -> Unit,
    private val alertSettingsFlow: StateFlow<UsageAlertSettings>,
    private val saveAlertSettings: (UsageAlertSettings) -> Unit,
    private val alerts: Flow<UsageAlert>,
    private val snapshotProvider: () -> UsageSnapshot?,
    private val languageProvider: () -> AppLanguage,
    private val clock: Clock = Clock.System
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var alertJob: Job? = null

    private val _status = MutableStateFlow<TelegramBotStatus>(TelegramBotStatus.Off)
    val status: StateFlow<TelegramBotStatus> = _status.asStateFlow()

    /** Idempotente. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch { pollLoop() }
        alertJob = scope.launch {
            alerts.collect { alert -> broadcast(TelegramBotMessages.alert(alert, languageProvider())) }
        }
    }

    fun onDestroy() {
        scope.cancel()
    }

    /** Botão "Enviar teste" das Configurações. */
    fun sendTest() {
        scope.launch { broadcast(TelegramBotMessages.test(languageProvider())) }
    }

    private suspend fun pollLoop() {
        var activeToken: String? = null
        var offset: Long? = null
        var failures = 0
        while (true) {
            val settings = settingsFlow.value
            if (!settings.enabled || settings.botToken.isBlank()) {
                _status.value = TelegramBotStatus.Off
                activeToken = null
                delay(IDLE_CHECK_MILLIS)
                continue
            }
            try {
                if (settings.botToken != activeToken) {
                    _status.value = TelegramBotStatus.Connecting
                    api.deleteWebhook(settings.botToken)
                    // Pula o acumulado: o último update vira o ponto de partida.
                    offset = api.getUpdates(settings.botToken, offset = -1, timeoutSeconds = 0).lastOrNull()?.updateId?.plus(1)
                    activeToken = settings.botToken
                }
                _status.value = TelegramBotStatus.Connected(settings.authorizedChats.size)
                val updates = api.getUpdates(settings.botToken, offset, LONG_POLL_SECONDS)
                failures = 0
                for (update in updates) {
                    offset = update.updateId + 1
                    handle(settings.botToken, update)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (limited: TelegramRateLimitedException) {
                delay(limited.retryAfterSeconds * 1_000L)
            } catch (_: TelegramUnauthorizedException) {
                _status.value = TelegramBotStatus.Failed("Token do bot recusado pelo Telegram.")
                activeToken = null
                delay(UNAUTHORIZED_RETRY_MILLIS)
            } catch (error: Exception) {
                failures++
                _status.value = TelegramBotStatus.Failed(error.message ?: error::class.simpleName.orEmpty())
                delay((BACKOFF_BASE_MILLIS shl (failures - 1).coerceAtMost(4)).coerceAtMost(BACKOFF_MAX_MILLIS))
            }
        }
    }

    private suspend fun handle(token: String, message: TelegramIncomingMessage) {
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
                api.sendMessage(token, message.chatId, TelegramBotMessages.paired(language))
            }
            return
        }
        // Conversa não pareada não recebe resposta: o bot não confirma que existe.
        if (!settings.isAuthorized(message.chatId)) return
        val reply = when (command) {
            BotCommand.Status -> TelegramBotMessages.status(snapshotProvider(), language)
            BotCommand.Help -> TelegramBotMessages.help(language)
            is BotCommand.Invalid -> TelegramBotMessages.invalid(command.usage, language)
            else -> {
                val updated = applyBotCommand(alertSettingsFlow.value, command)
                if (updated != null) saveAlertSettings(updated)
                TelegramBotMessages.applied(command, language)
            }
        }
        api.sendMessage(token, message.chatId, reply)
    }

    /** Uma mensagem por conversa, espaçadas: o Telegram limita ~1 mensagem/s por conversa. */
    private suspend fun broadcast(text: String) {
        val settings = settingsFlow.value
        if (!settings.enabled || settings.botToken.isBlank()) return
        for (chat in settings.authorizedChats) {
            runCatching { api.sendMessage(settings.botToken, chat.id, text) }
                .onFailure { error -> if (error is CancellationException) throw error }
            delay(SEND_SPACING_MILLIS)
        }
    }

    private companion object {
        const val LONG_POLL_SECONDS = 25
        const val IDLE_CHECK_MILLIS = 5_000L
        const val UNAUTHORIZED_RETRY_MILLIS = 60_000L
        const val BACKOFF_BASE_MILLIS = 2_000L
        const val BACKOFF_MAX_MILLIS = 60_000L
        const val SEND_SPACING_MILLIS = 1_100L
    }
}
