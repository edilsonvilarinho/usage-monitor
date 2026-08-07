package com.usagemonitor.domain

import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.riskSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageForecastRiskTest {

    private val referenceAt = Instant.parse("2025-01-01T00:00:00Z")

    @Test
    fun `ResetsBeforeExhaustion maps to ON_TRACK without exhaustion instant`() {
        val summary = UsageForecast.ResetsBeforeExhaustion.riskSummary(
            referenceAt = referenceAt,
            periodEndAt = referenceAt.plusHours(5)
        )

        assertEquals(UsageRiskLevel.ON_TRACK, summary?.level)
        assertNull(summary?.estimatedExhaustionAt)
    }

    @Test
    fun `exhaustion using less than half the remaining time maps to WILL_EXCEED`() {
        val periodEndAt = referenceAt.plusHours(10)
        val exhaustionAt = referenceAt.plusHours(4) // 40% do tempo restante

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = periodEndAt
        )

        assertEquals(UsageRiskLevel.WILL_EXCEED, summary?.level)
        assertEquals(exhaustionAt, summary?.estimatedExhaustionAt)
    }

    @Test
    fun `exhaustion at exactly half the remaining time maps to AT_RISK`() {
        val periodEndAt = referenceAt.plusHours(10)
        val exhaustionAt = referenceAt.plusHours(5) // exatamente 50%

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = periodEndAt
        )

        assertEquals(UsageRiskLevel.AT_RISK, summary?.level)
    }

    @Test
    fun `exhaustion using more than half the remaining time maps to AT_RISK`() {
        val periodEndAt = referenceAt.plusHours(10)
        val exhaustionAt = referenceAt.plusHours(9) // 90% do tempo restante

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = periodEndAt
        )

        assertEquals(UsageRiskLevel.AT_RISK, summary?.level)
        assertEquals(exhaustionAt, summary?.estimatedExhaustionAt)
    }

    @Test
    fun `NoGrowth has no risk summary`() {
        val summary = UsageForecast.NoGrowth.riskSummary(
            referenceAt = referenceAt,
            periodEndAt = referenceAt.plusHours(5)
        )

        assertNull(summary)
    }

    @Test
    fun `InsufficientData has no risk summary`() {
        val summary = UsageForecast.InsufficientData.riskSummary(
            referenceAt = referenceAt,
            periodEndAt = referenceAt.plusHours(5)
        )

        assertNull(summary)
    }

    private fun Instant.plusHours(hours: Long): Instant {
        return Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + hours * 3_600_000L)
    }
}
