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
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.Dp
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
import java.awt.GraphicsDevice
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
 * dashboard saiu quando a HUD virou o único modo. Por isso ela é também a
 * âncora do app — diálogo de arquivo, captura do relatório de bug, ativação
 * pela bandeja e pela segunda instância —, entregue por [onWindowReady].
 *
 * **Janela transparente, e ela engole clique na área vazia** (medido no Windows
 * 11, C11 do plano de execução). Por isso, parada, ela só aceita clique no
 * notch: a janela tem sempre o tamanho da aberta (`hudDockedWindowBounds`) e
 * a área de clique é recortada por `Window.shape` (`hudRestHitRegion`).
 *
 * A janela **não muda de origem nem de tamanho** entre parada e aberta. Mudar
 * a origem de uma janela transparente mostra um quadro do conteúdo antigo no
 * lugar novo — era o pisca ao passar o ponteiro (E11) e, na borda direita e
 * na de baixo, o notch saltando 274px a cada abrir e fechar (issue #294).
 *
 * **O recorte é só do Windows** ([hudUsesHitRegion]). No elementary OS 6.1
 * (X11) o `shape = null` da abertura não tirava o recorte: o balão pintava
 * só dentro da margem de 16dp e as alças saíam cortadas. Fora do Windows a
 * janela fica sem recorte — o balão abre inteiro, e a área vazia pode
 * engolir clique.
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
    // A mesma ação da faixa, oferecida no balão da engrenagem — nunca no
    // ícone do notch, onde seria clique de rotina reiniciando o app.
    val updateAction = appUpdateState?.let { state ->
        updateBannerAction(
            state = state,
            onOpenRelease = { viewModel.openUpdateReleasePage() },
            onRestartAndUpdate = { viewModel.restartAndUpdateNow() }
        )
    }
    // Conta que nasce ou colapsa (API ativada/desativada, início do app)
    // fica na lista durante a transição; a geometria segue essa lista.
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

    // O monitor do notch (issue #273): é o monitor gravado, resolvido de
    // novo a cada abertura por hover. Um monitor reconectado volta a
    // receber o notch sem reiniciar o app.
    var screenArea by remember { mutableStateOf(resolveHudScreenArea(settings, hudScreenArea)) }
    var placement by remember { mutableStateOf(readPersistedHudPlacement(settings, screenArea)) }
    var hovered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    // A área de clique da janela inteira, sem o recorte do notch parado. Sai
    // **antes** do conteúdo ao abrir e volta **depois** dele ao fechar.
    var windowOpen by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val retract = remember { HudAutoRetractState(settings) }
    // Durante o arrasto o notch sai da borda e anda livre; esta é a
    // posição da janela (dp de janela). Nulo fora de arrasto.
    var dragWindowPosition by remember { mutableStateOf<DpOffset?>(null) }
    var dragPointer by remember { mutableStateOf<java.awt.Point?>(null) }

    LaunchedEffect(hovered, dragging) {
        if (dragging) return@LaunchedEffect
        if (hovered) {
            delay(hudOpenDelayMillis(retract.enabled, alreadyOpen = windowOpen))
            screenArea = resolveHudScreenArea(settings, hudScreenArea)
            windowOpen = true
            // Um quadro para o recorte sair antes do conteúdo abrir.
            withFrameNanos { }
            expanded = true
        } else {
            // `Exit` de um quadro quando o ponteiro cruza a divisa notch/painel.
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
    val bounds = hudHostWindowBounds(dragging, placement, sizes, composedArea)
    val hitRegionSupported = remember { hudUsesHitRegion(AutoStartManager.currentPlatform()) }
    val hitRegion = if (dragging || windowOpen) null else hudRestHitRegion(placement.edge, bounds, sizes, retracted = retract.enabled)
    val windowSize = DpSize(bounds.size.width * scale, bounds.size.height * scale)
    val docked = WindowPosition(bounds.x * scale, bounds.y * scale)

    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = windowSize,
        position = docked
    )
    // Sincroniza o `windowState` (tamanho + posição dock) **fora do arrasto**.
    // Dentro do arrasto o efeito dentro do `Window` move a AWT direto
    // (#416: passar por `windowState.position` a cada tick do mouse dispara
    // recompose + redesenho Skia + `swapBuffers` síncrono, e o `EdtWatchdog`
    // capturou 5s parado em `LinuxOpenGLRedrawer.swapBuffers` no Linux).
    LaunchedEffect(windowSize, docked) {
        windowState.size = windowSize
        if (!dragging) {
            windowState.position = docked
        }
    }

    // A janela de arrasto, que é o que se move (issue #288): esta medida sai
    // da geometria e não muda no arrasto.
    val dragStart = hudDragWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    val dragSize = DpSize(dragStart.size.width * scale, dragStart.size.height * scale)

    val dragBegin = {
        dragging = true
        expanded = false
        windowOpen = false
        // O arrasto parte da janela de arrasto já encaixada, com o notch no
        // ponto da tela em que ele está — nunca da origem da janela aberta.
        dragWindowPosition = DpOffset(dragStart.x * scale, dragStart.y * scale)
        dragPointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        HudDragProbe.begin()
    }
    val dragTo = {
        val previous = dragPointer
        val current = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        val position = dragWindowPosition
        if (previous != null && current != null && position != null) {
            dragPointer = current
            // Incremental: somar o total desde o início prenderia o notch
            // na borda. Área = monitor **sob o ponteiro**, bounds inteiros.
            val moved = fitWindowPosition(
                x = position.x + (current.x - previous.x).dp,
                y = position.y + (current.y - previous.y).dp,
                size = dragSize,
                workArea = pointerScreen()?.bounds ?: screenArea
            )
            // O efeito dentro do `Window` move a AWT direto, sem passar
            // por `windowState.position` (recompose + `swapBuffers` trava
            // o EDT no OpenGL do Linux, #416).
            dragWindowPosition = DpOffset(moved.x, moved.y)
        }
    }
    val dragFinish = {
        val position = dragWindowPosition
        HudDragProbe.finish(position)
        if (position != null) {
            // Borda mais próxima do **centro** do notch, no monitor em que
            // foi solto; a barra de tarefas é *topmost* (issue #288).
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
        // O que o sistema fez com o pedido de posição e de alfa (#416).
        LaunchedEffect(window) {
            recordHudWindowFacts(window, { windowState.position }, { windowState.size })
        }
        // O `dragTo` só atualiza `dragWindowPosition`; a posição real da
        // janela AWT é movida no `dragFinish`. Mover durante o arrasto
        // dispararia recompose + `swapBuffers` síncrono a cada tick do
        // mouse, e o `EdtWatchdog` capturou 5s parado em
        // `LinuxOpenGLRedrawer.swapBuffers` no OpenGL do Linux (#416).
        LaunchedEffect(dragWindowPosition) {
            // Não move: ver KDoc acima. Só reage para o caso do arrasto
            // ser cancelado por outro caminho (sem `dragFinish`).
            if (dragWindowPosition != null) return@LaunchedEffect
        }
        // Reaplica o fundo transparente quando a janela cruza um `GraphicsDevice` (#416).
        ApplyHudDeviceChangeReapply(window)
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
                    edge = edge, sizes = sizes, settledSizes = settledSizes,
                    fallbackLabel = fallbackLabel, fallbackTone = fallbackTone,
                    expanded = expanded && !dragging, dragging = dragging,
                    updateIndicator = updateIndicator, notchCenter = centerInWindow,
                    language = language,
                    onHoverChange = { hovered = it },
                    onDragStart = dragBegin, onDragMove = dragTo, onDragEnd = dragFinish,
                    onRefreshAccount = { target -> viewModel.refresh(target) },
                    accountActions = { account ->
                        HudAccountActions(
                            account = account, language = language,
                            teamEnabledProfileIds = teamEnabledProfileIds,
                            cliSessionPulse = cliSessionPulses[account.targetKey] ?: SessionPulse.EMPTY,
                            teamSessionPulse = teamSessionPulses[account.targetKey] ?: SessionPulse.EMPTY,
                            actions = actions,
                            onRefresh = { viewModel.refresh(account.targetKey) }
                        )
                    },
                    appBalloon = {
                        HudWindowAppBalloon(
                            language = language,
                            nextRefreshAt = nextRefreshAt, refreshInterval = pollInterval,
                            updateIndicator = updateIndicator, updateAction = updateAction,
                            actions = actions, exportScope = exportScope,
                            currentStats = { (dashboardState as? UiState.Success)?.data },
                            onQuit = onCloseRequest
                        )
                    },
                    appBalloonHeight = hudAppBalloonHeight(
                        hasUpdateIndicator = updateIndicator != null,
                        hasUpdateAction = updateAction != null
                    ),
                    gearDescription = hudGearDescription(language),
                    autoRetract = retract.enabled, onToggleAutoRetract = retract::toggle,
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
 * antes de abrir o balão, já sai sem o recorte. Fora do Windows ([supported]
 * falso) não aplica nada — só grava que pulou (#342).
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
 * Detecta quando a janela da HUD atravessa um `GraphicsDevice` e tenta
 * recuperar a transparência perdida (issue #416, monitor secundário).
 *
 * **Três passos, na ordem:** (1) `setBackground(Color(0,0,0,0)) + repaint` —
 * acorda stacks que renegociam o peer AWT sem refazer o contexto. (2) toggle
 * de `isVisible` + nudge de `opacity` — re-mapeia o peer quando o passo 1
 * não basta. (3) nudge de `size +1/-1` — força o AWT a descartar o back
 * buffer e realocar um novo. Custa um pisca, mas é o que tira a janela do
 * estado opaco no secundário quando o compositor KDE/COSMIC deixa o buffer
 * sem canal alfa. O log diz qual dos passos funcionou e o que o peer está
 * pintando depois (`fundo`, `opaco`, `buffer-alfa`).
 *
 * **Sondagem em vez de `ComponentListener`/`componentMoved`:** o AWT não
 * dispara o evento de mover dentro do mesmo `GraphicsDevice`; o que a gente
 * quer é a interseção entre mover e trocar de device. 500ms cobre um arrasto
 * entre monitores sem custar a EDT ociosa.
 */
@Composable
private fun ApplyHudDeviceChangeReapply(window: java.awt.Window) {
    LaunchedEffect(window) {
        var previousDeviceId: String? = null
        while (true) {
            val currentDeviceId = hudDeviceIdOf(window)
            if (currentDeviceId != null && previousDeviceId != null && currentDeviceId != previousDeviceId) {
                val reapply = runCatching {
                    window.background = java.awt.Color(0, 0, 0, 0)
                    window.invalidate()
                    window.repaint()
                }
                val remap = runCatching {
                    if (window.isVisible) {
                        window.isVisible = false
                        window.isVisible = true
                        val originalOpacity = window.opacity
                        window.opacity = 0.99f
                        window.opacity = originalOpacity
                        window.invalidate()
                        window.repaint()
                    }
                }
                val bufferRecreate = runCatching {
                    val size = window.size
                    window.size = java.awt.Dimension(size.width + 1, size.height + 1)
                    window.size = size
                    window.invalidate()
                    window.repaint()
                }
                recordHudDeviceChange(window, hudDeviceChangeFactsOf(
                    window, previousDeviceId, currentDeviceId, reapply, remap, bufferRecreate
                ))
            }
            previousDeviceId = currentDeviceId
            delay(HUD_DEVICE_POLL_INTERVAL_MILLIS)
        }
    }
}

/** Lê o estado do peer no **novo** `GraphicsDevice` e empacota a fact de troca. */
private fun hudDeviceChangeFactsOf(
    window: java.awt.Window,
    previousDeviceId: String,
    currentDeviceId: String,
    reapply: Result<Unit>,
    remap: Result<Unit>,
    bufferRecreate: Result<Unit>
): HudDeviceChangeFacts {
    val device = runCatching { window.graphicsConfiguration?.device }.getOrNull()
    fun supports(kind: GraphicsDevice.WindowTranslucency): Boolean? =
        device?.let { runCatching { it.isWindowTranslucencySupported(kind) }.getOrNull() }
    return HudDeviceChangeFacts(
        prevId = previousDeviceId,
        newId = currentDeviceId,
        x = window.x,
        y = window.y,
        width = window.width,
        height = window.height,
        perPixelTranslucent = supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT),
        uniformTranslucent = supports(GraphicsDevice.WindowTranslucency.TRANSLUCENT),
        perPixelTransparent = supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT),
        renderApi = runCatching { (window as? ComposeWindow)?.renderApi?.name }.getOrNull(),
        reapplied = reapply.isSuccess,
        reapplyError = reapply.exceptionOrNull()
            ?.let { error -> "${error::class.simpleName}: ${error.message}" },
        visibilityRemapped = remap.isSuccess,
        remapError = remap.exceptionOrNull()
            ?.takeIf { remap.isFailure }
            ?.let { error -> "${error::class.simpleName}: ${error.message}" },
        backgroundAlpha = runCatching { window.background?.alpha }.getOrNull(),
        isOpaque = runCatching { window.isOpaque }.getOrNull(),
        isDisplayable = runCatching { window.isDisplayable }.getOrNull(),
        isShowing = runCatching { window.isShowing }.getOrNull(),
        bufferRecreated = bufferRecreate.isSuccess,
        bufferRecreateError = bufferRecreate.exceptionOrNull()
            ?.let { error -> "${error::class.simpleName}: ${error.message}" }
    )
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
