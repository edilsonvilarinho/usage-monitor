package com.usagemonitor.domain

import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsagePeriodComparison
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-08-16T12:00:00Z")

class UsagePeriodComparisonTest {

    /** A janela anterior tem a mesma duração e encosta na atual. */
    @Test
    fun `the previous window mirrors the current one`() {
        assertEquals(
            HistoryRange.LAST_24_HOURS.windowStart(NOW),
            Instant.parse("2026-08-15T12:00:00Z")
        )
        assertEquals(
            HistoryRange.LAST_24_HOURS.previousWindowStart(NOW),
            Instant.parse("2026-08-14T12:00:00Z")
        )
        assertEquals(
            HistoryRange.LAST_7_DAYS.previousWindowStart(NOW),
            Instant.parse("2026-08-02T12:00:00Z")
        )
    }

    /** "Total" não tem período anterior contra o que comparar. */
    @Test
    fun `total has no previous window`() {
        assertNull(HistoryRange.TOTAL.previousWindowStart(NOW))
    }

    @Test
    fun `the change is measured against the previous consumption`() {
        val comparison = UsagePeriodComparison(currentDelta = 118L, previousDelta = 100L)

        assertEquals(0.18, comparison.changeRatio)
        assertTrue(comparison.isIncrease)
    }

    @Test
    fun `a drop reports a negative change`() {
        val comparison = UsagePeriodComparison(currentDelta = 80L, previousDelta = 100L)

        assertEquals(-0.2, comparison.changeRatio)
        assertFalse(comparison.isIncrease)
    }

    /** Dividir por zero daria "infinito por cento", que não informa nada. */
    @Test
    fun `without previous consumption the change is undefined`() {
        assertNull(UsagePeriodComparison(currentDelta = 50L, previousDelta = 0L).changeRatio)
    }
}
