package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudAppBalloonContent
import com.usagemonitor.presentation.ui.HudCountdown
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.CardAction
import com.usagemonitor.presentation.ui.components.CardActionButton
import com.usagemonitor.presentation.ui.components.CardIconActionButton
import com.usagemonitor.presentation.ui.components.FooterActionGroup
import com.usagemonitor.presentation.ui.components.RefreshGlyph
import com.usagemonitor.presentation.ui.components.cardActionsFor
import com.usagemonitor.presentation.ui.components.nextRefreshLabel
import com.usagemonitor.presentation.ui.components.refreshActionLabel
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val HUD_BALLOON_ACTION_ICON: Dp = 16.dp

/**
 * O balão da engrenagem: o que o rodapé do modo padrão oferece — aqui não há
 * rodapé —, mais a contagem, o aviso de atualização e "Sair do Usage Monitor".
 *
 * [exportScope] é do host, não deste balão: o balão sai da composição quando o
 * ponteiro deixa a HUD, e um escopo dele cancelaria a exportação no meio.
 */
@Composable
internal fun HudWindowAppBalloon(
    language: AppLanguage,
    nextRefreshAt: Instant?,
    refreshInterval: Duration,
    updateIndicator: HudUpdateIndicator?,
    updateAction: (() -> Unit)?,
    actions: AppShellActions,
    exportScope: CoroutineScope,
    currentStats: () -> List<ApiUsageStats>?,
    onQuit: () -> Unit
) {
    HudAppBalloonContent(
        language = language,
        appVersion = CURRENT_APP_VERSION,
        countdown = nextRefreshAt?.let { refreshAt ->
            {
                HudCountdown(
                    nextRefreshAt = refreshAt,
                    description = nextRefreshLabel(language),
                    interval = refreshInterval,
                    nowProvider = { Clock.System.now() },
                    waitNextTick = { delay(1_000L) },
                    updatesEnabled = true
                )
            }
        },
        updateIndicator = updateIndicator,
        onUpdateAction = updateAction,
        onQuit = onQuit,
        actions = {
            FooterActionGroup(
                language = language,
                onRefresh = actions.refreshAll,
                onOpenSettings = actions.openSettings,
                onOpenAdminOverview = actions.openAdminOverview,
                onOpenTeamPresence = actions.openTeamPresenceOverview,
                onOpenHelp = actions.openHelp,
                onOpenComparison = actions.openComparison,
                onExportSnapshot = {
                    val stats = currentStats()
                    if (stats != null) {
                        // Sem snackbar aqui: o diálogo de arquivo é o retorno.
                        exportScope.launch {
                            runCatching { actions.exportSnapshot(stats) }
                                .onFailure { error -> actions.onExportFailure(error) }
                        }
                    }
                }
            )
        }
    )
}

/**
 * Os botões do card de uma conta, no balão dela: as janelas de
 * `cardActionsFor` — a mesma regra do card — e, por último, atualizar só esta
 * conta, com o glifo que gira enquanto coleta.
 */
@Composable
internal fun HudAccountActions(
    account: HudAccount,
    language: AppLanguage,
    teamEnabledProfileIds: Set<String>,
    cliSessionPulse: SessionPulse,
    teamSessionPulse: SessionPulse,
    actions: AppShellActions,
    onRefresh: () -> Unit
) {
    val target = account.targetKey
    cardActionsFor(target, teamEnabledProfileIds).forEach { action ->
        CardActionButton(
            action = action,
            language = language,
            buttonSize = HUD_BALLOON_ACTIONS,
            iconSize = HUD_BALLOON_ACTION_ICON,
            cliSessionPulse = cliSessionPulse,
            teamSessionPulse = teamSessionPulse,
            onClick = {
                when (action) {
                    CardAction.HISTORY -> actions.openHistory(target.source, account.accountKey)
                    CardAction.ANALYSIS -> actions.openAnalysis(target)
                    CardAction.CODEX_CLI_SESSIONS -> actions.openCodexCliSessions(target)
                    CardAction.CLI_SESSIONS -> actions.openCliSessions(target)
                    CardAction.TEAM_USAGE -> actions.openTeamUsage(target)
                    CardAction.TEAM_PRESENCE -> actions.openTeamPresence(target)
                }
            }
        )
    }
    CardIconActionButton(
        label = refreshActionLabel(account.refreshing, language),
        onClick = onRefresh,
        buttonSize = HUD_BALLOON_ACTIONS,
        enabled = !account.refreshing
    ) { tint ->
        RefreshGlyph(refreshing = account.refreshing, tint = tint, size = HUD_BALLOON_ACTION_ICON)
    }
}
