package com.usagemonitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import androidx.compose.runtime.rememberCoroutineScope
import com.usagemonitor.presentation.ui.HudAppBalloonContent
import com.usagemonitor.presentation.ui.HudCountdown
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.components.CardAction
import com.usagemonitor.presentation.ui.components.CardActionButton
import com.usagemonitor.presentation.ui.components.CardIconActionButton
import com.usagemonitor.presentation.ui.components.RefreshGlyph
import com.usagemonitor.presentation.ui.components.cardActionsFor
import com.usagemonitor.presentation.ui.components.refreshActionLabel
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.presentation.ui.components.FooterActionGroup
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.launch
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.hudFallbackLabel
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.nextRefreshLabel
import com.usagemonitor.presentation.ui.components.toneFor
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.rememberHudPresence
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.ui.updateBannerAction
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import java.awt.MouseInfo
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import com.usagemonitor.domain.entity.ApiUsageStats
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A barra HUD numa janela **própria**, em forma de notch colado a uma borda.
 *
 * É a **única** janela de visualização do app: a janela principal com o
 * dashboard saiu quando a HUD virou o único modo. Por isso ela é também a âncora
 * do app — diálogo de arquivo, captura do relatório de bug, ativação pela bandeja
 * e pela segunda instância —, entregue por [onWindowReady]. Antes disso ela foi a
 * janela principal encolhida, com a geometria de antes guardada à parte e a
 * janela AWT redimensionada a cada quadro — a fonte do tranco.
 *
 * **Janela transparente, e ela engole clique na área vazia** (medido no Windows
 * 11, C11 do plano de execução). Por isso, parada, ela só aceita clique no notch:
 * a janela tem sempre o tamanho da aberta (`hudDockedWindowBounds`) e a área de
 * clique é recortada por `Window.shape` (`hudRestHitRegion`). Ao entrar, o
 * recorte sai e o balão entra; ao sair, o balão some e só depois o recorte volta.
 *
 * A janela **não muda de origem nem de tamanho** entre parada e aberta. Mudar a
 * origem de uma janela transparente mostra um quadro do conteúdo antigo no lugar
 * novo — era o pisca ao passar o ponteiro (E11) e, na borda direita e na de
 * baixo, o notch saltando 274px a cada abrir e fechar (issue #294).
 *
 * **O recorte é do Windows sempre, e do Linux só sem alfa** ([hudHitRegionMode],
 * #426). No elementary OS 6.1 (X11)
 * o `shape = null` da abertura não tirava o recorte: o balão pintava só dentro
 * da margem de 16dp e as alças saíam cortadas. Fora do Windows a janela fica sem
 * recorte — o balão abre inteiro, e a área vazia dele pode engolir clique.
 */
@Composable
internal fun HudWindowHost(
    settings: PreferencesSettings,
    viewModel: DashboardViewModel,
    usageAlertViewModel: UsageAlertViewModel,
    cardOrder: List<UsageTargetKey>,
    language: AppLanguage,
    themePreset: AppThemePreset,
    uiScalePercent: Int,
    motion: AppMotionPolicy,
    windowOpacityPercent: Int,
    iconImage: Painter?,
    /**
     * O mesmo ícone em `java.awt.Image`. No Linux a janela da HUD é construída
     * direto na AWT (#426), e ali o ícone não é um `Painter`.
     */
    windowIconImage: java.awt.Image?,
    hudScreenArea: ScreenWorkArea,
    /**
     * A janela AWT, uma vez composta. Quem recebe faz o que a janela principal
     * fazia ao nascer: registrar o arranque e confirmar a atualização.
     */
    onWindowReady: suspend (java.awt.Window) -> Unit,
    /** As ações do rodapé, que aqui moram no balão da engrenagem. */
    actions: AppShellActions,
    /** Perfis marcados como parte do time: decidem os botões de time no balão. */
    teamEnabledProfileIds: Set<String>,
    cliSessionPulses: Map<UsageTargetKey, SessionPulse>,
    teamSessionPulses: Map<UsageTargetKey, SessionPulse>,
    /** A cor escolhida por conta Claude (issue #275), por `profileId`. */
    accountColors: Map<String, AccountAccent> = emptyMap(),
    /** O emoji escolhido por conta Claude (issue #287), por `profileId`. */
    accountEmojis: Map<String, AccountEmoji> = emptyMap(),
    /**
     * Encerra o app (`shutdownApplication` do `Main.kt`): o fechar da janela e o
     * "Sair do Usage Monitor" do balão da engrenagem, a saída sem bandeja (#416).
     */
    onCloseRequest: () -> Unit,
    /** Alvos com turno de sessão CLI nos últimos 5 min; acende o arco que gira. */
    activeTargets: StateFlow<Set<UsageTargetKey>>? = null,
    /** Sessões sem resposta desde o último pedido; viram sinal no balão da conta (#265). */
    stalledSessions: StateFlow<List<StalledCliSession>>? = null
) {
    val snapshot by usageAlertViewModel.worstSnapshot.collectAsState()
    val quotaRisks by usageAlertViewModel.quotaRisks.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val nextRefreshAt by viewModel.nextRefreshAt.collectAsState()
    val pollInterval by viewModel.currentPollInterval.collectAsState()
    val dashboardState by viewModel.uiState.collectAsState()
    val refreshingTargets by viewModel.refreshingTargets.collectAsState()
    val exportScope = rememberCoroutineScope()
    val active = activeTargets?.collectAsState()?.value.orEmpty()
    val stalled = stalledSessions?.collectAsState()?.value.orEmpty()

    val fallbackTone = snapshot?.let { worst -> toneFor(worst.risk.level) } ?: AppTone.NEUTRAL
    val fallbackLabel = hudFallbackLabel(dashboardState is UiState.NoApisEnabled, language)
    // A faixa de atualização do modo padrão não existe aqui. O aviso vira o ponto
    // da engrenagem e o banner do balão dela, com o mesmo texto e tom de
    // `updateBannerContent` (#225, #291).
    val updateIndicator = appUpdateState?.let { state -> hudUpdateIndicatorOf(state, language) }
    // A mesma ação da faixa, oferecida no balão da engrenagem — nunca no ícone do
    // notch, onde seria clique de rotina reiniciando o app.
    val updateAction = appUpdateState?.let { state ->
        updateBannerAction(
            state = state,
            onOpenRelease = { viewModel.openUpdateReleasePage() },
            onRestartAndUpdate = { viewModel.restartAndUpdateNow() }
        )
    }
    // Conta que nasce ou colapsa (API ativada/desativada, início do app) fica na
    // lista durante a transição; a geometria segue essa lista.
    val accounts = rememberHudPresence(buildHudAccounts(
        quotaRisks = quotaRisks,
        cardOrder = cardOrder,
        language = language,
        now = Clock.System.now(),
        activeTargets = active,
        refreshingTargets = refreshingTargets,
        accountColors = accountColors,
        accountEmojis = accountEmojis,
        sessionPulses = cliSessionPulses,
        stalledSessions = stalled
    ), motion)

    // O monitor do notch: área de encaixe e id da configuração (#273, #426, #430).
    val screen = remember { HudScreenSelection(settings, hudScreenArea) }
    val screenArea = screen.area
    var placement by remember { mutableStateOf(readPersistedHudPlacement(settings, screenArea)) }
    var hovered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // A área de clique da janela inteira, sem o recorte do notch parado. Sai
    // **antes** do conteúdo ao abrir e volta **depois** dele ao fechar — ver o KDoc.
    var windowOpen by remember { mutableStateOf(false) }
    val drag = remember { HudDragState() }
    val retract = remember { HudAutoRetractState(settings) }
    // Durante o arrasto o notch sai da borda e anda livre; esta é a posição da
    // janela (dp de janela). Nulo fora de arrasto.
    var dragWindowPosition by remember { mutableStateOf<DpOffset?>(null) }
    var dragPointer by remember { mutableStateOf<java.awt.Point?>(null) }

    LaunchedEffect(hovered, drag.dragging) {
        if (drag.dragging) return@LaunchedEffect
        if (hovered) {
            delay(hudOpenDelayMillis(retract.enabled, alreadyOpen = windowOpen))
            screen.reload(hudScreenArea)
            windowOpen = true
            // Um quadro para o recorte sair antes de o conteúdo começar a se
            // abrir; sem ele a mola corre nos primeiros quadros dentro do recorte
            // do notch e o balão sai cortado.
            withFrameNanos { }
            expanded = true
        } else {
            // Recolher espera uma passada: o ponteiro cruzando a divisa entre o
            // notch e o painel gera um `Exit` de um quadro.
            delay(HUD_COLLAPSE_DELAY_MILLIS)
            expanded = false
            delay(HUD_COLLAPSE_SETTLE_MILLIS)
            windowOpen = false
        }
    }

    val scale = uiScaleFactor(uiScalePercent)
    val (sizes, settledSizes) = hudWindowSizes(
        accounts = accounts,
        placement = placement,
        screenArea = screenArea,
        scale = scale,
        fallbackLabel = fallbackLabel,
        hasUpdateIndicator = updateIndicator != null,
        hasUpdateAction = updateAction != null
    )
    val composedArea = screenArea.inCompositionDp(scale)
    val bounds = hudHostWindowBounds(drag.dragging, placement, sizes, composedArea)
    val hitRegionMode = remember { hudHitRegionMode(AutoStartManager.currentPlatform()) }
    val restHitRegion = if (drag.dragging || windowOpen) {
        null
    } else {
        hudRestHitRegion(placement.edge, bounds, sizes, retracted = retract.enabled)
    }
    val windowSize = DpSize(bounds.size.width * scale, bounds.size.height * scale)
    val docked = WindowPosition(bounds.x * scale, bounds.y * scale)

    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = windowSize,
        position = docked
    )
    // Um salto, nunca interpolação AWT: o movimento é do conteúdo.
    LaunchedEffect(windowSize, docked, drag.windowPosition) {
        windowState.size = windowSize
        val free = drag.windowPosition
        windowState.position = if (free != null) WindowPosition(free.x, free.y) else docked
    }

    // A janela de arrasto, que é o que se move (issue #288). O gesto guarda os
    // lambdas da composição em que começou, com o notch **aberto**: com
    // `windowSize` ali, o primeiro passo prendia à tela uma janela da largura do
    // balão e a jogava 274dp para dentro, e o vão seguia o arrasto inteiro —
    // embaixo e à direita, onde a aberta é recuada. O encaixe lia o centro dessa
    // mesma largura errada. Esta medida sai da geometria e não muda no arrasto.
    val dragStart = hudDragWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    val dragSize = DpSize(dragStart.size.width * scale, dragStart.size.height * scale)

    // Estado e contas do gesto moram em `HudWindowDrag.kt`, onde são testáveis.
    val dragBegin = {
        expanded = false
        windowOpen = false
        drag.begin(DpOffset(dragStart.x * scale, dragStart.y * scale))
    }
    val dragTo = { drag.moveTo(dragSize, pointerScreen()?.bounds ?: screen.area) }
    val dragFinish = {
        val position = drag.finish()
        if (position != null) {
            // Borda, fração e monitor do ponto em que foi solto são gravados.
            val target = pointerScreen()
            val snapped = hudDragLanding(position, dragSize, screen.area, target)
            if (target != null) {
                screen.moveTo(target)
            }
            placement = snapped
            persistHudPlacement(settings, snapped)
        }
        Unit
    }

    // A janela nasce na configuração **com alfa** do monitor em que a HUD vive,
    // e renasce quando esse monitor muda (#426). Remendar a janela aberta
    // devolveria o alfa recriando a janela nativa, e o Compose para de emitir
    // quadros depois disso — a barra fica correta e congelada.
    val rebirth = rememberHudWindowRebirth(screen.id, AutoStartManager.currentPlatform())
    key(rebirth.configuration, rebirth.generation) {
    HudWindowFrame(
        useExplicitConfiguration = rebirth.explicit,
        graphicsConfiguration = rebirth.configuration,
        state = windowState,
        size = windowSize,
        // A posição desta composição: no arrasto, onde o gesto está; fora dele,
        // a encaixada. O `windowState` só recebe isto no efeito abaixo, tarde
        // demais para a janela que renasce (#426).
        position = drag.windowPosition?.let { free -> WindowPosition(free.x, free.y) } ?: docked,
        icon = iconImage,
        iconImage = windowIconImage,
        onCloseRequest = onCloseRequest,
        onKeyEvent = { event ->
            handleHudWindowKey(type = event.type, key = event.key, onOpenHelp = actions.openHelp)
        },
        dragging = drag.dragging
    ) {
        HudWindowEffects(
            window = window,
            windowState = windowState,
            onWindowReady = onWindowReady,
            windowOpacityPercent = windowOpacityPercent,
            hitRegionMode = hitRegionMode,
            restHitRegion = restHitRegion,
            scale = scale,
            platform = AutoStartManager.currentPlatform(),
            dragging = drag.dragging,
            // No COSMIC, renasce no monitor onde o compositor pôs a janela (#430).
            onAlphaLost = { state -> rebirth.onAlphaLost(state, drag.dragging, screen.id, onAdopt = screen::moveTo) }
        )
        AppTheme(preset = themePreset, uiScalePercent = uiScalePercent, motion = motion) {
            val edge = placement.edge
            val centerInWindow = if (drag.dragging) null else bounds.notchCenterInWindow
            Box(modifier = Modifier.fillMaxSize()) {
                HudNotch(
                    accounts = accounts,
                    edge = edge,
                    sizes = sizes,
                    settledSizes = settledSizes,
                    fallbackLabel = fallbackLabel,
                    fallbackTone = fallbackTone,
                    expanded = expanded && !drag.dragging,
                    dragging = drag.dragging,
                    updateIndicator = updateIndicator,
                    notchCenter = centerInWindow,
                    language = language,
                    onHoverChange = { isHovered -> hovered = isHovered },
                    onDragStart = dragBegin,
                    onDragMove = dragTo,
                    onDragEnd = dragFinish,
                    // Clique num anel recoleta aquela conta, como no Codenotch.
                    onRefreshAccount = { target -> viewModel.refresh(target) },
                    // Os botões do card daquela conta, pela mesma regra do card.
                    accountActions = { account ->
                        HudAccountActions(
                            account = account,
                            language = language,
                            teamEnabledProfileIds = teamEnabledProfileIds,
                            cliSessionPulse = cliSessionPulses[account.targetKey] ?: SessionPulse.EMPTY,
                            teamSessionPulse = teamSessionPulses[account.targetKey] ?: SessionPulse.EMPTY,
                            actions = actions,
                            onRefresh = { viewModel.refresh(account.targetKey) }
                        )
                    },
                    // A engrenagem da ponta de longe abre o balão com o que o
                    // rodapé do modo padrão oferece — aqui não há rodapé.
                    appBalloon = {
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
                    },
                    appBalloonHeight = hudAppBalloonHeight(
                        hasUpdateIndicator = updateIndicator != null,
                        hasUpdateAction = updateAction != null
                    ),
                    gearDescription = hudGearDescription(language),
                    autoRetract = retract.enabled,
                    onToggleAutoRetract = retract::toggle,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    }
}

/**
 * O balão da engrenagem: o que o rodapé do modo padrão oferece — aqui não há
 * rodapé —, mais a contagem, o aviso de atualização e "Sair do Usage Monitor".
 *
 * [exportScope] é do host, não deste balão: o balão sai da composição quando o
 * ponteiro deixa a HUD, e um escopo dele cancelaria a exportação no meio.
 */
@Composable
private fun HudWindowAppBalloon(
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
private fun HudAccountActions(
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

private val HUD_BALLOON_ACTION_ICON = 16.dp

/** Uma passada de hover: o `Exit` de um quadro na divisa não fecha o painel. */
private const val HUD_COLLAPSE_DELAY_MILLIS = 150L

/**
 * A saída do balão (o jato dobrando, [AppGargantuaTokens.jetCloseMillis]) com
 * folga; a janela encolhe e o recorte volta depois dela, senão o recorte do
 * notch corta a pintura do fechamento.
 */
private const val HUD_COLLAPSE_SETTLE_MILLIS = AppGargantuaTokens.jetCloseMillis + 60L

/** O monitor sob o ponteiro; `null` se o AWT não souber dizer. */
private fun pointerScreen(): ScreenInfo? =
    runCatching { MouseInfo.getPointerInfo()?.device?.toScreenInfo() }.getOrNull()

internal fun applyHudWindowOpacity(window: java.awt.Window, percent: Int, platform: AutoStartManager.Platform) {
    applyWindowOpacity(window, hudWindowOpacityPercent(percent, platform))
    if (hudRepaintsAfterOpacityChange(platform)) {
        window.repaint()
    }
}

