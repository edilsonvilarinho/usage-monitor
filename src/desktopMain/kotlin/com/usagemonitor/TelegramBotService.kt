package com.usagemonitor

import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.data.datasource.TelegramRateLimitedException
import com.usagemonitor.data.datasource.TelegramUnauthorizedException
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramDailySpend
import com.usagemonitor.domain.entity.dailySummaryDate
import com.usagemonitor.domain.entity.isDailySummaryDue
import com.usagemonitor.presentation.ui.TelegramBotSummaryMessages
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageSnapshot
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
    /** [botUsername] sem `@`; `null` quando o `getMe` falhou — o link "Abrir no Telegram" some. */
    data class Connected(val chatCount: Int, val botUsername: String? = null) : TelegramBotStatus
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
 *
 * O que cada comando e cada botão faz mora em [TelegramBotHandlers]; aqui ficam o
 * laço de polling, o repasse dos alertas e o envio em lote.
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
    /** Pede coleta ao app e volta quando ela termina (ou desiste); respeita o backoff de 429 do painel. */
    private val requestRefresh: suspend () -> Unit = {},
    private val clock: Clock = Clock.System,
    /** Fontes ligadas e como ligar/desligar uma (#398, Y8); o `/api` usa os dois. */
    private val enabledSources: () -> Set<ApiSource> = { emptySet() },
    private val toggleSource: (ApiSource, Boolean) -> Unit = { _, _ -> },
    /** Gasto do Claude Code nas últimas 24 h (#398, Y1); `null` é "não medido". */
    private val spendProvider: suspend () -> TelegramDailySpend? = { null }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var alertJob: Job? = null
    private var summaryJob: Job? = null

    private val handlers = TelegramBotHandlers(
        api = api,
        scope = scope,
        settingsFlow = settingsFlow,
        saveSettings = saveSettings,
        alertSettingsFlow = alertSettingsFlow,
        saveAlertSettings = saveAlertSettings,
        snapshotProvider = snapshotProvider,
        languageProvider = languageProvider,
        requestRefresh = requestRefresh,
        clock = clock,
        enabledSources = enabledSources,
        toggleSource = toggleSource,
        summaryText = { language -> TelegramBotSummaryMessages.summary(snapshotProvider(), spendProvider(), language) }
    )

    private val _status = MutableStateFlow<TelegramBotStatus>(TelegramBotStatus.Off)
    val status: StateFlow<TelegramBotStatus> = _status.asStateFlow()

    /** Idempotente. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch { pollLoop() }
        alertJob = scope.launch {
            alerts.collect { alert -> broadcast(TelegramBotMessages.alert(alert, languageProvider())) }
        }
        summaryJob = scope.launch { summaryLoop() }
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
        var botUsername: String? = null
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
                    registerMenu(settings.botToken)
                    botUsername = fetchUsername(settings.botToken)
                }
                _status.value = TelegramBotStatus.Connected(settings.authorizedChats.size, botUsername)
                val updates = api.getUpdates(settings.botToken, offset, LONG_POLL_SECONDS)
                failures = 0
                for (update in updates) {
                    offset = update.updateId + 1
                    handlers.handle(settings.botToken, update)
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

    private suspend fun fetchUsername(token: String): String? =
        runCatching { api.getMe(token) }.onFailure { error -> if (error is CancellationException) throw error }.getOrNull()

    /** Menu de comandos do Telegram (#396). Falhar aqui não derruba a conexão: o bot responde sem o menu. */
    private suspend fun registerMenu(token: String) {
        runCatching { api.setMyCommands(token, TelegramBotMessages.menu(languageProvider())) }
            .onFailure { error -> if (error is CancellationException) throw error }
    }

    /**
     * Resumo diário (#398, Y1): checa uma vez por minuto se já é hora. A data vai
     * para o `telegram.json` **antes** do envio: falhar no meio não repete o
     * resumo a cada minuto — perde-se um dia, não se inunda a conversa.
     */
    private suspend fun summaryLoop() {
        while (true) {
            val settings = settingsFlow.value
            val now = clock.now()
            val ready = settings.enabled && settings.botToken.isNotBlank() && settings.authorizedChats.isNotEmpty()
            if (ready && isDailySummaryDue(settings.dailySummaryHour, settings.lastSummaryDate, now, alertSettingsFlow.value)) {
                val marked = settings.copy(lastSummaryDate = dailySummaryDate(now))
                runCatching { saveSettings(marked) }
                settingsFlow.value = marked
                runCatching {
                    val language = languageProvider()
                    broadcast(TelegramBotSummaryMessages.summary(snapshotProvider(), spendProvider(), language))
                }.onFailure { error -> if (error is CancellationException) throw error }
            }
            delay(SUMMARY_CHECK_MILLIS)
        }
    }

    /** Uma mensagem por conversa, espaçadas: o Telegram limita ~1 mensagem/s por conversa. */
    private suspend fun broadcast(text: String) {
        val settings = settingsFlow.value
        if (!settings.enabled || settings.botToken.isBlank()) return
        for (chat in settings.authorizedChats) {
            runCatching { api.sendMessage(settings.botToken, chat.id, text, html = true) }
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
        const val SUMMARY_CHECK_MILLIS = 60_000L
    }
}
