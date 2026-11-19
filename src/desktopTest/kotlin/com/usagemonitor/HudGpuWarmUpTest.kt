package com.usagemonitor

import com.usagemonitor.AutoStartManager.Platform
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O aquecimento da GPU da HUD (#627). Só Windows: é a única plataforma medida,
 * e com "Reduzir animações" não há tween para aquecer.
 */
class HudGpuWarmUpTest {

    @Test
    fun `warm up runs three cycles on Windows without reduced motion`() {
        assertEquals(3, hudGpuWarmUpCycles(Platform.WINDOWS, motionReduced = false))
    }

    @Test
    fun `warm up is skipped on every platform when motion is reduced`() {
        Platform.entries.forEach { platform ->
            assertEquals(0, hudGpuWarmUpCycles(platform, motionReduced = true), "platform=$platform")
        }
    }

    @Test
    fun `warm up is skipped outside Windows`() {
        listOf(Platform.LINUX, Platform.MACOS, Platform.OTHER).forEach { platform ->
            assertEquals(0, hudGpuWarmUpCycles(platform, motionReduced = false), "platform=$platform")
        }
    }

    /** Valores medidos no protótipo do #627: alfa 0,01, 3 s de atraso, 600 ms de abertura, 400 ms de fechamento. */
    @Test
    fun `warm up constants match the measured prototype`() {
        assertEquals(0.01f, HUD_GPU_WARM_UP_ALPHA)
        assertEquals(3_000L, HUD_GPU_WARM_UP_START_DELAY_MILLIS)
        assertEquals(600L, HUD_GPU_WARM_UP_OPEN_MILLIS)
        assertEquals(400L, HUD_GPU_WARM_UP_CLOSE_MILLIS)
    }

    /** Cada chamada leva o tempo virtual em que aconteceu: a ordem e o instante são o contrato. */
    private class RecordingWarmUpTarget(private val scheduler: TestCoroutineScheduler) : HudGpuWarmUpTarget {
        val calls = mutableListOf<String>()
        var onFrame: () -> Unit = {}

        override fun setWarming(warming: Boolean) = record("warming:$warming")
        override fun setWindowOpen(open: Boolean) = record("open:$open")
        override fun setExpanded(expanded: Boolean) = record("expanded:$expanded")

        override suspend fun awaitFrame() {
            record("frame")
            onFrame()
        }

        private fun record(event: String) {
            calls += "$event@${scheduler.currentTime}"
        }
    }

    @Test
    fun `zero or negative cycles are skipped without touching the target or waiting`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)

        assertEquals(HudGpuWarmUpOutcome.SKIPPED, runHudGpuWarmUp(0, target) { false })
        assertEquals(HudGpuWarmUpOutcome.SKIPPED, runHudGpuWarmUp(-1, target) { false })

        assertEquals(emptyList(), target.calls)
        assertEquals(0L, testScheduler.currentTime)
    }

    /** Sequência inteira de um ciclo: atraso, aquecimento ligado, abertura, quadro, expansão, fechamento, desligamento. */
    @Test
    fun `one cycle follows the prototype sequence and completes`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)

        val outcome = runHudGpuWarmUp(1, target) { false }

        assertEquals(HudGpuWarmUpOutcome.COMPLETED, outcome)
        assertEquals(
            listOf(
                "warming:true@3000",
                "open:true@3000",
                "frame@3000",
                "expanded:true@3000",
                "expanded:false@3600",
                "open:false@4000",
                "warming:false@4000"
            ),
            target.calls
        )
    }

    /** A janela fica aberta entre os ciclos: só o fim a fecha, e cada ciclo a reabre. */
    @Test
    fun `window stays open across cycles and closes once at the end`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)

        val outcome = runHudGpuWarmUp(2, target) { false }

        assertEquals(HudGpuWarmUpOutcome.COMPLETED, outcome)
        assertEquals(
            listOf(
                "warming:true@3000",
                "open:true@3000",
                "frame@3000",
                "expanded:true@3000",
                "expanded:false@3600",
                "open:true@4000",
                "frame@4000",
                "expanded:true@4000",
                "expanded:false@4600",
                "open:false@5000",
                "warming:false@5000"
            ),
            target.calls
        )
    }

    /** Uso real antes do primeiro passo: só o aquecimento é desligado; a janela e a expansão não são tocadas. */
    @Test
    fun `interrupted before the first step only turns warming off`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)

        val outcome = runHudGpuWarmUp(3, target) { true }

        assertEquals(HudGpuWarmUpOutcome.INTERRUPTED, outcome)
        assertEquals(listOf("warming:false@0"), target.calls)
    }

    /**
     * Uso real durante o quadro: nada depois do quadro acontece, e a janela e a
     * expansão ficam como estavam — quem as dono é o hover.
     */
    @Test
    fun `interrupted mid cycle stops before expanding and leaves window state alone`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)
        var stop = false
        target.onFrame = { stop = true }

        val outcome = runHudGpuWarmUp(3, target) { stop }

        assertEquals(HudGpuWarmUpOutcome.INTERRUPTED, outcome)
        assertEquals(
            listOf(
                "warming:true@3000",
                "open:true@3000",
                "frame@3000",
                "warming:false@3000"
            ),
            target.calls
        )
    }

    /** Uso real depois do primeiro ciclo: o segundo não começa. */
    @Test
    fun `interrupted between cycles does not start the next one`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)

        val outcome = runHudGpuWarmUp(2, target) { testScheduler.currentTime >= 4_000L }

        assertEquals(HudGpuWarmUpOutcome.INTERRUPTED, outcome)
        assertEquals(
            listOf(
                "warming:true@3000",
                "open:true@3000",
                "frame@3000",
                "expanded:true@3000",
                "expanded:false@3600",
                "warming:false@4000"
            ),
            target.calls
        )
    }

    /** Cancelamento da coroutine no meio da abertura: o `finally` desliga o aquecimento e a exceção segue. */
    @Test
    fun `cancellation mid cycle still turns warming off`() = runTest {
        val target = RecordingWarmUpTarget(testScheduler)
        val job = launch { runHudGpuWarmUp(1, target) { false } }

        testScheduler.advanceTimeBy(3_300L)
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(
            listOf(
                "warming:true@3000",
                "open:true@3000",
                "frame@3000",
                "expanded:true@3000",
                "warming:false@3300"
            ),
            target.calls
        )
    }
}
