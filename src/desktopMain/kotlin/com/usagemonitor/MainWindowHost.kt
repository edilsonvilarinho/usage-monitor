package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.usecase.GenerateBugReportUseCase
import com.usagemonitor.presentation.ui.BugReportHost
import com.usagemonitor.presentation.ui.DashboardScreen
import com.usagemonitor.presentation.ui.DesktopWindowFrame
import com.usagemonitor.presentation.ui.crashPrefillDescription
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.update.UpdateAckChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.entity.ApiSource
import kotlinx.coroutines.isActive

/** O que o card de cada conta mostra além das cotas: pulsos de sessão, cor e emoji. */
internal class CardDecorations(
    val cliSessionPulses: Map<UsageTargetKey, SessionPulse>,
    val teamSessionPulses: Map<UsageTargetKey, SessionPulse>,
    val accountColors: Map<String, AccountAccent>,
    val accountEmojis: Map<String, AccountEmoji>
)

/**
 * A janela principal: o Dashboard dentro da moldura do app, os atalhos de
 * teclado e o relatório de bug.
 *
 * Em modo HUD ela fica **escondida**, não encolhida: a barra mora em
 * `HudWindowHost`, e esta janela guarda a própria geometria intacta para a volta.
 * Escondida não é minimizada, então a coleta segue alimentando a HUD.
 */
@Composable
internal fun MainWindowHost(
    graph: AppGraph,
    viewModels: AppViewModels,
    shell: AppShellState,
    modal: AppModalState,
    startup: AppStartup,
    windows: AppWindowStates,
    workArea: ScreenWorkArea,
    iconImage: Painter?,
    actions: AppShellActions,
    enabledProfiles: List<AnthropicProfileRef>,
    teamSettings: TeamIntegrationSettings,
    decorations: CardDecorations,
    pendingCrash: PendingCrashReport?,
    onQuit: () -> Unit
) {
    val breadcrumbs = graph.breadcrumbs
    val enabledApis by graph.enabledApis.collectAsState()
    Window(
        onCloseRequest = onQuit,
        title = "Usage Monitor",
        icon = iconImage,
        state = windows.main,
        undecorated = true,
        // Expressão recomposta a cada leitura, não gravação: a preferência só é
        // gravada pelo controle das Configurações.
        alwaysOnTop = shell.alwaysOnTopEnabled,
        visible = !shell.hudMode,
        onKeyEvent = { event -> handleMainWindowShortcut(event, shell, modal, breadcrumbs) }
    ) {
        LaunchedEffect(window) {
            graph.mainWindow = window
            // A mesma janela, também fora da composição: é dela que a captura em
            // caso de queda tira os limites do recorte. A atribuição vem **antes**
            // do registro de diagnóstico, que suspende numa ida à IO.
            appMainWindow = window
            // O efetivo é lido **de volta da AWT**, não da preferência: a diferença
            // entre os dois separa "o app não pediu" de "o pedido foi engolido"
            // (issue #120). Só a escrita no arquivo vai para a IO.
            val requested = shell.alwaysOnTopEnabled
            val effective = runCatching { window.isAlwaysOnTop }.getOrNull()
            withContext(Dispatchers.IO) {
                startup.record(
                    StartupOutcome.WINDOW_SHOWN,
                    startup.machineContext.copy(alwaysOnTopRequested = requested, alwaysOnTopEffective = effective)
                )
            }
        }
        ApplyWindowMinimumSize(
            window = window,
            widthDp = MAIN_MIN_WINDOW_WIDTH_DP,
            heightDp = DEFAULT_MODAL_MIN_HEIGHT.value.toInt(),
            uiScalePercent = shell.uiScalePercent,
            workArea = workArea
        )
        // O ACK sai daqui: ele afirma que esta versão subiu inteira, e o único
        // ponto em que isso é verdade é depois de a instância única deixar passar,
        // de os recursos críticos serem construídos e de a janela compor.
        // Confirmar antes faria o script guardar como boa uma versão que ainda pode
        // não abrir — e o rollback existe justamente para esse caso.
        val ackToken = startup.updateAckToken
        if (ackToken != null) {
            LaunchedEffect(ackToken) {
                withContext(Dispatchers.IO) { UpdateAckChannel().acknowledge(ackToken) }
            }
        }
        LaunchedEffect(shell.windowOpacityPercent) {
            applyWindowOpacity(window, shell.windowOpacityPercent)
        }
        AppTheme(preset = shell.themePreset, uiScalePercent = shell.uiScalePercent, motion = shell.motion) {
            // O menu de molduras (issues #187 e #215) só é composto na faixa
            // revelada do modo somente cards; em Padrão o rodapé já o tem.
            DesktopWindowFrame(
                title = "Usage Monitor",
                iconPainter = iconImage,
                windowState = windows.main,
                onCloseRequest = onQuit,
                compact = shell.cardsOnlyMode,
                onExitCompact = { shell.changeCardsOnlyMode(false) },
                language = shell.language,
                windowMode = shell.windowMode,
                onWindowModeChange = if (shell.cardsOnlyMode) actions.changeWindowMode else null
            ) {
                MainDashboard(viewModels, shell, actions, enabledApis, enabledProfiles, teamSettings, decorations)
            }
            // Dentro da janela principal, e não da de Configurações: é ela que a
            // captura enquadra, e é ela que existe no arranque depois de uma queda.
            if (modal.isBugReportOpen) {
                MainBugReport(graph, shell, modal, pendingCrash)
            }
        }
    }
}

