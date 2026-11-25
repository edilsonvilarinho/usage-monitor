package com.usagemonitor

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.presentation.ui.AppDialogWindow
import com.usagemonitor.presentation.ui.HistoryScreen
import com.usagemonitor.presentation.ui.ModalWindowEnvironment
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.screenshots.fixedHistoryViewModel
import com.usagemonitor.screenshots.issue383HistoryReports
import com.usagemonitor.screenshots.issue383LongHistoryAccounts
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Frame
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Sonda do flash do pré-aquecimento (#669). NÃO rodar nesta máquina: abre janelas na tela do dono.
 * Fundo magenta em tela cheia; cada ciclo monta um AppDialogWindow novo, pré-aquecido ou
 * (com control) mostrado por 300 ms. Um amostrador conta os quadros em que o centro saiu do magenta.
 */
object PrewarmFlashProbe {
    private const val TITLE_PREFIX = "QA #669 — ciclo "
    private const val LIT_THRESHOLD = 8
    private const val SAMPLE_SIDE = 24
    private const val CYCLE_TIMEOUT_MS = 5_000L
    private const val SETTLE_MS = 150L
    private const val CONTROL_VISIBLE_MS = 300L
    private const val STARTUP_MS = 1_200L

    private class WindowMark(val title: String, val shown: Boolean, val nanos: Long)

    private class CycleResult(
        val index: Int,
        val overload: PrewarmOverload,
        val shownNanos: Long?,
        val hiddenNanos: Long?,
        val prewarmMs: Long?
    ) {
        val completed: Boolean get() = shownNanos != null && hiddenNanos != null
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val cycles = args.getOrNull(0)?.toIntOrNull() ?: 200
        val output = File(args.getOrNull(1) ?: "build/issue669-probe")
        val control = args.getOrNull(2) == "true"
        output.mkdirs()
        val screen = Toolkit.getDefaultToolkit().screenSize
        val marks = ConcurrentLinkedQueue<WindowMark>()
        val samples = ConcurrentLinkedQueue<PrewarmSample>()
        val running = AtomicBoolean(true)
        val results = CopyOnWriteArrayList<CycleResult>()
        val lastPrewarm = AtomicReference<String?>(null)
        val breadcrumbs = prewarmRecorder(lastPrewarm)
        val background = openBackground(screen)
        listenWindowMarks(marks)
        startSampler(screen, samples, running)
        application {
            var cycle by remember { mutableStateOf(-1) }
            var visible by remember { mutableStateOf(false) }
            val vm = remember {
                val accounts = issue383LongHistoryAccounts(ApiSource.CODEX)
                val base = issue383HistoryReports().first()
                fixedHistoryViewModel(base.copy(source = ApiSource.CODEX, accountContext = accounts.first(),
                    series = base.series.map { it.copy(quotaLabel = it.quotaLabel.replace("Claude", "Codex")) }), accounts)
            }
            DisposableEffect(vm) { onDispose { vm.onDestroy() } }
            Window(onCloseRequest = { exitApplication() }, title = "QA #669 ancora", content = {},
                state = rememberWindowState(size = DpSize(120.dp, 60.dp)), undecorated = true)
            if (cycle >= 0) key(cycle) {
                val title = TITLE_PREFIX + cycle
                val environment = ModalWindowEnvironment(null, AppThemePreset.OBSIDIANA_DARK, 115,
                    AppMotionPolicy.Live, ScreenWorkArea.Unknown, breadcrumbs, prewarmReady = true)
                val content: @Composable () -> Unit = {
                    HistoryScreen(vm, AppLanguage.PT, {}, focusedSource = ApiSource.CODEX, showSourceSelector = false)
                }
                val position = WindowPosition.Aligned(Alignment.Center)
                if (overloadOf(cycle) == PrewarmOverload.WINDOW_STATE) {
                    AppDialogWindow(visible = visible, title = title,
                        state = rememberWindowState(size = DpSize(900.dp, 640.dp), position = position),
                        environment = environment, diagnosticName = "sonda flash $cycle",
                        minWidthDp = 320, minHeightDp = 320, onCloseRequest = { visible = false },
                        openGeneration = cycle, prewarm = !control, content = content)
                } else {
                    AppDialogWindow(visible = visible, title = title,
                        state = rememberDialogState(size = DpSize(900.dp, 640.dp), position = position),
                        environment = environment, diagnosticName = "sonda flash $cycle",
                        minWidthDp = 320, minHeightDp = 320, onCloseRequest = { visible = false },
                        openGeneration = cycle, prewarm = !control, content = content)
                }
            }
            LaunchedEffect(Unit) {
                delay(STARTUP_MS)
                for (index in 0 until cycles) {
                    val title = TITLE_PREFIX + index
                    lastPrewarm.set(null)
                    cycle = index
                    if (control) {
                        withFrameNanos { }
                        visible = true
                        delay(CONTROL_VISIBLE_MS)
                        visible = false
                    }
                    awaitCycle(title, marks)
                    val message = lastPrewarm.get()
                    val prewarmMs = if (message == null) null else PrewarmFlashSamples.prewarmMillisOf(message)
                    results.add(resultOf(index, title, marks, prewarmMs))
                    delay(SETTLE_MS)
                }
                running.set(false)
                background.dispose()
                writeReport(output, results.toList(), samples.toList())
                exitApplication()
            }
        }
    }

    private fun overloadOf(index: Int): PrewarmOverload =
        if (index % 2 == 0) PrewarmOverload.WINDOW_STATE else PrewarmOverload.DIALOG_STATE

    private fun prewarmRecorder(last: AtomicReference<String?>): BreadcrumbRecorder = object : BreadcrumbRecorder {
        override fun record(category: BreadcrumbCategory, message: String) {
            if (message.contains("pré-aquecida")) last.set(message)
        }

        override fun read(limit: Int): List<Breadcrumb> = emptyList()
    }

    /** Fundo magenta sem moldura, não focável, em tela cheia. */
    private fun openBackground(screen: Dimension): JFrame {
        val holder = AtomicReference<JFrame?>(null)
        SwingUtilities.invokeAndWait {
            val frame = JFrame()
            frame.isUndecorated = true
            frame.focusableWindowState = false
            frame.contentPane.background = Color(255, 0, 255)
            frame.setBounds(0, 0, screen.width, screen.height)
            frame.isVisible = true
            holder.set(frame)
        }
        return checkNotNull(holder.get())
    }

    /** Registra shown/hidden de toda janela com o prefixo do ciclo; a contagem não depende de corrida com o pré-aquecimento. */
    private fun listenWindowMarks(marks: ConcurrentLinkedQueue<WindowMark>) {
        Toolkit.getDefaultToolkit().addAWTEventListener(AWTEventListener { event ->
            val shown = event.id == ComponentEvent.COMPONENT_SHOWN
            if (!shown && event.id != ComponentEvent.COMPONENT_HIDDEN) return@AWTEventListener
            val title = titleOf(event.source)
            if (title != null && title.startsWith(TITLE_PREFIX)) marks.add(WindowMark(title, shown, System.nanoTime()))
        }, AWTEvent.COMPONENT_EVENT_MASK)
    }

    private fun titleOf(source: Any?): String? = when (source) {
        is Frame -> source.title
        is Dialog -> source.title
        else -> null
    }

    /** Amostrador daemon: o mais rápido possível, um quadrado 24x24 no centro da tela. */
    private fun startSampler(screen: Dimension, samples: ConcurrentLinkedQueue<PrewarmSample>, running: AtomicBoolean) {
        val robot = Robot()
        val centre = Rectangle(screen.width / 2 - SAMPLE_SIDE / 2, screen.height / 2 - SAMPLE_SIDE / 2, SAMPLE_SIDE, SAMPLE_SIDE)
        val thread = Thread {
            while (running.get()) {
                val before = System.nanoTime()
                val image = robot.createScreenCapture(centre)
                val after = System.nanoTime()
                samples.add(PrewarmSample(before, after, isLit(image)))
            }
        }
        thread.isDaemon = true
        thread.name = "prewarm-flash-sampler"
        thread.start()
    }

    /** Aceso = algum pixel com canal a mais de [LIT_THRESHOLD] do magenta (255, 0, 255). */
    private fun isLit(image: BufferedImage): Boolean {
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val rgb = image.getRGB(x, y)
                val red = (rgb shr 16) and 0xFF
                val green = (rgb shr 8) and 0xFF
                val blue = rgb and 0xFF
                if (Math.abs(red - 255) > LIT_THRESHOLD || green > LIT_THRESHOLD || Math.abs(blue - 255) > LIT_THRESHOLD) {
                    return true
                }
            }
        }
        return false
    }

    /** Espera shown e hidden do ciclo; teto de 5 s. Sem os dois, o ciclo conta como tempo esgotado. */
    private suspend fun awaitCycle(title: String, marks: ConcurrentLinkedQueue<WindowMark>): Boolean =
        withTimeoutOrNull(CYCLE_TIMEOUT_MS) {
            while (marks.none { it.title == title && it.shown } || marks.none { it.title == title && !it.shown }) {
                delay(5)
            }
            true
        } ?: false

    private fun resultOf(index: Int, title: String, marks: ConcurrentLinkedQueue<WindowMark>, prewarmMs: Long?): CycleResult {
        val shown = marks.firstOrNull { it.title == title && it.shown }?.nanos
        val hidden = marks.firstOrNull { it.title == title && !it.shown }?.nanos
        return CycleResult(index, overloadOf(index), shown, hidden, prewarmMs)
    }

    private fun writeReport(output: File, results: List<CycleResult>, samples: List<PrewarmSample>) {
        val cycles = results.filter { it.completed }.map {
            PrewarmCycle(it.index, it.overload, it.shownNanos!!, it.hiddenNanos!!)
        }
        val rawFlashes = PrewarmFlashSamples.flashesOf(samples)
        val flashes = PrewarmFlashSamples.classify(rawFlashes, cycles, FLASH_MARGIN_NANOS)
        val durations = results.mapNotNull { durationOf(it) }
        val prewarms = results.mapNotNull { it.prewarmMs?.toDouble() }
        val hz = PrewarmFlashSamples.samplesPerSecond(samples)
        val timeouts = results.count { !it.completed }
        val litCycles = flashes.mapNotNull { it.cycle }.distinct().size
        val outsideCycles = flashes.count { it.cycle == null }
        val flashJson = flashes.zip(rawFlashes).joinToString(",\n    ") { (f, raw) ->
            "{\"cycle\": ${f.cycle}, \"overload\": ${quoted(f.overload?.name)}, " +
                "\"startMsFromShown\": ${jsonNumber(f.startMsFromShown)}, \"endMsFromShown\": ${jsonNumber(f.endMsFromShown)}, " +
                "\"startMsFromHidden\": ${jsonNumber(f.startMsFromHidden)}, \"endMsFromHidden\": ${jsonNumber(f.endMsFromHidden)}, " +
                "\"durationMs\": ${jsonNumber((raw.endNanos - raw.startNanos) / 1_000_000.0)}, \"samples\": ${raw.samples}}"
        }
        val cycleJson = results.joinToString(",\n    ") { r ->
            "{\"index\": ${r.index}, \"overload\": ${quoted(r.overload.name)}, \"completed\": ${r.completed}, " +
                "\"durationMs\": ${jsonNumber(durationOf(r))}, \"prewarmMs\": ${r.prewarmMs}}"
        }
        val json = "{\n" +
            "  \"cycles\": ${results.size},\n" +
            "  \"completedCycles\": ${results.size - timeouts},\n" +
            "  \"timeoutCycles\": $timeouts,\n" +
            "  \"cyclesWithFlash\": $litCycles,\n" +
            "  \"flashesOutsideCycles\": $outsideCycles,\n" +
            "  \"samplesPerSecond\": ${jsonNumber(hz)},\n" +
            "  \"durationMs\": {\"median\": ${jsonNumber(PrewarmFlashSamples.median(durations))}, \"p95\": ${jsonNumber(PrewarmFlashSamples.percentile95(durations))}},\n" +
            "  \"prewarmMs\": {\"median\": ${jsonNumber(PrewarmFlashSamples.median(prewarms))}, \"p95\": ${jsonNumber(PrewarmFlashSamples.percentile95(prewarms))}},\n" +
            "  \"flashes\": [\n    $flashJson\n  ],\n" +
            "  \"cycleDetails\": [\n    $cycleJson\n  ]\n" +
            "}\n"
        File(output, "result.json").writeText(json)
        println("prewarm-flash cycles=${results.size} flashes=${flashes.size} timeouts=$timeouts " +
            "hz=${String.format(Locale.ROOT, "%.1f", hz)}")
    }

    private const val FLASH_MARGIN_NANOS = 150_000_000L

    private fun durationOf(result: CycleResult): Double? {
        val shown = result.shownNanos ?: return null
        val hidden = result.hiddenNanos ?: return null
        return (hidden - shown) / 1_000_000.0
    }

    private fun quoted(name: String?): String = if (name == null) "null" else "\"$name\""

    private fun jsonNumber(value: Double?): String = value?.toString() ?: "null"
}
