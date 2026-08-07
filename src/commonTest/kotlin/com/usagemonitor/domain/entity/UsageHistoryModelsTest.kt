package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageHistoryModelsTest {

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    private fun point(capturedAt: String, used: Long): UsageHistoryPoint {
        return UsageHistoryPoint(
            capturedAt = Instant.parse(capturedAt),
            used = used,
            total = 100L,
            rawUsed = used,
            rawTotal = 100L,
            periodEndAt = Instant.parse("2026-08-31T23:59:59Z")
        )
    }

    @Test
    fun `bucketedConsumption sum matches cumulativePositiveDelta across resets`() {
        val points = listOf(
            point("2026-08-01T10:00:00-03:00", 10),
            point("2026-08-01T14:00:00-03:00", 90),
            point("2026-08-01T15:00:00-03:00", 5),
            point("2026-08-02T09:00:00-03:00", 60),
            point("2026-08-03T09:00:00-03:00", 40)
        )

        val expectedTotal = cumulativePositiveDelta(points, UsageUnit.PERCENTAGE)
        val buckets = bucketedConsumption(points, UsageUnit.PERCENTAGE, UsageBucketGranularity.DAY, saoPaulo)

        assertEquals(expectedTotal, buckets.sumOf { it.delta })
    }

    @Test
    fun `bucketedConsumption ignores a drop across a day boundary`() {
        // Reset exatamente na virada do dia: 23h (dia 1, used=90) -> 01h (dia 2, used=20).
        val points = listOf(
            point("2026-08-01T23:00:00-03:00", 90),
            point("2026-08-02T01:00:00-03:00", 20)
        )

        val buckets = bucketedConsumption(points, UsageUnit.PERCENTAGE, UsageBucketGranularity.DAY, saoPaulo)

        // Queda (reset), não incremento — nada é somado.
        assertTrue(buckets.isEmpty())
    }

    @Test
    fun `bucketedConsumption attributes growth across a day boundary to the later day`() {
        val points = listOf(
            point("2026-08-01T23:00:00-03:00", 10),
            point("2026-08-02T01:00:00-03:00", 40)
        )

        val buckets = bucketedConsumption(points, UsageUnit.PERCENTAGE, UsageBucketGranularity.DAY, saoPaulo)

        assertEquals(1, buckets.size)
        assertEquals(30L, buckets.first().delta)
        assertEquals(
            Instant.parse("2026-08-02T00:00:00-03:00"),
            buckets.first().bucketStart
        )
    }

    @Test
    fun `bucketedConsumption returns empty list for buckets without growth`() {
        val points = listOf(
            point("2026-08-01T10:00:00-03:00", 50),
            point("2026-08-01T11:00:00-03:00", 30)
        )

        val buckets = bucketedConsumption(points, UsageUnit.PERCENTAGE, UsageBucketGranularity.HOUR, saoPaulo)

        assertTrue(buckets.isEmpty())
    }

    @Test
    fun `bucketedConsumption for CURRENCY_USD sums balance drops`() {
        val points = listOf(
            point("2026-08-01T10:00:00-03:00", 1000),
            point("2026-08-01T11:00:00-03:00", 700),
            point("2026-08-01T12:00:00-03:00", 400)
        )

        val buckets = bucketedConsumption(points, UsageUnit.CURRENCY_USD, UsageBucketGranularity.DAY, saoPaulo)

        assertEquals(1, buckets.size)
        assertEquals(600L, buckets.first().delta)
    }

    @Test
    fun `truncateToBucket aligns to Monday for WEEK granularity`() {
        // 2026-08-06 é uma quinta-feira.
        val truncated = truncateToBucket(
            Instant.parse("2026-08-06T15:00:00-03:00"),
            UsageBucketGranularity.WEEK,
            saoPaulo
        )

        assertEquals(Instant.parse("2026-08-03T00:00:00-03:00"), truncated)
    }
}
