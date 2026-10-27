package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamTrendRow
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import com.usagemonitor.domain.repository.TeamUsageTrendData
import com.usagemonitor.domain.usecase.DEFAULT_TEAM_TREND_DAYS
import com.usagemonitor.domain.usecase.GetTeamUsageTrendUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private class TrendRepository(private val result: Result<TeamUsageTrendData?>) : TeamUsageRepository {
    var requestedDays: Int? = null

    override suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?> {
        requestedDays = days
        return result
    }

    override suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt> = error("fora do teste")
    override suspend fun fetch(accountKey: String, cutoffMillis: Long?): Result<TeamUsageSnapshot> = error("fora do teste")
    override suspend fun fetchSessionDetail(accountKey: String, deviceId: String, sessionId: String): Result<CliSessionDetail?> =
        error("fora do teste")
    override suspend fun touchPresence(accountKey: String, member: TeamMemberIdentity): Result<TeamPresenceReceipt> =
        error("fora do teste")
    override suspend fun checkConnection(): Result<Unit> = error("fora do teste")
}

class GetTeamUsageTrendUseCaseTest {

    /** 02:00 UTC de 28/10 ainda é 27/10 em São Paulo: o eixo termina no dia de BRT. */
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-10-28T02:00:00Z")
    }

    @Test
    fun `day axis ends today in BRT and includes days without activity`() = runTest {
        val repository = TrendRepository(
            Result.success(
                TeamUsageTrendData(
                    members = listOf(TeamMemberIdentity(deviceId = "d1", alias = "ana")),
                    rows = listOf(
                        TeamTrendRow(
                            deviceId = "d1",
                            // Meia-noite de 26/10 em BRT.
                            dayStartMillis = Instant.parse("2026-10-26T03:00:00Z").toEpochMilliseconds(),
                            model = "claude-sonnet-5",
                            turnCount = 2,
                            inputTokens = 1_000L
                        )
                    )
                )
            )
        )

        val trend = GetTeamUsageTrendUseCase(repository, clock, TimeZone.of("America/Sao_Paulo"))("conta-1", days = 3)
            .getOrThrow()!!

        assertEquals(3, repository.requestedDays)
        assertEquals(
            listOf(LocalDate(2026, 10, 25), LocalDate(2026, 10, 26), LocalDate(2026, 10, 27)),
            trend.days
        )
        val points = trend.members.single().points
        assertEquals(listOf(0, 2, 0), points.map { it.turnCount })
        assertTrue(points[1].costMicros > 0L)
    }

    @Test
    fun `server without the route yields null, not an error`() = runTest {
        val repository = TrendRepository(Result.success(null))

        val result = GetTeamUsageTrendUseCase(repository, clock)("conta-1")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
        assertEquals(DEFAULT_TEAM_TREND_DAYS, repository.requestedDays)
    }

    @Test
    fun `read failure stays a failure`() = runTest {
        val repository = TrendRepository(Result.failure(IllegalStateException("offline")))

        val result = GetTeamUsageTrendUseCase(repository, clock)("conta-1")

        assertEquals("offline", result.exceptionOrNull()?.message)
    }
}
