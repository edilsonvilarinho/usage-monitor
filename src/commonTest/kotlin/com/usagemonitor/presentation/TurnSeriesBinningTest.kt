package com.usagemonitor.presentation

import com.usagemonitor.presentation.ui.components.BinMode
import com.usagemonitor.presentation.ui.components.binSeries
import com.usagemonitor.presentation.ui.components.dropIndices
import com.usagemonitor.presentation.ui.components.scaleCeiling
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TurnSeriesBinningTest {

    @Test
    fun `series smaller than the target passes through untouched`() {
        val values = listOf(1L, 2L, 3L)

        assertEquals(values, binSeries(values, targetBins = 10, mode = BinMode.SUM))
    }

    @Test
    fun `sum mode adds the turns of each bin`() {
        val values = listOf(1L, 2L, 3L, 4L, 5L, 6L)

        assertEquals(listOf(3L, 7L, 11L), binSeries(values, targetBins = 3, mode = BinMode.SUM))
    }

    @Test
    fun `last mode keeps the live value of each bin`() {
        val values = listOf(1L, 2L, 3L, 4L, 5L, 6L)

        assertEquals(listOf(2L, 4L, 6L), binSeries(values, targetBins = 3, mode = BinMode.LAST))
    }

    @Test
    fun `max mode keeps the peak of each bin`() {
        val values = listOf(1L, 9L, 3L, 4L, 2L, 6L)

        assertEquals(listOf(9L, 4L, 6L), binSeries(values, targetBins = 3, mode = BinMode.MAX))
    }

    @Test
    fun `uneven series keeps the remainder in a shorter last bin`() {
        val values = listOf(1L, 1L, 1L, 1L, 1L)

        val binned = binSeries(values, targetBins = 2, mode = BinMode.SUM)

        assertEquals(listOf(3L, 2L), binned)
    }

    @Test
    fun `long series is condensed to at most the target`() {
        val values = List(251) { index -> index.toLong() }

        val binned = binSeries(values, targetBins = 40, mode = BinMode.LAST)

        assertTrue(binned.size <= 40)
        assertEquals(250L, binned.last())
    }

    @Test
    fun `target of zero yields nothing`() {
        assertEquals(emptyList(), binSeries(listOf(1L, 2L), targetBins = 0, mode = BinMode.SUM))
    }

    @Test
    fun `ceiling ignores a single outlier`() {
        // Um pico isolado achatava todo o resto do gráfico de cache write.
        val values = List(99) { 100L } + listOf(1_000_000L)

        assertEquals(100L, scaleCeiling(values))
    }

    @Test
    fun `ceiling never falls below the median`() {
        val values = listOf(10L, 20L, 30L)

        assertTrue(scaleCeiling(values) >= 20L)
    }

    @Test
    fun `ceiling of an empty or zeroed series is zero`() {
        assertEquals(0L, scaleCeiling(emptyList()))
        assertEquals(0L, scaleCeiling(listOf(0L, 0L)))
    }

    @Test
    fun `drops mark where the context shrank`() {
        // Índices 2 e 4 são compactações.
        val values = listOf(100L, 200L, 50L, 120L, 60L)

        assertEquals(listOf(2, 4), dropIndices(values))
    }

    @Test
    fun `a monotonic series has no drops`() {
        assertEquals(emptyList(), dropIndices(listOf(1L, 2L, 3L)))
        assertEquals(emptyList(), dropIndices(listOf(5L)))
        assertEquals(emptyList(), dropIndices(emptyList()))
    }
}
