package com.usagemonitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
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
 * **O recorte é só do Windows** ([hudUsesHitRegion]). No elementary OS 6.1 (X11)
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

    // O monitor do notch (issue #273). Era sempre o padrão: o arrasto era preso a
    // ele e o encaixe usava as bordas dele, e o notch não saía do primário. Agora
    // é o monitor gravado, resolvido de novo a cada abertura por hover — um
    // monitor reconectado volta a receber o notch sem reiniciar o app.
    var screenArea by remember { mutableStateOf(resolveHudScreenArea(settings, hudScreenArea)) }
    var placement by remember { mutableStateOf(readPersistedHudPlacement(settings, screenArea)) }
    var hovered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // A área de clique da janela inteira, sem o recorte do notch parado. Sai
    // **antes** do conteúdo ao abrir e volta **depois** dele ao fechar — ver o KDoc.
    var windowOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // Durante o arrasto o notch sai da borda e anda livre; esta é a posição da
    // janela (dp de janela). Nulo fora de arrasto.
    var dragWindowPosition by remember { mutableStateOf<DpOffset?>(null) }
    var dragPointer by remember { mutableStateOf<java.awt.Point?>(null) }

    LaunchedEffect(hovered, dragging) {
        if (dragging) {
            return@LaunchedEffect
        }
        if (hovered) {
            screenArea = resolveHudScreenArea(settings, hudScreenArea)
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
    // A janela mede a lista com quem está saindo, até a vaga fechar; o notch
    // recolhe até a lista sem ela por dentro da janela (K1).
    val (sizes, settledSizes) = hudNotchSizesWithDeparture(
        accounts = accounts,
        edge = placement.edge,
        fallbackLabel = fallbackLabel,
        hasUpdateIndicator = updateIndicator != null,
        // Mais que isso da borda e a faixa fica compacta (anel + percentual).
        maxAlong = (if (placement.edge.isHorizontal) screenArea.size.width else screenArea.size.height) /
            uiScaleFactor(uiScalePercent) * hudMaxAlongFraction(placement.edge),
        hasUpdateAction = updateAction != null,
        maxWindowHeight = screenArea.size.height / scale
    )
    val composedArea = screenArea.inCompositionDp(scale)
    // Parada e aberta a janela é a mesma, com o notch no mesmo ponto da tela; quem
    // se ajusta a um canto é o balão. Parada, só o notch aceita clique.
    // Carregando, a janela é o notch com as alças — simétrico ao longo da borda,
    // então o centro dela continua sendo o do notch, que é o que o encaixe lê.
    val bounds = if (dragging) {
        hudDragWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    } else {
        hudDockedWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    }
    val hitRegionSupported = remember { hudUsesHitRegion(AutoStartManager.currentPlatform()) }
    val hitRegion = if (dragging || windowOpen) null else hudRestHitRegion(placement.edge, bounds, sizes)
    val windowSize = DpSize(bounds.size.width * scale, bounds.size.height * scale)
    val docked = WindowPosition(bounds.x * scale, bounds.y * scale)

    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = windowSize,
        position = docked
    )
    // Um salto, nunca interpolação AWT: o movimento é do conteúdo.
    LaunchedEffect(windowSize, docked, dragWindowPosition) {
        windowState.size = windowSize
        val free = dragWindowPosition
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

    val dragBegin = {
        dragging = true
        expanded = false
        windowOpen = false
        // O arrasto parte da janela de arrasto já encaixada, com o notch no ponto
        // da tela em que ele está — nunca da origem da janela aberta.
        dragWindowPosition = DpOffset(dragStart.x * scale, dragStart.y * scale)
        dragPointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
    }
    val dragTo = {
        val previous = dragPointer
        val current = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        val position = dragWindowPosition
        if (previous != null && current != null && position != null) {
            dragPointer = current
            // Incremental: somar o total desde o início deixaria o notch preso na
            // borda, porque o excedente de um arrasto para fora da tela nunca
            // seria descartado. A área é a do monitor **sob o ponteiro**, a tela
            // inteira dele: presa ao primário, o notch nunca cruzava a divisa.
            val moved = fitWindowPosition(
                x = position.x + (current.x - previous.x).dp,
                y = position.y + (current.y - previous.y).dp,
                size = dragSize,
                workArea = pointerScreen()?.bounds ?: screenArea
            )
            dragWindowPosition = DpOffset(moved.x, moved.y)
        }
    }
    val dragFinish = {
        val position = dragWindowPosition
        if (position != null) {
            // O notch gruda na borda mais próxima do **centro** dele, no monitor
            // em que foi solto, e borda, fração e monitor são gravados. O encaixe
            // é na área **útil**: a barra de tarefas também é *topmost* e volta
            // para cima do notch a cada clique nela (issue #288).
            val target = pointerScreen()
            val area = target?.workArea ?: screenArea
            val snapped = nearestHudPlacement(
                centerX = position.x + dragSize.width / 2,
                centerY = position.y + dragSize.height / 2,
                area = area
            )
            if (target != null) {
                screenArea = target.workArea
                persistHudScreen(settings, target)
            }
            placement = snapped
            persistHudPlacement(settings, snapped)
        }
        dragPointer = null
        dragWindowPosition = null
        dragging = false
        Unit
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Usage Monitor",
        icon = iconImage,
        state = windowState,
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onKeyEvent = { event ->
            handleHudWindowKey(event = event, onOpenHelp = actions.openHelp)
        }
    ) {
        LaunchedEffect(window) {
            onWindowReady(window)
        }
        LaunchedEffect(windowOpacityPercent) {
            applyHudWindowOpacity(window, windowOpacityPercent, AutoStartManager.currentPlatform())
        }
        // Recorte só no Windows: no elementary OS (X11) o balão saía cortado.
        ApplyHudHitRegion(window, hitRegion, scale, supported = hitRegionSupported)
        AppTheme(preset = themePreset, uiScalePercent = uiScalePercent, motion = motion) {
            val edge = placement.edge
            val centerInWindow = if (dragging) null else bounds.notchCenterInWindow
            Box(modifier = Modifier.fillMaxSize()) {
                HudNotch(
                    accounts = accounts,
                    edge = edge,
                    sizes = sizes,
                    settledSizes = settledSizes,
                    fallbackLabel = fallbackLabel,
                    fallbackTone = fallbackTone,
                    expanded = expanded && !dragging,
                    dragging = dragging,
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
                            currentStats = { (dashboardState as? UiState.Success)?.data }
                        )
                    },
                    appBalloonHeight = hudAppBalloonHeight(
                        hasUpdateIndicator = updateIndicator != null,
                        hasUpdateAction = updateAction != null
                    ),
                    gearDescription = hudGearDescription(language),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}



private fun hudUpdateIndicatorOf(state: AppUpdateUiState, language: AppLanguage): HudUpdateIndicator {
    val content = updateBannerContent(state = state, language = language)
    return HudUpdateIndicator(
        tone = content.tone,
        description = content.title,
        actionLabel = content.actionLabel,
        headline = content.headline,
        detail = content.detail
    )
}

/** `F1` abre a ajuda, a tecla que o sistema reserva para ela. */
private fun handleHudWindowKey(event: KeyEvent, onOpenHelp: () -> Unit): Boolean {
    val help = event.type == KeyEventType.KeyDown && event.key == Key.F1
    if (help) {
        onOpenHelp()
    }
    return help
}

/**
 * O balão da engrenagem: o que o rodapé do modo padrão oferece — aqui não há
 * rodapé —, mais a contagem e o aviso de atualização.
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
    currentStats: () -> List<ApiUsageStats>?
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

/** A engrenagem abre as ações do app; é o que ela diz ao leitor de tela. */
internal fun hudGearDescription(language: AppLanguage): String =
    if (language == AppLanguage.PT) "Ações do Usage Monitor" else "Usage Monitor actions"

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

/**
 * A área útil do monitor gravado para o notch; sem gravação, ou com o monitor
 * desligado, a do padrão. O que está gravado **não é apagado** quando o monitor
 * some: ele pode voltar.
 *
 * Área **útil**, e não a tela inteira (issue #288). A #256 deixou o notch ocupar
 * a faixa da barra de tarefas, mas no Windows ela também é *topmost* e volta
 * para cima de toda janela *topmost* a cada clique, hover ou notificação: o
 * `alwaysOnTop` perde essa disputa, e o notch de baixo ficava meio coberto. O
 * monitor continua identificado pelos limites **inteiros** (`persistHudScreen`),
 * que não mudam quando a barra é movida.
 */
private fun resolveHudScreenArea(settings: PreferencesSettings, fallback: ScreenWorkArea): ScreenWorkArea {
    val saved = readPersistedHudScreen(settings)
    val screens = availableScreens()
    return resolveScreen(screens, saved.id, saved.bounds)?.workArea ?: screens.firstOrNull()?.workArea ?: fallback
}

/**
 * A área da tela na escala da composição. A geometria trabalha em dp de
 * composição; a janela, em dp do sistema. A área desce à escala da composição e o
 * resultado da geometria volta multiplicado.
 */
private fun ScreenWorkArea.inCompositionDp(scale: Float): ScreenWorkArea = ScreenWorkArea(
    x = x / scale,
    y = y / scale,
    size = DpSize(size.width / scale, size.height / scale)
)

/**
 * Se a HUD recorta a área de clique da janela parada por `Window.shape`. Só no
 * Windows, onde o recorte foi medido (C11, spike da #294); no Linux (X11) tirar
 * o recorte ao abrir não surtia efeito e o balão ficava cortado. macOS nunca foi
 * medido e fica do lado seguro.
 */
internal fun hudUsesHitRegion(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.WINDOWS

/**
 * A opacidade que a HUD usa, a partir da preferência do usuário.
 *
 * No Windows a janela transparente do Compose só recebe o mouse onde o fundo tem
 * alfa 1/255 (`JLayeredPaneWithTransparencyHack`), e o sistema multiplica esse alfa
 * pela opacidade da janela: em 50% dá 1 × 127/255, que arredonda para zero, e o
 * ponteiro atravessa a HUD — o hover some. Medido no Windows 11 com uma janela
 * transparente igual à da HUD: 50% nunca recebe o mouse; 55%, 60%… 99% recebem.
 * Por isso o piso de [HUD_WINDOWS_MIN_OPACITY_PERCENT] só no Windows; fora dele o
 * fundo de alfa 1/255 não existe e a preferência vale inteira (issue #340).
 */
internal fun hudWindowOpacityPercent(percent: Int, platform: AutoStartManager.Platform): Int {
    val clamped = clampWindowOpacityPercent(percent)
    if (platform != AutoStartManager.Platform.WINDOWS) {
        return clamped
    }
    return maxOf(clamped, HUD_WINDOWS_MIN_OPACITY_PERCENT)
}

/**
 * Se a HUD precisa ser repintada depois de mudar a opacidade. No Windows, mudar a
 * opacidade refaz a camada da janela sem o fundo de alfa 1/255 e o mouse passa a
 * atravessá-la até a próxima pintura AWT: medido, 100 → 50 → 100 sem repintar fica
 * sem hover; repintando, volta. Era o hover que só voltava saindo e entrando no
 * modo HUD.
 */
internal fun hudRepaintsAfterOpacityChange(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.WINDOWS

private fun applyHudWindowOpacity(window: java.awt.Window, percent: Int, platform: AutoStartManager.Platform) {
    applyWindowOpacity(window, hudWindowOpacityPercent(percent, platform))
    if (hudRepaintsAfterOpacityChange(platform)) {
        window.repaint()
    }
}

/** O menor valor medido em que a HUD transparente ainda recebe o mouse no Windows. */
internal const val HUD_WINDOWS_MIN_OPACITY_PERCENT = 55

/**
 * Síncrono na aplicação da composição: o quadro seguinte, que o hover espera
 * antes de abrir o balão, já sai sem o recorte.
 *
 * Fora do Windows ([supported] falso) não aplica nada — só grava que pulou, para
 * o próximo relato vir com o que a HUD fez (issue #342).
 */
@Composable
private fun ApplyHudHitRegion(window: java.awt.Window, region: DpRect?, scale: Float, supported: Boolean) {
    val diagnostics = remember { HudWindowDiagnostics() }
    val applier = remember { HudHitRegionApplier(onEvent = diagnostics::record) }
    LaunchedEffect(supported) {
        val event = if (supported) HudHitRegionEvent.ENABLED else HudHitRegionEvent.SKIPPED
        withContext(Dispatchers.IO) { diagnostics.record(event) }
    }
    if (!supported) {
        return
    }
    SideEffect {
        applier.apply(window, region, scale)
    }
}

/**
 * Aplica o recorte de clique da janela parada, ou o tira com a região nula.
 *
 * Guarda o último retângulo aplicado porque `Window.getShape()` devolve uma cópia
 * em `Path2D`, que nunca é igual ao `Rectangle` pedido: comparar por ela
 * refaria a região da janela no sistema a cada recomposição.
 *
 * `Window.shape` exige suporte a janela recortada (`PERPIXEL_TRANSPARENT`). Sem
 * ele a HUD continua funcionando sem recorte: a área do balão volta a engolir
 * clique, mas o notch não pisca — o defeito menor dos dois. Fora do Windows o
 * host nem o compõe ([hudUsesHitRegion]).
 */
private class HudHitRegionApplier(
    private val onEvent: (HudHitRegionEvent, String?) -> Unit
) {
    private var applied: java.awt.Rectangle? = null
    private var hasApplied = false

    fun apply(window: java.awt.Window, region: DpRect?, scale: Float) {
        val shape = region?.let { rect ->
            val left = floor(rect.left.value * scale).toInt()
            val top = floor(rect.top.value * scale).toInt()
            val right = ceil(rect.right.value * scale).toInt()
            val bottom = ceil(rect.bottom.value * scale).toInt()
            java.awt.Rectangle(left, top, right - left, bottom - top)
        }
        if (hasApplied && shape == applied) {
            return
        }
        hasApplied = true
        applied = shape
        runCatching { window.shape = shape }
            .onFailure { error -> onEvent(HudHitRegionEvent.SET_FAILED, "${error::class.simpleName}: ${error.message}") }
            .onSuccess {
                if (shape == null && window.shape != null) {
                    onEvent(HudHitRegionEvent.CLEAR_NOT_EFFECTIVE, null)
                }
            }
    }
}