@Composable
private fun MainDashboard(
    viewModels: AppViewModels,
    shell: AppShellState,
    actions: AppShellActions,
    enabledApis: Set<com.usagemonitor.domain.entity.ApiSource>,
    enabledProfiles: List<AnthropicProfileRef>,
    teamSettings: TeamIntegrationSettings,
    decorations: CardDecorations
) {
    DashboardScreen(
        viewModel = viewModels.dashboard,
        appVersion = CURRENT_APP_VERSION,
        language = shell.language,
        cardOrder = shell.cardOrder,
        minimizedCards = shell.minimizedCards,
        onMoveCardToIndex = { target, targetIndex ->
            val visibleTargets = enabledUsageTargets(enabledSources = enabledApis, enabledProfiles = enabledProfiles)
            shell.moveCard(target, targetIndex, visibleTargets)
        },
        onToggleCardMinimized = shell::toggleCardMinimized,
        onOpenHistory = actions.openHistory,
        onOpenSettings = actions.openSettings,
        onOpenHelp = actions.openHelp,
        onOpenCodexCliSessions = actions.openCodexCliSessions,
        // Só quem administra recebe os dois botões: `null` esconde.
        onOpenAdminOverview = actions.openAdminOverview,
        onOpenTeamPresenceOverview = actions.openTeamPresenceOverview,
        onOpenCliSessions = actions.openCliSessions,
        onOpenTeamUsage = actions.openTeamUsage,
        onOpenTeamPresence = actions.openTeamPresence,
        // Vazio com a integração desligada: o botão some de todos os cards sem
        // nenhuma outra condição espalhada na tela.
        teamEnabledProfileIds = if (teamSettings.isActive) teamSettings.participatingProfileIds else emptySet(),
        cliSessionPulses = decorations.cliSessionPulses,
        teamSessionPulses = decorations.teamSessionPulses,
        accountColors = decorations.accountColors,
        accountEmojis = decorations.accountEmojis,
        showFooter = !shell.cardsOnlyMode,
        windowMode = shell.windowMode,
        onWindowModeChange = actions.changeWindowMode,
        onExportSnapshot = actions.exportSnapshot,
        onExportFailure = actions.onExportFailure
    )
}

@Composable
private fun MainBugReport(graph: AppGraph, shell: AppShellState, modal: AppModalState, pendingCrash: PendingCrashReport?) {
    val breadcrumbs = graph.breadcrumbs
    val generateBugReport = remember(breadcrumbs) {
        GenerateBugReportUseCase(
            breadcrumbs = breadcrumbs,
            // Função, e não valor: idioma e escala mudam enquanto o app roda, e
            // congelá-los faria o relatório descrever outro momento.
            machineInfo = { desktopBugReportMachineInfo(shell.language, shell.uiScalePercent) },
            // O corte do arquivo tem um dono só, e é o registro de arranque.
            breadcrumbLimit = StartupDiagnostics.MAX_LINES
        )
    }
    val writer = remember { DesktopBugReportWriter(parentWindow = { graph.mainWindow }) }
    val issueOpener = remember { BugReportIssueOpener() }
    val capturer = remember { RobotWindowScreenshotCapturer { graph.mainWindow } }
    BugReportHost(
        generateBugReport = generateBugReport,
        writer = writer,
        issueOpener = issueOpener,
        screenshots = capturer,
        breadcrumbs = breadcrumbs,
        language = shell.language,
        crashPrefill = pendingCrash?.let { crash ->
            crashPrefillDescription(
                exception = crash.marker.exception,
                thread = crash.marker.thread,
                isPt = shell.language == AppLanguage.PT
            )
        },
        crashScreenshotPng = pendingCrash?.screenshotPng,
        onDismiss = { modal.isBugReportOpen = false }
    )
}

/**
 * Os atalhos da janela principal. `Ctrl+Shift+M` e `Ctrl+Shift+H` são saídas das
 * molduras reduzidas que funcionam com a janela coberta por outra; `F1` é a tecla
 * que o sistema reserva para a ajuda e não colide com as duas.
 */
private fun handleMainWindowShortcut(
    event: KeyEvent,
    shell: AppShellState,
    modal: AppModalState,
    breadcrumbs: com.usagemonitor.domain.repository.BreadcrumbRecorder
): Boolean {
    val isDown = event.type == KeyEventType.KeyDown
    val isCardsOnlyToggle = isDown && event.isCtrlPressed && event.isShiftPressed && event.key == Key.M
    val isHudToggle = isDown && event.isCtrlPressed && event.isShiftPressed && event.key == Key.H
    val isHelpShortcut = isDown && event.key == Key.F1
    when {
        isCardsOnlyToggle -> shell.changeCardsOnlyMode(!shell.cardsOnlyMode)
        isHudToggle -> shell.changeHudMode(!shell.hudMode)
        isHelpShortcut -> {
            breadcrumbs.recordScreenOpened("Ajuda (F1)")
            modal.isHelpOpen = true
        }
    }
    return isCardsOnlyToggle || isHudToggle || isHelpShortcut
}

/** Piso horizontal do Dashboard; abaixo disso os cards já operam em coluna única. */
internal const val MAIN_MIN_WINDOW_WIDTH_DP = 240
