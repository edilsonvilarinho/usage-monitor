package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.UsageHistoryPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UsageHistoryLineChartTest {

    @Test
    fun `buildTimelineFractions uses real timestamp spacing`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 469),
            historyPoint("2026-05-06T18:30:00Z", 468),
            historyPoint("2026-05-06T20:00:00Z", 466)
        )

        val fractions = buildTimelineFractions(points)

        assertEquals(0f, fractions[0])
        assertEquals(0.25f, fractions[1])
        assertEquals(1f, fractions[2])
    }

    @Test
    fun `buildCurrencyAxis keeps small balance drifts from filling the whole chart`() {
        val axis = buildCurrencyAxis(listOf(469L, 468L, 466L))

        assertNotNull(axis)
        assertEquals(469f, axis.max)
        assertTrue(axis.max - axis.min >= 100f)
        assertTrue(axis.min >= 0f)
    }

    private fun historyPoint(capturedAt: String, displayUsed: Long): UsageHistoryPoint {
        return UsageHistoryPoint(
            capturedAt = Instant.parse(capturedAt),
            used = 0L,
            total = displayUsed,
            rawUsed = displayUsed,
            rawTotal = displayUsed,
            periodEndAt = Instant.parse("9999-12-31T23:59:59Z")
        )
    }
}
