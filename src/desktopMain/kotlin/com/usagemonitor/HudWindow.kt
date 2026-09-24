package com.usagemonitor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
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
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.components.FooterActionGroup
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.launch
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.nextRefreshLabel
import com.usagemonitor.presentation.ui.components.toneFor
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import java.awt.MouseInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

/**
 * A barra HUD numa janela **própria**, em forma de notch colado a uma borda.
 *
 * Ela era a janela principal encolhida, e isso obrigava `main()` a guardar a
 * geometria de antes, proibir o coletor de gravar a pílula como "tamanho
 * normal", ordenar textualmente o piso de tamanho e redimensionar a janela AWT a
 * cada quadro — a fonte do tranco. Agora a principal fica escondida com a
 * geometria intacta, e esta janela é só do notch. Mora fora de `main()`, que está
 * no limite do backend JVM.
 *
 * **Janela transparente, e ela engole clique na área vazia** (medido no Windows
 * 11, C11 do plano de execução): por isso ela só tem o tamanho da área aberta
 * enquanto o ponteiro está no notch. Ao entrar, a janela cresce **de uma vez** —
 * a área nova é transparente, o salto não se vê — e o balão entra dentro dela; ao
 * sair, o balão some e só depois a janela encolhe. O notch não anda na tela.
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
    onOpenFull: () -> Unit,
    onSwitchToCardsOnly: () -> Unit,
    /** As ações do rodapé, que aqui moram no balão da engrenagem. */
    actions: AppShellActions,
    onCloseRequest: () -> Unit,
    /** Alvos com turno de sessão CLI nos últimos 5 min; acende o arco que gira. */
    activeTargets: StateFlow<Set<UsageTargetKey>>? = null
) {
    val snapshot by usageAlertViewModel.worstSnapshot.collectAsState()
    val quotaRisks by usageAlertViewModel.quotaRisks.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val nextRefreshAt by viewModel.nextRefreshAt.collectAsState()
    val dashboardState by viewModel.uiState.collectAsState()
    val exportScope = rememberCoroutineScope()
    val active = activeTargets?.collectAsState()?.value.orEmpty()

    val fallbackTone = snapshot?.let { worst -> toneFor(worst.risk.level) } ?: AppTone.NEUTRAL
    val fallbackLabel = if (language == AppLanguage.PT) "Carregando" else "Loading"
    // A faixa de atualização do modo padrão não existe aqui; o indicador ocupa o
    // notch com o mesmo texto e tom de `updateBannerContent` (#225).
    val updateIndicator = appUpdateState?.let { state ->
        val content = updateBannerContent(state = state, language = language)
        HudUpdateIndicator(tone = content.tone, description = content.title)
    }
    val accounts = buildHudAccounts(
        quotaRisks = quotaRisks,
        cardOrder = cardOrder,
        language = language,
        now = Clock.System.now(),
        activeTargets = active
    )

    var placement by remember { mutableStateOf(readPersistedHudPlacement(settings, hudScreenArea)) }
    var hovered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // A janela no tamanho do painel aberto. Anda **antes** do conteúdo ao abrir e
    // **depois** dele ao fechar — ver o KDoc.
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
            windowOpen = true
            // Um quadro para a janela crescer antes de o conteúdo começar a se
            // abrir; sem ele a mola corre nos primeiros quadros dentro da janela
            // pequena e sai recortada.
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
    val sizes = hudNotchSizes(
        accounts = accounts,
        edge = placement.edge,
        fallbackLabel = fallbackLabel,
        showsCountdown = nextRefreshAt != null,
        hasUpdateIndicator = updateIndicator != null
    )
    // A geometria trabalha em dp de composição; a janela, em dp do sistema. A área
    // da tela desce à escala da composição e o resultado volta multiplicado.
    val composedArea = ScreenWorkArea(
        x = hudScreenArea.x / scale,
        y = hudScreenArea.y / scale,
        size = DpSize(hudScreenArea.size.width / scale, hudScreenArea.size.height / scale)
    )
    // Aberta, a janela ganha o espaço do balão e o notch fica no mesmo ponto da
    // tela (`hudOpenWindowBounds`); quem se ajusta a um canto é o balão.
    // Carregando, a janela é o notch com as alças — simétrico ao longo da borda,
    // então o centro dela continua sendo o do notch, que é o que o encaixe lê.
    val bounds = when {
        dragging -> hudWindowBounds(placement.edge, placement.offsetFraction, sizes.withHandles, composedArea)
        windowOpen -> hudOpenWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
        else -> hudRestWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    }
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

    val dragBegin = {
        dragging = true
        expanded = false
        windowOpen = false
        val current = windowState.position
        dragWindowPosition = DpOffset(current.x, current.y)
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
            // seria descartado. Mesma área do encaixe, a tela inteira.
            val moved = fitWindowPosition(
                x = position.x + (current.x - previous.x).dp,
                y = position.y + (current.y - previous.y).dp,
                size = windowSize,
                workArea = hudScreenArea
            )
            dragWindowPosition = DpOffset(moved.x, moved.y)
        }
    }
    val dragFinish = {
        val position = dragWindowPosition
        if (position != null) {
            // O notch gruda na borda mais próxima do **centro** dele, e a fração
            // ao longo dela é gravada — sobrevive a troca de resolução.
            val snapped = nearestHudPlacement(
                centerX = position.x + windowSize.width / 2,
                centerY = position.y + windowSize.height / 2,
                area = hudScreenArea
            )
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
            val isDown = event.type == KeyEventType.KeyDown
            val hudToggle = isDown && event.isCtrlPressed && event.isShiftPressed && event.key == Key.H
            val cardsOnlyToggle = isDown && event.isCtrlPressed && event.isShiftPressed && event.key == Key.M
            val help = isDown && event.key == Key.F1
            when {
                hudToggle -> onOpenFull()
                cardsOnlyToggle -> onSwitchToCardsOnly()
                help -> actions.openHelp()
            }
            hudToggle || cardsOnlyToggle || help
        }
    ) {
        LaunchedEffect(windowOpacityPercent) {
            applyWindowOpacity(window, windowOpacityPercent)
        }
        AppTheme(preset = themePreset, uiScalePercent = uiScalePercent, motion = motion) {
            val edge = placement.edge
            val centerInWindow = if (dragging) null else bounds.notchCenterInWindow
            Box(modifier = Modifier.fillMaxSize()) {
                HudNotch(
                    accounts = accounts,
                    edge = edge,
                    sizes = sizes,
                    fallbackLabel = fallbackLabel,
                    fallbackTone = fallbackTone,
                    expanded = expanded && !dragging,
                    dragging = dragging,
                    updateIndicator = updateIndicator,
                    nextRefreshAt = nextRefreshAt,
                    countdownDescription = nextRefreshLabel(language),
                    notchCenter = centerInWindow,
                    language = language,
                    onHoverChange = { isHovered -> hovered = isHovered },
                    onDragStart = dragBegin,
                    onDragMove = dragTo,
                    onDragEnd = dragFinish,
                    onOpenFull = onOpenFull,
                    // Botão direito (issue #215): direto para "Somente cards".
                    onSwitchToCardsOnly = onSwitchToCardsOnly,
                    // A engrenagem da ponta de longe abre o balão com o que o
                    // rodapé do modo padrão oferece — aqui não há rodapé.
                    appBalloon = {
                        HudAppBalloonContent(
                            language = language,
                            countdown = nextRefreshAt?.let { refreshAt ->
                                {
                                    HudCountdown(
                                        nextRefreshAt = refreshAt,
                                        description = nextRefreshLabel(language),
                                        vertical = false,
                                        nowProvider = { Clock.System.now() },
                                        waitNextTick = { delay(1_000L) },
                                        updatesEnabled = true
                                    )
                                }
                            },
                            updateIndicator = updateIndicator,
                            onWindowModeChange = actions.changeWindowMode,
                            actions = {
                                FooterActionGroup(
                                    language = language,
                                    onRefresh = actions.refreshAll,
                                    onOpenSettings = actions.openSettings,
                                    onOpenAdminOverview = actions.openAdminOverview,
                                    onOpenTeamPresence = actions.openTeamPresenceOverview,
                                    onOpenHelp = actions.openHelp,
                                    onExportSnapshot = {
                                        val stats = (dashboardState as? UiState.Success)?.data
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
                    },
                    appBalloonHeight = hudAppBalloonHeight(hasUpdateIndicator = updateIndicator != null),
                    gearDescription = hudGearDescription(language),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** A engrenagem abre as ações do app; é o que ela diz ao leitor de tela. */
internal fun hudGearDescription(language: AppLanguage): String =
    if (language == AppLanguage.PT) "Ações do Usage Monitor" else "Usage Monitor actions"

/** Uma passada de hover: o `Exit` de um quadro na divisa não fecha o painel. */
private const val HUD_COLLAPSE_DELAY_MILLIS = 150L

/** A saída do balão (fade de 90ms) com folga; a janela encolhe depois dela. */
private const val HUD_COLLAPSE_SETTLE_MILLIS = 200L
