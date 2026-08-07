package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageBucketGranularity
import com.usagemonitor.domain.entity.UsageBucketTotal
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryScreenFormattingTest {

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    @Test
    fun `formatCumulativeConsumption keeps percentage when within a single cycle`() {
        val value = formatCumulativeConsumption(
            delta = 70.0,
            total = 100L,
            unit = UsageUnit.PERCENTAGE,
            language = AppLanguage.PT
        )

        assertEquals("70 %", value)
    }

    @Test
    fun `formatCumulativeConsumption switches to quota multiplier when it spans multiple cycles`() {
        val value = formatCumulativeConsumption(
            delta = 28620.0,
            total = 4500L,
            unit = UsageUnit.PERCENTAGE,
            language = AppLanguage.PT
        )

        assertEquals("6,4× a cota", value)
    }

    @Test
    fun `formatCumulativeConsumption quota multiplier is localized`() {
        val value = formatCumulativeConsumption(
            delta = 28620.0,
            total = 4500L,
            unit = UsageUnit.TOKENS,
            language = AppLanguage.EN
        )

        assertEquals("6.4× the quota", value)
    }

    @Test
    fun `formatCumulativeConsumption for REQUESTS never shows percentage`() {
        val value = formatCumulativeConsumption(
            delta = 12000.0,
            total = 4500L,
            unit = UsageUnit.REQUESTS,
            language = AppLanguage.PT
        )

        assertEquals("12K req", value)
    }

    @Test
    fun `formatCumulativeConsumption for CURRENCY_USD formats as cents`() {
        val value = formatCumulativeConsumption(
            delta = 1234.0,
            total = 10000L,
            unit = UsageUnit.CURRENCY_USD,
            language = AppLanguage.PT
        )

        assertEquals("$12.34", value)
    }

    @Test
    fun `formatCumulativeConsumption returns placeholder when total is unknown`() {
        val value = formatCumulativeConsumption(
            delta = 100.0,
            total = 0L,
            unit = UsageUnit.PERCENTAGE,
            language = AppLanguage.PT
        )

        assertEquals("— %", value)
    }

    @Test
    fun `granularityForRange maps each chip to the expected granularity`() {
        assertEquals(UsageBucketGranularity.HOUR, granularityForRange(HistoryRange.LAST_24_HOURS))
        assertEquals(UsageBucketGranularity.DAY, granularityForRange(HistoryRange.LAST_7_DAYS))
        assertEquals(UsageBucketGranularity.DAY, granularityForRange(HistoryRange.LAST_30_DAYS))
        assertEquals(UsageBucketGranularity.WEEK, granularityForRange(HistoryRange.TOTAL))
    }

    @Test
    fun `fillBucketGaps fills missing days with zero and keeps existing totals`() {
        val existing = listOf(
            UsageBucketTotal(Instant.parse("2026-08-01T00:00:00-03:00"), 30L),
            UsageBucketTotal(Instant.parse("2026-08-03T00:00:00-03:00"), 10L)
        )

        val filled = fillBucketGaps(
            buckets = existing,
            windowStart = Instant.parse("2026-08-01T00:00:00-03:00"),
            granularity = UsageBucketGranularity.DAY,
            now = Instant.parse("2026-08-03T12:00:00-03:00"),
            timeZone = saoPaulo
        )

        assertEquals(3, filled.size)
        assertEquals(30L, filled[0].delta)
        assertEquals(0L, filled[1].delta)
        assertEquals(10L, filled[2].delta)
    }

    @Test
    fun `fillBucketGaps returns empty when window start is after now`() {
        val filled = fillBucketGaps(
            buckets = emptyList(),
            windowStart = Instant.parse("2026-08-05T00:00:00-03:00"),
            granularity = UsageBucketGranularity.DAY,
            now = Instant.parse("2026-08-01T00:00:00-03:00"),
            timeZone = saoPaulo
        )

        assertTrue(filled.isEmpty())
    }
}
