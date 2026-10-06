package com.usagemonitor

import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.Window
import androidx.compose.material3.Text
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.presentation.ui.AppDialogWindow
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.ModalWindowEnvironment
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.screenshots.fixedHistoryViewModel
import com.usagemonitor.screenshots.issue383HistoryReports
import com.usagemonitor.screenshots.issue383LongHistoryAccounts
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Sonda nativa isolada: dados sintéticos, sem abrir grafo, credenciais ou preferências do app. */
object HistoryOpenProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val output = File(args.firstOrNull() ?: "build/issue389-probe")
        output.mkdirs()
        val crashes = CopyOnWriteArrayList<Throwable>()
        val prewarm = args.getOrNull(1) == "true"
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            crashes += error
            File(output, "crash-${crashes.size}.txt").writeText("${thread.name}\n${error.stackTraceToString()}")
            error.printStackTrace()
        }
        application {
            var visible by remember { mutableStateOf(false) }
            var generation by remember { mutableStateOf(0) }
            val vm = remember {
                val accounts = issue383LongHistoryAccounts(ApiSource.CODEX)
                val base = issue383HistoryReports().first()
                fixedHistoryViewModel(base.copy(source = ApiSource.CODEX, accountContext = accounts.first(),
                    series = base.series.map { it.copy(quotaLabel = it.quotaLabel.replace("Claude", "Codex")) }), accounts)
            }
            val state = rememberWindowState(size = DpSize(900.dp, 640.dp))
            DisposableEffect(vm) { onDispose { vm.onDestroy() } }
            Window(onCloseRequest = { exitApplication() }, title = "QA #389 — acionador",
                state = rememberWindowState(size = DpSize(360.dp, 100.dp)),
                undecorated = true, transparent = true, alwaysOnTop = true) {
            Text("Sonda de abertura $generation / 5")
            LaunchedEffect(Unit) {
                delay(if (prewarm) 1200 else 200)
                repeat(5) {
                    withFrameNanos { generation += 1; visible = true }
                    delay(900)
                    visible = false
                    delay(400)
                }
                File(output, "result.txt").writeText("openings=5\ncrashes=${crashes.size}\n")
                println("Native history probe: openings=5 crashes=${crashes.size}")
                exitApplication()
            }
            }
            AppDialogWindow(
                visible = visible, title = "QA #389 — dados sintéticos", state = state,
                environment = ModalWindowEnvironment(null, AppThemePreset.OBSIDIANA_DARK, 115,
                    AppMotionPolicy.Live, ScreenWorkArea.Unknown, NoOpBreadcrumbRecorder, prewarmReady = prewarm),
                diagnosticName = "sonda histórico", minWidthDp = 320, minHeightDp = 320,
                onCloseRequest = { visible = false }, openGeneration = generation, prewarm = prewarm
            ) {
                HistoryScreen(vm, AppLanguage.PT, {}, focusedSource = ApiSource.CODEX, showSourceSelector = false)
            }
        }
        check(crashes.isEmpty()) { "Falha nativa reproduzida: ${crashes.size} exceções; consulte ${output.absolutePath}" }
    }
}
