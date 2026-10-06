package com.usagemonitor

import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TELEGRAM_PAIRING_TTL_MILLIS
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.TelegramBotSectionModel
import kotlinx.coroutines.flow.MutableStateFlow
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
        is TelegramBotStatus.Connected -> if (status.chatCount == 0) {
            (if (pt) "Conectado · sem conversa pareada" else "Connected · no paired chat") to AppTone.WARNING
        } else {
            (if (pt) "Conectado · ${status.chatCount} conversa(s)" else "Connected · ${status.chatCount} chat(s)") to AppTone.OK
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
        statusPreview = TelegramBotMessages.status(snapshot, language),
        onEnabledChange = actions::setEnabled,
        onTokenChange = actions::changeToken,
        onStartPairing = actions::startPairing,
        onRemoveChat = actions::removeChat,
        onSendTest = actions::sendTest
    )
}
