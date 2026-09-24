package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudBar
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.nextRefreshLabel
import com.usagemonitor.presentation.ui.components.toneFor
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.ui.toSourceStatus
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import java.awt.MouseInfo
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * A barra HUD numa janela **própria**.
 *
 * Ela era a janela principal encolhida. Isso obrigava `main()` a guardar a
 * geometria de antes para restaurar ao sair (`preHudWindowGeometry`), a proibir
 * o coletor de persistência de gravar a pílula como "tamanho normal", a trocar o
 * piso de tamanho **antes** do efeito que redimensiona — por ordem textual — e a
 * redimensionar a janela AWT a cada quadro para animar o painel, que era a fonte
 * do tranco. Com uma janela só para a HUD, a principal fica escondida com a
 * própria geometria intacta, o dashboard continua composto (voltar é
 * instantâneo) e nada disso existe mais.
 *
 * Mora fora de `main()` porque aquele composable está no limite do backend JVM
 * (CLAUDE.md, "Injeção de dependências"): estado novo ali é o que estoura o ASM.
 * `main()` só a chama com `if (hudMode)`.
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
    onOpenHelp: () -> Unit,
    onCloseRequest: () -> Unit
) {
    val snapshot by usageAlertViewModel.worstSnapshot.collectAsState()
    val quotaRisks by usageAlertViewModel.quotaRisks.collectAsState()
    val appUpdateState by viewModel.appUpdateState.collectAsState()
    val nextRefreshAt by viewModel.nextRefreshAt.collectAsState()

    val statusTone = snapshot?.let { worst -> toneFor(worst.risk.level) } ?: AppTone.NEUTRAL
    val fallbackLabel = if (language == AppLanguage.PT) "Carregando" else "Loading"
    // A faixa de atualização do modo padrão não existe aqui; o indicador ocupa a
    // primeira linha com o mesmo texto e tom de `updateBannerContent` (#225).
    val updateIndicator = appUpdateState?.let { state ->
        val content = updateBannerContent(state = state, language = language)
        HudUpdateIndicator(tone = content.tone, description = content.title)
    }
    val accounts = buildHudAccounts(
        quotaRisks = quotaRisks,
        cardOrder = cardOrder,
        language = language,
        now = Clock.System.now()
    )
    val sources = accounts.map { account -> account.toSourceStatus() }

    var hovered by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    // Última posição do ponteiro na tela durante o arrasto. Incremental: guardar
    // a de partida e somar o total deixaria a barra presa na borda, porque o
    // excedente de um arrasto para fora da tela nunca seria descartado.
    var dragPointer by remember { mutableStateOf<java.awt.Point?>(null) }

    // Expandir é imediato; recolher espera uma passada de `AppMotion.fast` — sem
    // ela o ponteiro cruzando a divisa entre duas linhas geraria um `Exit` de um
    // quadro e o painel fecharia debaixo dele.
    LaunchedEffect(hovered, dragging) {
        if (hovered && !dragging) {
            expanded = true
        } else if (!dragging) {
            delay(AppMotion.fast.toLong())
            if (!dragging && !hovered) {
                expanded = false
            }
        }
    }

    val scale = uiScaleFactor(uiScalePercent)
    // A âncora descreve sempre o painel **parado**: é ela que o arrasto move e
    // que fica gravada, e expandir não a desloca.
    val anchorSize = hudWindowSize(
        sources = sources,
        fallbackLabel = fallbackLabel,
        expanded = false,
        showsCountdown = true,
        hasUpdateIndicator = updateIndicator != null
    ).let { size -> DpSize(size.width * scale, size.height * scale) }
    val targetSize = hudWindowSize(
        sources = sources,
        fallbackLabel = fallbackLabel,
        expanded = expanded && !dragging,
        showsCountdown = true,
        hasUpdateIndicator = updateIndicator != null
    ).let { size -> DpSize(size.width * scale, size.height * scale) }

    var anchor by remember {
        val stored = readPersistedHudPosition(settings)
        val entry = fitWindowPosition(
            x = stored?.xDp?.dp ?: (hudScreenArea.x + hudScreenArea.size.width - anchorSize.width),
            y = stored?.yDp?.dp ?: hudScreenArea.y,
            size = anchorSize,
            workArea = hudScreenArea
        )
        mutableStateOf(DpOffset(entry.x, entry.y))
    }
    val initialPosition = hudWindowPosition(anchor.x, anchor.y, anchorSize, targetSize, hudScreenArea)
    val windowState = rememberWindowState(
        placement = WindowPlacement.Floating,
        size = targetSize,
        position = initialPosition
    )

    // O tamanho muda **de uma vez**, sem interpolar a janela AWT quadro a quadro:
    // aquela interpolação era a fonte do tranco da HUD anterior. O movimento do
    // painel é conteúdo, e a janela só acompanha.
    LaunchedEffect(anchor, targetSize, anchorSize) {
        windowState.size = targetSize
        windowState.position = hudWindowPosition(anchor.x, anchor.y, anchorSize, targetSize, hudScreenArea)
    }

    val dragBegin = {
        dragging = true
        expanded = false
        dragPointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
    }
    val dragTo = {
        val previous = dragPointer
        val current = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()
        if (previous != null && current != null) {
            dragPointer = current
            // Mesma área do encaixe: arrastar limitado à área útil e encaixar na
            // tela inteira impedia a barra de chegar sobre a barra de tarefas
            // durante o arrasto, e só o encaixe final a deixava lá.
            val moved = fitWindowPosition(
                x = anchor.x + (current.x - previous.x).dp,
                y = anchor.y + (current.y - previous.y).dp,
                size = anchorSize,
                workArea = hudScreenArea
            )
            anchor = DpOffset(moved.x, moved.y)
        }
    }
    val dragFinish = {
        dragPointer = null
        dragging = false
        val snapped = snapHudPosition(
            x = anchor.x,
            y = anchor.y,
            size = anchorSize,
            workArea = hudScreenArea
        )
        anchor = DpOffset(snapped.x, snapped.y)
        persistHudPosition(settings, xDp = snapped.x.value, yDp = snapped.y.value)
        if (hovered) {
            expanded = true
        }
        Unit
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Usage Monitor",
        icon = iconImage,
        state = windowState,
        undecorated = true,
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
                help -> onOpenHelp()
            }
            hudToggle || cardsOnlyToggle || help
        }
    ) {
        LaunchedEffect(windowOpacityPercent) {
            applyWindowOpacity(window, windowOpacityPercent)
        }
        AppTheme(preset = themePreset, uiScalePercent = uiScalePercent, motion = motion) {
            HudBar(
                statusTone = statusTone,
                sources = sources,
                fallbackLabel = fallbackLabel,
                expanded = expanded && !dragging,
                dragging = dragging,
                updateIndicator = updateIndicator,
                nextRefreshAt = nextRefreshAt,
                countdownDescription = nextRefreshLabel(language),
                onHoverChange = { isHovered -> hovered = isHovered },
                onDragStart = dragBegin,
                onDragMove = dragTo,
                onDragEnd = dragFinish,
                onOpenFull = onOpenFull,
                // Botão direito (issue #215): direto para "Somente cards".
                onSwitchToCardsOnly = onSwitchToCardsOnly
            )
        }
    }
}
