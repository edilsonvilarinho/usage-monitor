package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrewarmFlashSamplesTest {

    private val ms = 1_000_000L
    private val margin = 150 * ms

    private fun sample(startMs: Long, endMs: Long, lit: Boolean) =
        PrewarmSample(startMs * ms, endMs * ms, lit)

    private fun cycle(index: Int, shownMs: Long, hiddenMs: Long, overload: PrewarmOverload = PrewarmOverload.WINDOW_STATE) =
        PrewarmCycle(index, overload, shownMs * ms, hiddenMs * ms)

    @Test
    fun nothingLitHasNoFlash() {
        val samples = listOf(sample(0, 10, false), sample(10, 20, false))

        assertEquals(emptyList<PrewarmFlash>(), PrewarmFlashSamples.flashesOf(samples))
    }

    @Test
    fun consecutiveLitSamplesFormOneFlash() {
        val samples = listOf(sample(0, 10, false), sample(10, 20, true), sample(20, 30, true), sample(30, 40, false))

        assertEquals(listOf(PrewarmFlash(10 * ms, 30 * ms, 2)), PrewarmFlashSamples.flashesOf(samples))
    }

    @Test
    fun runsSeparatedByDarkSamplesFormTwoFlashes() {
        val samples = listOf(sample(0, 10, true), sample(10, 20, false), sample(20, 30, true))

        assertEquals(
            listOf(PrewarmFlash(0 * ms, 10 * ms, 1), PrewarmFlash(20 * ms, 30 * ms, 1)),
            PrewarmFlashSamples.flashesOf(samples)
        )
    }

    @Test
    fun flashOutsideEveryCycleHasNoCycle() {
        val flashes = listOf(PrewarmFlash(5000 * ms, 6000 * ms, 1))
        val cycles = listOf(cycle(0, 1000, 2000))

        val expected = ClassifiedPrewarmFlash(
            cycle = null, overload = null,
            startMsFromShown = null, endMsFromShown = null,
            startMsFromHidden = null, endMsFromHidden = null
        )
        assertEquals(listOf(expected), PrewarmFlashSamples.classify(flashes, cycles, margin))
    }

    @Test
    fun flashInsideCycleNamesCycleAndOverloadWithOffsets() {
        val cycles = listOf(
            cycle(0, 1000, 1400),
            cycle(1, 2000, 2500, PrewarmOverload.DIALOG_STATE)
        )
        val flashes = listOf(PrewarmFlash(1900 * ms, 2100 * ms, 1))

        val expected = ClassifiedPrewarmFlash(
            cycle = 1, overload = PrewarmOverload.DIALOG_STATE,
            startMsFromShown = -100.0, endMsFromShown = 100.0,
            startMsFromHidden = -600.0, endMsFromHidden = -400.0
        )
        assertEquals(listOf(expected), PrewarmFlashSamples.classify(flashes, cycles, margin))
    }

    @Test
    fun flashBeforeShownWithinMarginNamesCycle() {
        val flashes = listOf(PrewarmFlash(900 * ms, 950 * ms, 1))
        val cycles = listOf(cycle(0, 1000, 1400))

        val expected = ClassifiedPrewarmFlash(
            cycle = 0, overload = PrewarmOverload.WINDOW_STATE,
            startMsFromShown = -100.0, endMsFromShown = -50.0,
            startMsFromHidden = -500.0, endMsFromHidden = -450.0
        )
        assertEquals(listOf(expected), PrewarmFlashSamples.classify(flashes, cycles, margin))
    }

    @Test
    fun flashAfterHiddenWithinMarginNamesCycle() {
        val flashes = listOf(PrewarmFlash(1450 * ms, 1500 * ms, 1))
        val cycles = listOf(cycle(0, 1000, 1400))

        val expected = ClassifiedPrewarmFlash(
            cycle = 0, overload = PrewarmOverload.WINDOW_STATE,
            startMsFromShown = 450.0, endMsFromShown = 500.0,
            startMsFromHidden = 50.0, endMsFromHidden = 100.0
        )
        assertEquals(listOf(expected), PrewarmFlashSamples.classify(flashes, cycles, margin))
    }

    @Test
    fun flashBeyondMarginHasNoCycle() {
        val cycles = listOf(cycle(0, 1000, 1400))
        val afterMargin = listOf(PrewarmFlash(1600 * ms, 1650 * ms, 1))
        val beforeMargin = listOf(PrewarmFlash(800 * ms, 840 * ms, 1))

        val unmatched = ClassifiedPrewarmFlash(null, null, null, null, null, null)
        assertEquals(listOf(unmatched), PrewarmFlashSamples.classify(afterMargin, cycles, margin))
        assertEquals(listOf(unmatched), PrewarmFlashSamples.classify(beforeMargin, cycles, margin))
    }

    @Test
    fun twoCyclesCloseTogetherPicksNearestUnextendedInterval() {
        val cycles = listOf(
            cycle(0, 1000, 1100),
            cycle(1, 1200, 1300, PrewarmOverload.DIALOG_STATE)
        )
        val flashes = listOf(PrewarmFlash(1150 * ms, 1160 * ms, 1))

        val expected = ClassifiedPrewarmFlash(
            cycle = 1, overload = PrewarmOverload.DIALOG_STATE,
            startMsFromShown = -50.0, endMsFromShown = -40.0,
            startMsFromHidden = -150.0, endMsFromHidden = -140.0
        )
        assertEquals(listOf(expected), PrewarmFlashSamples.classify(flashes, cycles, margin))
    }

    @Test
    fun medianOfOddAndEvenCounts() {
        assertEquals(2.0, PrewarmFlashSamples.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, PrewarmFlashSamples.median(listOf(1.0, 2.0, 3.0, 4.0)))
        assertNull(PrewarmFlashSamples.median(emptyList()))
    }

    @Test
    fun percentile95UsesNearestRank() {
        val values = (1..20).map { it.toDouble() }

        assertEquals(19.0, PrewarmFlashSamples.percentile95(values))
        assertNull(PrewarmFlashSamples.percentile95(emptyList()))
    }

    @Test
    fun samplesPerSecondOverWholeRun() {
        val samples = (0 until 10).map { sample(it * 100L, (it + 1) * 100L, false) }

        assertEquals(10.0, PrewarmFlashSamples.samplesPerSecond(samples))
        assertEquals(0.0, PrewarmFlashSamples.samplesPerSecond(emptyList()))
    }

    @Test
    fun prewarmMillisComesFromBreadcrumbMessage() {
        assertEquals(37L, PrewarmFlashSamples.prewarmMillisOf("janela sonda histórico pré-aquecida em 37 ms"))
        assertNull(PrewarmFlashSamples.prewarmMillisOf("outra mensagem"))
    }

    @Test
    fun openedMillisComesFromFirstOpenBreadcrumbOnly() {
        assertEquals(42L, PrewarmFlashSamples.openedMillisOf("janela sonda flash 0 pintada em 42 ms (primeira abertura)"))
        assertNull(PrewarmFlashSamples.openedMillisOf("janela sonda flash 0 pintada em 42 ms (reabertura)"))
        assertNull(PrewarmFlashSamples.openedMillisOf("janela sonda flash 0 pré-aquecida em 37 ms"))
    }

    @Test
    fun samplesBeforeCutKeepOnlyStartsBeforeCut() {
        val samples = listOf(sample(0, 10, true), sample(10, 20, true), sample(20, 30, true))

        assertEquals(listOf(samples[0], samples[1]), PrewarmFlashSamples.samplesBeforeCut(samples, 15 * ms))
    }

    @Test
    fun cutAtSampleStartDropsItsFlash() {
        val samples = listOf(sample(0, 10, false), sample(10, 20, true))

        assertEquals(
            emptyList<PrewarmFlash>(),
            PrewarmFlashSamples.flashesOf(PrewarmFlashSamples.samplesBeforeCut(samples, 10 * ms))
        )
    }

    @Test
    fun modeFromPropertyValue() {
        assertEquals(PrewarmFlashMode.PREWARM, PrewarmFlashSamples.modeOf(null))
        assertEquals(PrewarmFlashMode.PREWARM, PrewarmFlashSamples.modeOf(""))
        assertEquals(PrewarmFlashMode.PREWARM, PrewarmFlashSamples.modeOf("prewarm"))
        assertEquals(PrewarmFlashMode.CONTROL, PrewarmFlashSamples.modeOf("control"))
        assertEquals(PrewarmFlashMode.OPEN_AFTER, PrewarmFlashSamples.modeOf("openAfter"))
        assertNull(PrewarmFlashSamples.modeOf("bogus"))
    }

    @Test
    fun overloadAlternatesByIndexFromFirst() {
        assertEquals(PrewarmOverload.WINDOW_STATE, PrewarmFlashSamples.overloadFor(0, PrewarmOverload.WINDOW_STATE))
        assertEquals(PrewarmOverload.DIALOG_STATE, PrewarmFlashSamples.overloadFor(1, PrewarmOverload.WINDOW_STATE))
        assertEquals(PrewarmOverload.DIALOG_STATE, PrewarmFlashSamples.overloadFor(0, PrewarmOverload.DIALOG_STATE))
        assertEquals(PrewarmOverload.WINDOW_STATE, PrewarmFlashSamples.overloadFor(3, PrewarmOverload.DIALOG_STATE))
    }

    @Test
    fun firstOverloadFromPropertyValue() {
        assertEquals(PrewarmOverload.WINDOW_STATE, PrewarmFlashSamples.firstOverloadOf(null))
        assertEquals(PrewarmOverload.DIALOG_STATE, PrewarmFlashSamples.firstOverloadOf("dialog"))
        assertNull(PrewarmFlashSamples.firstOverloadOf("bogus"))
    }
}
