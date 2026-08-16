package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamTrendRow
import com.usagemonitor.domain.entity.buildTeamUsageTrend
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OPUS = "claude-opus-5"
private val BRT = TimeZone.of("America/Sao_Paulo")

private val DAYS = listOf(
    LocalDate(2026, 8, 9),
    LocalDate(2026, 8, 10),
    LocalDate(2026, 8, 11)
)

private val MEMBERS = listOf(
    TeamMemberIdentity(deviceId = "device-1", alias = "edilson", hostName = "DESKTOP-A1"),
    TeamMemberIdentity(deviceId = "device-2", alias = "maria", hostName = "NOTE-B2")
)

class TeamUsageTrendTest {

    /** Sem um ponto por dia a linha pularia dias e sugeriria continuidade. */
    @Test
    fun `every member gets a point on every day of the window`() {
        val trend = buildTeamUsageTrend(
            rows = listOf(row("device-1", "2026-08-10T12:00:00Z", inputTokens = 1_000_000)),
            members = MEMBERS,
            days = DAYS,
            timeZone = BRT
        )

        assertEquals(2, trend.members.size)
        assertTrue(trend.members.all { member -> member.points.size == 3 })
        assertEquals(DAYS, trend.members.first().points.map { point -> point.date })
    }

    /** O servidor agrupa em UTC; quem traduz para o fuso local é o cliente. */
    @Test
    fun `the utc day is translated to the local day`() {
        val trend = buildTeamUsageTrend(
            // Meia-noite UTC do dia 11 é 21h BRT do dia 10.
            rows = listOf(row("device-1", "2026-08-11T00:00:00Z", inputTokens = 1_000_000)),
            members = MEMBERS,
            days = DAYS,
            timeZone = BRT
        )

        val member = trend.members.first { it.deviceId == "device-1" }
        val active = member.points.filter { point -> point.turnCount > 0 }
        assertEquals(listOf(LocalDate(2026, 8, 10)), active.map { point -> point.date })
    }

    @Test
    fun `cost comes from the model pricing table`() {
        val pricing = ModelPricingTable.forModel(OPUS)!!
        val trend = buildTeamUsageTrend(
            rows = listOf(row("device-1", "2026-08-10T12:00:00Z", inputTokens = 1_000_000, outputTokens = 500_000)),
            members = MEMBERS,
            days = DAYS,
            timeZone = BRT
        )

        val expected = pricing.costMicros(inputTokens = 1_000_000, outputTokens = 500_000)
        assertEquals(expected, trend.totalCostMicros)
        assertEquals(expected, trend.peakDailyCostMicros)
    }

    @Test
    fun `an unpriced model adds no cost and is declared`() {
        val trend = buildTeamUsageTrend(
            rows = listOf(
                row("device-1", "2026-08-10T12:00:00Z", model = "modelo-inexistente", turnCount = 4)
            ),
            members = MEMBERS,
            days = DAYS,
            timeZone = BRT
        )

        val point = trend.members
            .first { it.deviceId == "device-1" }
            .points
            .first { it.date == LocalDate(2026, 8, 10) }

        assertEquals(0L, point.costMicros)
        assertEquals(4, point.unpricedTurnCount)
        assertFalse(point.isCostComplete)
    }

    /** Máquina fora da lista de integrantes não pode sumir: o consumo é real. */
    @Test
    fun `a device missing from the member list still appears`() {
        val trend = buildTeamUsageTrend(
            rows = listOf(row("device-9", "2026-08-10T12:00:00Z", inputTokens = 1_000_000)),
            members = MEMBERS,
            days = DAYS,
            timeZone = BRT
        )

        val extra = trend.members.first { it.deviceId == "device-9" }
        assertEquals("device-9", extra.alias)
        assertTrue(extra.hasActivity)
    }

    @Test
    fun `equal readings produce equal trends`() {
        val rows = listOf(
            row("device-1", "2026-08-09T12:00:00Z", inputTokens = 2_000_000),
            row("device-2", "2026-08-10T12:00:00Z", inputTokens = 1_000_000),
            row("device-1", "2026-08-11T12:00:00Z", inputTokens = 500_000)
        )

        assertEquals(
            buildTeamUsageTrend(rows, MEMBERS, DAYS, BRT),
            buildTeamUsageTrend(rows.reversed(), MEMBERS, DAYS, BRT)
        )
    }

    @Test
    fun `members are ranked by total cost`() {
        val trend = buildTeamUsageTrend(
            rows = listOf(
                row("device-1", "2026-08-10T12:00:00Z", inputTokens = 1_000_000),
                row("device-2", "2026-08-10T12:00:00Z", inputTokens = 3_000_000)
            ),
            members = MEMBERS,
            days = DAYS,
            timeZone = BRT
        )

        assertEquals(listOf("maria", "edilson"), trend.members.map { member -> member.alias })
    }

    @Test
    fun `a window with no consumption is empty`() {
        val trend = buildTeamUsageTrend(emptyList(), MEMBERS, DAYS, BRT)

        assertTrue(trend.isEmpty)
        assertEquals(0L, trend.peakDailyCostMicros)
    }
}

private fun row(
    deviceId: String,
    dayStart: String,
    model: String? = OPUS,
    turnCount: Int = 1,
    inputTokens: Long = 0L,
    outputTokens: Long = 0L
): TeamTrendRow {
    return TeamTrendRow(
        deviceId = deviceId,
        dayStartMillis = Instant.parse(dayStart).toEpochMilliseconds(),
        model = model,
        turnCount = turnCount,
        inputTokens = inputTokens,
        outputTokens = outputTokens
    )
}
