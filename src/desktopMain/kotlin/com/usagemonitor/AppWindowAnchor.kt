package com.usagemonitor

import com.usagemonitor.presentation.ui.CommonLabels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.usecase.GenerateBugReportUseCase
import com.usagemonitor.presentation.ui.AppDialogWindow
import com.usagemonitor.presentation.ui.BugReportHost
import com.usagemonitor.presentation.ui.ModalWindowEnvironment
import com.usagemonitor.presentation.ui.crashPrefillDescription
import com.usagemonitor.update.UpdateAckChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * O que a janela principal fazia ao compor, agora feito pela janela da barra HUD,
 * a única de visualização do app.
 *
 * A janela vira a âncora do app: pai do diálogo de arquivo, alvo da captura do
 * relatório de bug e da ativação pela bandeja. A atribuição vem **antes** do
 * registro de diagnóstico, que suspende numa ida à IO — a captura em caso de
 * queda lê [appMainWindow] fora da composição.
 *
 * O ACK sai daqui: ele afirma que esta versão subiu inteira, e o único ponto em
 * que isso é verdade é depois de a instância única deixar passar, de os recursos
 * críticos serem construídos e de a janela compor. Confirmar antes faria o script
 * guardar como boa uma versão que ainda pode não abrir — e o rollback existe
 * justamente para esse caso.
 */
internal suspend fun anchorAppWindow(window: java.awt.Window, graph: AppGraph, startup: AppStartup) {
    graph.mainWindow = window
    appMainWindow = window
    recordAppAnchored(
        anchored = graph.appAnchored,
        // O efetivo é lido de volta da AWT (issue #120): a HUD sempre pede o topo, e
        // a diferença entre pedido e efetivo é o que o diagnóstico existe para ver.
        alwaysOnTopEffective = { runCatching { window.isAlwaysOnTop }.getOrNull() },
        recordShown = { effective ->
            startup.record(
                StartupOutcome.WINDOW_SHOWN,
                startup.machineContext.copy(alwaysOnTopRequested = true, alwaysOnTopEffective = effective)
            )
        },
        ackToken = startup.updateAckToken,
        acknowledge = { token -> UpdateAckChannel().acknowledge(token) }
    )
}

/** O registro de arranque e o ACK da primeira âncora, sem a janela e o grafo, para o teste. */
internal suspend fun recordAppAnchored(
    anchored: MutableStateFlow<Boolean>,
    alwaysOnTopEffective: () -> Boolean?,
    recordShown: (alwaysOnTopEffective: Boolean?) -> Unit,
    ackToken: String?,
    acknowledge: (String) -> Unit
) {
    // A HUD renasce quando muda de monitor (#426): a âncora passa a ser a janela
    // nova, mas o arranque e o ACK da atualização já foram registrados e não se
    // repetem — seriam uma segunda linha de `WINDOW_SHOWN` por arrasto.
    if (anchored.value) {
        return
    }
    val effective = alwaysOnTopEffective()
    withContext(Dispatchers.IO) { recordShown(effective) }
    if (ackToken != null) {
        withContext(Dispatchers.IO) { acknowledge(ackToken) }
    }
    anchored.value = true
}

/**
 * O relatório de bug em janela própria. Ele morava dentro da janela principal;
 * a HUD é do tamanho do notch e não comporta o formulário. É também a janela que
 * abre no arranque depois de uma queda.
 */
@Composable
internal fun BugReportWindow(
    graph: AppGraph,
    shell: AppShellState,
    modal: AppModalState,
    pendingCrash: PendingCrashReport?,
    windows: AppWindowStates,
    environment: ModalWindowEnvironment
) {
    val language = shell.language
    val close = { modal.isBugReportOpen = false }
    AppDialogWindow(
        visible = modal.isBugReportOpen,
        title = CommonLabels.reportBug(language),
        state = windows.bugReport,
        environment = environment,
        diagnosticName = "relatório de bug",
        minWidthDp = 480,
        minHeightDp = DEFAULT_MODAL_MIN_HEIGHT.value.toInt(),
        onCloseRequest = close
    ) {
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
        // A captura é da HUD, a janela que o usuário estava vendo.
        val capturer = remember { RobotWindowScreenshotCapturer { graph.mainWindow } }
        BugReportHost(
            generateBugReport = generateBugReport,
            writer = writer,
            issueOpener = issueOpener,
            screenshots = capturer,
            breadcrumbs = breadcrumbs,
            language = language,
            crashPrefill = pendingCrash?.let { crash ->
                crashPrefillDescription(
                    exception = crash.marker.exception,
                    thread = crash.marker.thread,
                    isPt = language == AppLanguage.PT
                )
            },
            crashScreenshotPng = pendingCrash?.screenshotPng,
            onDismiss = close
        )
    }
}
