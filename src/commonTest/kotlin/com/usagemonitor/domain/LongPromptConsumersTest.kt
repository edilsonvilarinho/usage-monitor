package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamTrendRow
import com.usagemonitor.domain.entity.UsageTokenCounts
import com.usagemonitor.domain.entity.WindowedSessionAccumulator
import com.usagemonitor.domain.entity.buildTeamUsageTrend
import com.usagemonitor.domain.entity.toActivityHeatmap
import com.usagemonitor.domain.entity.toUsageBreakdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Os quatro consumidores de custo de grupo: 76 turnos baixos e 124 altos de Haiku 5.5. */
class LongPromptConsumersTest {

    @Test
    fun `breakdown prices group by both tiers`() {
        val rows = listOf(
            CliUsageGroupRow(
                sessionId = "s1",
                model = HAIKU,
                turnCount = 200,
                inputTokens = 28_600_000L,
                outputTokens = 200_000L,
                longPrompt = HIGH_PART
            )
        )

        val bucket = rows.toUsageBreakdown().byModel.single()

        assertEquals(COST_TWO_TIERS, bucket.costMicros)
        assertEquals(0, bucket.unpricedTurnCount)
    }

    @Test
    fun `breakdown without long part is unpriced`() {
        val rows = listOf(
            CliUsageGroupRow(
                sessionId = "s1",
                model = HAIKU,
                turnCount = 200,
                inputTokens = 28_600_000L,
                outputTokens = 200_000L
            )
        )

        val bucket = rows.toUsageBreakdown().byModel.single()

        assertEquals(0L, bucket.costMicros)
        assertEquals(200, bucket.unpricedTurnCount)
    }

    @Test
    fun `breakdown cache savings follow the tier`() {
        val rows = listOf(
            CliUsageGroupRow(
                sessionId = "s1",
                model = HAIKU,
                turnCount = 2,
                inputTokens = 250_000L,
                outputTokens = 2_000L,
                cacheReadTokens = 100_000L,
                longPrompt = UsageTokenCounts(inputTokens = 200_000L, outputTokens = 1_000L, cacheReadTokens = 100_000L)
            )
        )

        val bucket = rows.toUsageBreakdown().byModel.single()

        assertEquals(113_000L, bucket.costMicros)
        assertEquals(45_000L, bucket.cacheSavingsMicros)
    }

    @Test
    fun `breakdown model without tier ignores long part`() {
        val base = CliUsageGroupRow(
            sessionId = "s1",
            model = SONNET,
            turnCount = 200,
            inputTokens = 28_600_000L,
            outputTokens = 200_000L
        )

        val withoutPart = listOf(base).toUsageBreakdown().byModel.single().costMicros
        val withPart = listOf(base.copy(longPrompt = HIGH_PART)).toUsageBreakdown().byModel.single().costMicros

        assertTrue(withoutPart > 0L)
        assertEquals(withoutPart, withPart)
    }

    @Test
    fun `activity heatmap prices hour by both tiers`() {
        val rows = listOf(
            CliHourlyUsageRow(
                hourStartMillis = instantMillis("2026-08-10T12:00:00Z"),
                model = HAIKU,
                turnCount = 200,
                inputTokens = 28_600_000L,
                outputTokens = 200_000L,
                longPrompt = HIGH_PART
            )
        )

        val cost = rows.toActivityHeatmap(TimeZone.UTC).cells.sumOf { cell -> cell.costMicros }

        assertEquals(COST_TWO_TIERS, cost)
    }

    @Test
    fun `activity heatmap without long part adds no cost`() {
        val rows = listOf(
            CliHourlyUsageRow(
                hourStartMillis = instantMillis("2026-08-10T12:00:00Z"),
                model = HAIKU,
                turnCount = 200,
                inputTokens = 28_600_000L,
                outputTokens = 200_000L
            )
        )

        val cost = rows.toActivityHeatmap(TimeZone.UTC).cells.sumOf { cell -> cell.costMicros }

        assertEquals(0L, cost)
    }

    @Test
    fun `team trend prices day by both tiers`() {
        val rows = listOf(
            TeamTrendRow(
                deviceId = "device-1",
                dayStartMillis = instantMillis("2026-08-10T00:00:00Z"),
                model = HAIKU,
                turnCount = 200,
                inputTokens = 28_600_000L,
                outputTokens = 200_000L,
                longPrompt = HIGH_PART
            )
        )

        val trend = buildTeamUsageTrend(
            rows = rows,
            members = listOf(TeamMemberIdentity(deviceId = "device-1", alias = "edilson")),
            days = listOf(LocalDate(2026, 8, 10)),
            timeZone = TimeZone.UTC
        )

        assertEquals(COST_TWO_TIERS, trend.members.single().points.single().costMicros)
    }

    @Test
    fun `team trend without long part is unpriced`() {
        val rows = listOf(
            TeamTrendRow(
                deviceId = "device-1",
                dayStartMillis = instantMillis("2026-08-10T00:00:00Z"),
                model = HAIKU,
                turnCount = 200,
                inputTokens = 28_600_000L,
                outputTokens = 200_000L
            )
        )

        val point = buildTeamUsageTrend(
            rows = rows,
            members = listOf(TeamMemberIdentity(deviceId = "device-1", alias = "edilson")),
            days = listOf(LocalDate(2026, 8, 10)),
            timeZone = TimeZone.UTC
        ).members.single().points.single()

        assertEquals(0L, point.costMicros)
        assertEquals(200, point.unpricedTurnCount)
    }

    @Test
    fun `accumulator prices group by both tiers`() {
        val accumulator = WindowedSessionAccumulator(sessionId = "s1")

        accumulator.addModelGroup(
            model = HAIKU,
            turnCount = 200,
            firstTsMillis = 0L,
            lastTsMillis = 1L,
            inputTokens = 28_600_000L,
            outputTokens = 200_000L,
            cacheReadTokens = 0L,
            cacheWrite5mTokens = 0L,
            cacheWrite1hTokens = 0L,
            longPrompt = HIGH_PART
        )
        val summary = accumulator.toSummary()

        assertEquals(COST_TWO_TIERS, summary.costMicros)
        assertEquals(COST_TWO_TIERS, summary.modelBreakdown.single().costMicros)
    }

    @Test
    fun `accumulator without long part is unpriced`() {
        val accumulator = WindowedSessionAccumulator(sessionId = "s1")

        accumulator.addModelGroup(
            model = HAIKU,
            turnCount = 200,
            firstTsMillis = 0L,
            lastTsMillis = 1L,
            inputTokens = 28_600_000L,
            outputTokens = 200_000L,
            cacheReadTokens = 0L,
            cacheWrite5mTokens = 0L,
            cacheWrite1hTokens = 0L,
            longPrompt = null
        )
        val summary = accumulator.toSummary()

        assertEquals(0L, summary.costMicros)
        assertEquals(200, summary.unpricedTurnCount)
    }

    private fun instantMillis(text: String): Long = Instant.parse(text).toEpochMilliseconds()

    private companion object {
        const val HAIKU = "claude-haiku-5-5"
        const val SONNET = "claude-sonnet-4-6"
        const val COST_TWO_TIERS = 13_128_000L
        val HIGH_PART = UsageTokenCounts(inputTokens = 24_800_000L, outputTokens = 124_000L)
    }
}
