package com.usagemonitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudMinimizeProgress
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.CoroutineScope

/**
 * O conteúdo da janela da HUD, já dentro do `AppTheme`: o notch parado ou a
 * flutuante (#619), com os balões e os botões que as duas compartilham.
 *
 * O host decide o estado (abertura, arrasto, geometria) e entrega pronto; aqui
 * só se monta a árvore. Nomes de sessão e de atualização chegam como parâmetro;
 * o que o balão da engrenagem lê do view model (próxima coleta, resultado da
 * última leitura) é lido aqui, na composição que o mostra.
 */
@Composable
internal fun HudWindowContent(
    floating: Boolean,
    expanded: Boolean,
    dragging: Boolean,
    accounts: List<HudAccount>,
    edge: HudEdge,
    notchCenter: Dp?,
    sizes: HudNotchSizes,
    settledSizes: HudNotchSizes?,
    minimize: HudMinimizeProgress,
    fallbackLabel: String,
    fallbackTone: AppTone,
    updateIndicator: HudUpdateIndicator?,
    updateAction: (() -> Unit)?,
    language: AppLanguage,
    viewModel: DashboardViewModel,
    actions: AppShellActions,
    exportScope: CoroutineScope,
    onCloseRequest: () -> Unit,
    teamEnabledProfileIds: Set<String>,
    cliSessionPulses: Map<UsageTargetKey, SessionPulse>,
    teamSessionPulses: Map<UsageTargetKey, SessionPulse>,
    toggleMinimize: (UsageTargetKey) -> Unit,
    detachedAlongShift: Dp,
    appBalloonHeight: Dp,
    autoRetract: Boolean,
    onToggleAutoRetract: () -> Unit,
    onToggleFloating: () -> Unit,
    onHoverChange: (Boolean) -> Unit,
    onDragStart: () -> Unit,
    onDragMove: () -> Unit,
    onDragEnd: () -> Unit,
    onReorderAccounts: (List<UsageTargetKey>) -> Unit
) {
    val nextRefreshAt by viewModel.nextRefreshAt.collectAsState()
    val pollInterval by viewModel.currentPollInterval.collectAsState()
    val dashboardState by viewModel.uiState.collectAsState()
    // Os balões e os botões do card, montados uma vez para o notch e para a flutuante (#619).
    val accountActions: @Composable (HudAccount) -> Unit = { account ->
        HudAccountActions(
            account = account,
            language = language,
            teamEnabledProfileIds = teamEnabledProfileIds,
            cliSessionPulse = cliSessionPulses[account.targetKey] ?: SessionPulse.EMPTY,
            teamSessionPulse = teamSessionPulses[account.targetKey] ?: SessionPulse.EMPTY,
            actions = actions,
            onRefresh = { viewModel.refresh(account.targetKey) },
            onToggleMinimize = toggleMinimize
        )
    }
    // A engrenagem da ponta de longe abre o balão com o que o rodapé do modo padrão
    // oferece — aqui não há rodapé.
    val appBalloon: @Composable () -> Unit = {
        HudWindowAppBalloon(
            language = language,
            nextRefreshAt = nextRefreshAt,
            refreshInterval = pollInterval,
            updateIndicator = updateIndicator,
            updateAction = updateAction,
            actions = actions,
            exportScope = exportScope,
            currentStats = { (dashboardState as? UiState.Success)?.data },
            onQuit = onCloseRequest
        )
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HudNotch(
            accounts = accounts,
            edge = edge,
            sizes = sizes,
            settledSizes = settledSizes,
            minimize = minimize,
            fallbackLabel = fallbackLabel,
            fallbackTone = fallbackTone,
            expanded = expanded && !dragging,
            dragging = dragging,
            updateIndicator = updateIndicator,
            notchCenter = notchCenter,
            language = language,
            onHoverChange = onHoverChange,
            onDragStart = onDragStart,
            onDragMove = onDragMove,
            onDragEnd = onDragEnd,
            // Clique num anel recoleta aquela conta, como no Codenotch.
            onRefreshAccount = { target -> viewModel.refresh(target) },
            onReorderAccounts = onReorderAccounts,
            // Os botões do card daquela conta, pela mesma regra do card.
            accountActions = accountActions,
            appBalloon = appBalloon,
            appBalloonHeight = appBalloonHeight,
            gearDescription = hudGearDescription(language),
            autoRetract = autoRetract,
            onToggleAutoRetract = onToggleAutoRetract,
            floatingSupported = hudFloatingSupported(AutoStartManager.currentPlatform()),
            floating = floating,
            onToggleFloating = onToggleFloating,
            // Modo flutuante (#619): o notch desprendido do buraco; encaixado, `detached` é falso.
            detached = floating,
            detachedAlongShift = detachedAlongShift,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * A janela encaixada: tamanho e posição saltam juntos, sem interpolação AWT
 * (o movimento é do conteúdo). Com arrasto livre em curso, a posição é a dele.
 */
@Composable
internal fun rememberHudDockedWindowState(
    windowSize: DpSize,
    docked: WindowPosition,
    freePosition: DpOffset?
): WindowState {
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = windowSize,
        position = docked
    )
    LaunchedEffect(windowSize, docked, freePosition) {
        windowState.size = windowSize
        windowState.position = if (freePosition != null) WindowPosition(freePosition.x, freePosition.y) else docked
    }
    return windowState
}
