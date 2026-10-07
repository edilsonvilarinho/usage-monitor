package com.usagemonitor

import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TELEGRAM_PAIRING_TTL_MILLIS
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.TelegramBotSectionModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/** Ações da seção do bot (#387); arquivo próprio para o `SettingsActions` não crescer. */
internal class TelegramBotActions(
    private val dataSource: LocalTelegramSettingsDataSource,
    private val settingsFlow: MutableStateFlow<TelegramBotSettings>,
    private val service: TelegramBotService,
    private val clock: Clock = Clock.System
) {
    fun setEnabled(enabled: Boolean) = update { current -> current.copy(enabled = enabled) }

    fun changeToken(token: String) = update { current -> current.copy(botToken = token.trim()) }

    /** Abre um código novo por [TELEGRAM_PAIRING_TTL_MILLIS]; o anterior deixa de valer. */
    fun startPairing() = update { current ->
        current.copy(
            pairingCode = LocalTelegramSettingsDataSource.newPairingCode(),
            pairingExpiresAtMillis = clock.now().toEpochMilliseconds() + TELEGRAM_PAIRING_TTL_MILLIS
        )
    }

    fun removeChat(chatId: Long) = update { current ->
        current.copy(authorizedChats = current.authorizedChats.filterNot { chat -> chat.id == chatId })
    }

    fun sendTest() = service.sendTest()

    fun setAllowSourceControl(allowed: Boolean) = update { current -> current.copy(allowSourceControl = allowed) }

    /**
     * Trocar a hora religa o resumo de hoje: escolher 18:00 às 10:00 não pode ficar
     * sem resumo porque o das 08:00 já saiu.
     */
    fun setDailySummaryHour(hour: Int?) = update { current -> current.copy(dailySummaryHour = hour, lastSummaryDate = null) }

    /** O serviço envia e fixa, ou desafixa, na próxima passada do painel (até 1 min). */
    fun setLivePanel(enabled: Boolean) = update { current -> current.copy(livePanelEnabled = enabled) }

    private fun update(transform: (TelegramBotSettings) -> TelegramBotSettings) {
        val next = transform(settingsFlow.value)
        runCatching { dataSource.save(next) }
        settingsFlow.value = next
    }
}

internal fun telegramBotSectionModel(
    settings: TelegramBotSettings,
    status: TelegramBotStatus,
    snapshot: UsageSnapshot?,
    language: AppLanguage,
    actions: TelegramBotActions,
    nowMillis: Long = Clock.System.now().toEpochMilliseconds()
): TelegramBotSectionModel {
    val pt = language == AppLanguage.PT
    val (label, tone) = when (status) {
        TelegramBotStatus.Off -> (if (pt) "Desligado" else "Off") to AppTone.NEUTRAL
        TelegramBotStatus.Connecting -> (if (pt) "Conectando" else "Connecting") to AppTone.INFO
        is TelegramBotStatus.Connected -> {
            val who = status.botUsername?.let { name -> (if (pt) "Conectado como @" else "Connected as @") + name } ?: if (pt) "Conectado" else "Connected"
            if (status.chatCount == 0) {
                (if (pt) "$who · sem conversa pareada" else "$who · no paired chat") to AppTone.WARNING
            } else {
                (if (pt) "$who · ${status.chatCount} conversa(s)" else "$who · ${status.chatCount} chat(s)") to AppTone.OK
            }
        }
        is TelegramBotStatus.Failed -> ((if (pt) "Falha: " else "Failed: ") + status.message) to AppTone.CRITICAL
    }
    val expiresAt = settings.pairingExpiresAtMillis
    val openCode = settings.pairingCode?.takeIf { expiresAt != null && nowMillis < expiresAt }
    val minutesLeft = expiresAt?.let { at -> ((at - nowMillis) / 60_000L).coerceAtLeast(0L) + 1 }
    return TelegramBotSectionModel(
        enabled = settings.enabled,
        token = settings.botToken,
        statusLabel = label,
        statusTone = tone,
        chats = settings.authorizedChats,
        pairingCode = openCode,
        pairingHint = openCode?.let { if (pt) "Vale por mais $minutesLeft min." else "Valid for $minutesLeft more min." },
        statusPreview = TelegramBotMessages.plain(TelegramBotMessages.status(snapshot, language)),
        onEnabledChange = actions::setEnabled,
        onTokenChange = actions::changeToken,
        onStartPairing = actions::startPairing,
        onRemoveChat = actions::removeChat,
        onSendTest = actions::sendTest,
        connected = status is TelegramBotStatus.Connected,
        botUsername = (status as? TelegramBotStatus.Connected)?.botUsername,
        allowSourceControl = settings.allowSourceControl,
        onAllowSourceControlChange = actions::setAllowSourceControl,
        dailySummaryHour = settings.dailySummaryHour,
        onDailySummaryHourChange = actions::setDailySummaryHour,
        livePanelEnabled = settings.livePanelEnabled,
        onLivePanelChange = actions::setLivePanel
    )
}

/**
 * "Atualizar" do bot (#396): a mesma coleta do botão do app — alvo em backoff de 429
 * não vai à rede —, esperando ela terminar. Se nada começar (tudo em backoff), volta
 * logo e o bot mostra a leitura que já tem.
 */
internal suspend fun refreshForBot(dashboard: DashboardViewModel) {
    dashboard.refresh()
    withTimeoutOrNull(BOT_REFRESH_START_MILLIS) { dashboard.refreshingTargets.first { targets -> targets.isNotEmpty() } } ?: return
    withTimeoutOrNull(BOT_REFRESH_FINISH_MILLIS) { dashboard.refreshingTargets.first { targets -> targets.isEmpty() } }
}

private const val BOT_REFRESH_START_MILLIS = 2_000L
private const val BOT_REFRESH_FINISH_MILLIS = 30_000L
