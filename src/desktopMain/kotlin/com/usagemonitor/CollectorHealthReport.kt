package com.usagemonitor

import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.domain.entity.CollectorSourceHealth
import com.usagemonitor.domain.entity.CollectorTeamHealth
import com.usagemonitor.domain.entity.CollectorTelegramHealth
import com.usagemonitor.domain.entity.CollectorWebHealth
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TelegramBotSettings
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.viewmodel.UiState
import kotlin.time.Instant

/**
 * A saúde do coletor a partir do estado vivo do motor (#440), em função pura: cada conta
 * coletada e as que só falharam, o bot, o time e o acesso web. O `service status` a lê
 * de fora, pelo arquivo que o motor grava.
 */
internal fun collectorHealthOf(
    dashboard: UiState,
    nextCollectionAt: Instant?,
    telegramStatus: TelegramBotStatus,
    telegramSettings: TelegramBotSettings,
    teamSettings: TeamIntegrationSettings,
    teamSync: TeamSyncStatus,
    webStatus: WebAccessStatus,
    now: Instant
): CollectorHealth {
    val data = (dashboard as? UiState.Success)?.data.orEmpty()
    val errors = when (dashboard) {
        is UiState.Success -> dashboard.errors
        is UiState.Error -> dashboard.errors
        else -> emptyList()
    }
    val failing = errors.associateBy { error -> error.target }
    val collected = data.map { stats ->
        val error = failing[stats.targetKey]
        val label = listOfNotNull(stats.source.displayName(), stats.profileLabel).joinToString(" ")
        CollectorSourceHealth(
            stats.source,
            label,
            ok = error == null,
            retryAtEpochMillis = error?.retryAt?.toEpochMilliseconds(),
            target = stats.targetKey.storageKey,
            failure = error?.message
        )
    }
    val collectedTargets = data.map { stats -> stats.targetKey }.toSet()
    val onlyFailing = errors.filter { error -> error.target !in collectedTargets }.map { error ->
        CollectorSourceHealth(
            error.source,
            error.targetLabel ?: error.source.displayName(),
            ok = false,
            retryAtEpochMillis = error.retryAt?.toEpochMilliseconds(),
            target = error.target.storageKey,
            failure = error.message
        )
    }
    return CollectorHealth(
        updatedAtEpochMillis = now.toEpochMilliseconds(),
        nextCollectionAtEpochMillis = nextCollectionAt?.toEpochMilliseconds(),
        sources = collected + onlyFailing,
        telegram = CollectorTelegramHealth(
            state = when (telegramStatus) {
                TelegramBotStatus.Off -> "off"
                TelegramBotStatus.Connecting -> "connecting"
                is TelegramBotStatus.Connected -> "connected"
                is TelegramBotStatus.Failed -> "failed"
                TelegramBotStatus.TokenRejected -> "token_rejected"
            },
            botUsername = (telegramStatus as? TelegramBotStatus.Connected)?.botUsername,
            chatCount = telegramSettings.authorizedChats.size
        ),
        team = CollectorTeamHealth(
            active = teamSettings.isActive,
            server = teamSettings.normalizedServerUrl.ifEmpty { null },
            lastSuccessAtEpochMillis = teamSync.lastSuccessAt?.toEpochMilliseconds(),
            lastFailure = teamSync.lastFailureMessage?.takeIf { teamSync.lastFailureAt != null && (teamSync.lastSuccessAt == null || teamSync.lastFailureAt > teamSync.lastSuccessAt) }
        ),
        web = when (webStatus) {
            WebAccessStatus.Stopped -> CollectorWebHealth()
            is WebAccessStatus.Running -> CollectorWebHealth("running", webStatus.port, webStatus.addresses)
            is WebAccessStatus.Failed -> CollectorWebHealth("failed", failure = webStatus.message)
        }
    )
}
