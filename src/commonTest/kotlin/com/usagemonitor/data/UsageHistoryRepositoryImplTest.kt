package com.usagemonitor.data

import com.usagemonitor.data.datasource.UsageHistoryDataSource
import com.usagemonitor.data.dto.UsageSnapshotRecord
import com.usagemonitor.data.repository.UsageHistoryRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UsageHistoryRepositoryImplTest {

    private val now = Instant.parse("2026-04-28T18:00:00Z")

    @Test
    fun `recordSnapshot delegates to datasource`() = kotlinx.coroutines.test.runTest {
        var inserted = false
        val dataSource = object : UsageHistoryDataSource {
            override suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
                inserted = true
            }

            override suspend fun readSnapshots(source: ApiSource, since: Instant): List<UsageSnapshotRecord> {
                return emptyList()
            }
        }

        val repository = UsageHistoryRepositoryImpl(dataSource)
        repository.recordSnapshot(
            ApiUsageStats(
                source = ApiSource.CODEX,
                apiName = "Codex",
                quotas = listOf(
                    QuotaInfo(
                        label = "Codex atual",
                        used = 10,
                        total = 100,
                        periodEndAt = now,
                        periodType = PeriodType.REPORTED,
                        unit = UsageUnit.PERCENTAGE
                    )
                )
            ),
            now
        )

        assertEquals(true, inserted)
    }

    @Test
    fun `forecast returns estimated exhaustion for steady growth`() = kotlinx.coroutines.test.runTest {
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(steadyGrowthRecords()))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        val series = report.series.single()
        assertEquals(400L, series.deltaDisplayUsed)
        assertIs<UsageForecast.EstimatedExhaustionAt>(series.forecast)
    }

    @Test
    fun `forecast ignores points before reset`() = kotlinx.coroutines.test.runTest {
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(resetRecords()))

        val report = repository.getHistoryReport(ApiSource.MINIMAX, HistoryRange.LAST_24_HOURS, now)

        val series = report.series.single()
        assertEquals(380L, series.deltaDisplayUsed)
        assertIs<UsageForecast.ResetsBeforeExhaustion>(series.forecast)
    }

    @Test
    fun `forecast returns insufficient data when segment is too short`() = kotlinx.coroutines.test.runTest {
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(shortRecords()))

        val report = repository.getHistoryReport(ApiSource.ANTHROPIC, HistoryRange.LAST_24_HOURS, now)

        assertIs<UsageForecast.InsufficientData>(report.series.single().forecast)
    }

    @Test
    fun `report calculates average consumption per hour`() = kotlinx.coroutines.test.runTest {
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(steadyGrowthRecords()))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertEquals(200.0, report.series.single().averageDisplayConsumptionPerHour)
    }

    @Test
    fun `forecast returns InsufficientData when fewer than 3 points`() = kotlinx.coroutines.test.runTest {
        val twoPoints = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 100, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 200, 1000)
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(twoPoints))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertIs<UsageForecast.InsufficientData>(report.series.single().forecast)
    }

    @Test
    fun `forecast returns InsufficientData when window under 30 minutes`() = kotlinx.coroutines.test.runTest {
        val tightWindow = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:50:00Z", 100, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:55:00Z", 110, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T18:00:00Z", 120, 1000)
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(tightWindow))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertIs<UsageForecast.InsufficientData>(report.series.single().forecast)
    }

    @Test
    fun `forecast returns NoGrowth when delta is zero across all points`() = kotlinx.coroutines.test.runTest {
        val flatRecords = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 500, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 500, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 500, 1000)
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(flatRecords))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertIs<UsageForecast.NoGrowth>(report.series.single().forecast)
    }

    @Test
    fun `forecast returns ResetsBeforeExhaustion when projection past period end`() = kotlinx.coroutines.test.runTest {
        // Crescimento lento (10/h) + período termina em ~3h: total 1000, used 100 -> 900 restantes -> 90h até esgotar.
        val slowGrowth = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 100, 1000, "2026-04-28T20:00:00Z"),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 110, 1000, "2026-04-28T20:00:00Z"),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 120, 1000, "2026-04-28T20:00:00Z")
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(slowGrowth))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertIs<UsageForecast.ResetsBeforeExhaustion>(report.series.single().forecast)
    }

    @Test
    fun `risk summary grades WILL_EXCEED vs AT_RISK using time to exhaustion ratio`() = kotlinx.coroutines.test.runTest {
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(steadyGrowthRecords()))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        // Último ponto 17:00, exaustão projetada 19:00, período termina 20:00.
        // Tempo até estourar (2h) é mais da metade do tempo até o reset (3h) -> AT_RISK.
        val risk = report.series.single().riskSummary
        assertEquals(UsageRiskLevel.AT_RISK, risk?.level)
        assertEquals(Instant.parse("2026-04-28T19:00:00Z"), risk?.estimatedExhaustionAt)
    }

    @Test
    fun `risk summary is ON_TRACK when forecast resets before exhaustion`() = kotlinx.coroutines.test.runTest {
        val slowGrowth = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 100, 1000, "2026-04-28T20:00:00Z"),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 110, 1000, "2026-04-28T20:00:00Z"),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 120, 1000, "2026-04-28T20:00:00Z")
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(slowGrowth))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        val risk = report.series.single().riskSummary
        assertEquals(UsageRiskLevel.ON_TRACK, risk?.level)
        assertNull(risk?.estimatedExhaustionAt)
    }

    @Test
    fun `risk summary is null when there is no growth`() = kotlinx.coroutines.test.runTest {
        val flatRecords = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 500, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 500, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 500, 1000)
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(flatRecords))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertNull(report.series.single().riskSummary)
    }

    @Test
    fun `risk summary is null when forecast has insufficient data`() = kotlinx.coroutines.test.runTest {
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(shortRecords()))

        val report = repository.getHistoryReport(ApiSource.ANTHROPIC, HistoryRange.LAST_24_HOURS, now)

        assertNull(report.series.single().riskSummary)
    }

    @Test
    fun `forecast survives reset by using only post-reset segment`() = kotlinx.coroutines.test.runTest {
        // Antes do reset: crescimento alto (800->900). Depois: ritmo estável e pequeno.
        // Se currentSegment não filtrasse o reset, a média seria distorcida.
        val records = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T13:00:00Z", 800, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T14:00:00Z", 900, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 50, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 100, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 150, 1000)
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(records))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        // Pós-reset: ritmo de 50/h, restam 850, projeção bem além do periodEnd default (20:00 = 3h).
        assertIs<UsageForecast.ResetsBeforeExhaustion>(report.series.single().forecast)
    }

    @Test
    fun `currentSegment ignores millisecond jitter in periodEndAt and keeps the full history`() = kotlinx.coroutines.test.runTest {
        // used sobe de forma constante (50/h) e periodEndAt oscila por jitter de servidor
        // (~1s), sem reset real. Se cada jitter cortasse o segmento, a média por hora
        // observada cairia pra quase zero (só o último ponto entraria no cálculo).
        val records = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T13:00:00Z", 100, 1000, "2026-04-28T20:00:00.100Z"),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T14:00:00Z", 150, 1000, "2026-04-28T19:59:59.700Z"),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 200, 1000, "2026-04-28T20:00:00.900Z")
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(records))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        val series = report.series.single()
        assertEquals(50.0, series.averageDisplayConsumptionPerHour, 0.001)
    }

    @Test
    fun `TOTAL range returns all snapshots including records older than thirty days`() = kotlinx.coroutines.test.runTest {
        val records = listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-02-15T10:00:00Z", 20, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-03-10T10:00:00Z", 40, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 80, 1000),
            record("Codex 7d", ApiSource.CODEX, "2026-02-20T10:00:00Z", 100, 5000, "2026-02-27T10:00:00Z")
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(records))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.TOTAL, now)

        assertEquals(HistoryRange.TOTAL, report.range)
        assertEquals(2, report.series.size)
        assertEquals(Instant.parse("2026-04-28T17:00:00Z"), report.lastUpdatedAt)
        assertEquals(
            listOf(
                Instant.parse("2026-02-15T10:00:00Z"),
                Instant.parse("2026-03-10T10:00:00Z"),
                Instant.parse("2026-04-28T17:00:00Z")
            ),
            report.series.first { it.quotaLabel == "Codex 5h" }.points.map { it.capturedAt }
        )
    }

    @Test
    fun `reported Codex series stays separate and keeps forecast inference disabled`() = kotlinx.coroutines.test.runTest {
        val records = listOf(
            record(
                label = "Codex 5h",
                source = ApiSource.CODEX,
                capturedAt = "2026-04-28T16:00:00Z",
                used = 5,
                total = 100,
                periodType = PeriodType.INTERVAL,
                unit = UsageUnit.PERCENTAGE
            ),
            record(
                label = "Codex atual",
                source = ApiSource.CODEX,
                capturedAt = "2026-04-28T16:00:00Z",
                used = 5,
                total = 100,
                periodType = PeriodType.REPORTED,
                unit = UsageUnit.PERCENTAGE
            ),
            record(
                label = "Codex atual",
                source = ApiSource.CODEX,
                capturedAt = "2026-04-28T17:00:00Z",
                used = 16,
                total = 100,
                periodType = PeriodType.REPORTED,
                unit = UsageUnit.PERCENTAGE
            )
        )
        val repository = UsageHistoryRepositoryImpl(FakeHistoryDataSource(records))

        val report = repository.getHistoryReport(ApiSource.CODEX, HistoryRange.LAST_24_HOURS, now)

        assertEquals(listOf("Codex 5h", "Codex atual"), report.series.map { it.quotaLabel })
        val reportedSeries = report.series.first { it.quotaLabel == "Codex atual" }
        assertEquals(PeriodType.REPORTED, reportedSeries.periodType)
        assertEquals(0.0, reportedSeries.averageDisplayConsumptionPerHour)
        assertIs<UsageForecast.InsufficientData>(reportedSeries.forecast)
    }

    @Test
    fun `account scoped report never mixes points from another workspace`() = kotlinx.coroutines.test.runTest {
        val accountA = account("same-user", "workspace-a")
        val accountB = account("same-user", "workspace-b")
        val recordsByAccount = mapOf(
            accountA.key to listOf(
                record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 100, 1000),
                record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 200, 1000),
                record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 300, 1000)
            ),
            accountB.key to listOf(
                record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 700, 1000),
                record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 800, 1000),
                record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 900, 1000)
            )
        )
        val dataSource = object : UsageHistoryDataSource {
            override suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun readSnapshots(source: ApiSource, since: Instant): List<UsageSnapshotRecord> {
                return recordsByAccount.values.flatten()
            }

            override suspend fun readSnapshots(
                source: ApiSource,
                accountKey: UsageAccountKey?,
                since: Instant
            ): List<UsageSnapshotRecord> {
                return recordsByAccount[accountKey].orEmpty()
            }

            override suspend fun readAccounts(source: ApiSource): List<UsageAccountContext> {
                return listOf(accountA, accountB)
            }
        }
        val repository = UsageHistoryRepositoryImpl(dataSource)

        val report = repository.getHistoryReport(
            ApiSource.CODEX,
            accountA.key,
            HistoryRange.LAST_24_HOURS,
            now
        )

        assertEquals(accountA, report.accountContext)
        assertEquals(listOf(100L, 200L, 300L), report.series.single().points.map { it.displayUsed })
        assertEquals(200L, report.series.single().deltaDisplayUsed)
    }

    private class FakeHistoryDataSource(
        private val records: List<UsageSnapshotRecord>
    ) : UsageHistoryDataSource {
        override suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

        override suspend fun readSnapshots(source: ApiSource, since: Instant): List<UsageSnapshotRecord> {
            return records.filter { it.source == source && it.capturedAt >= since }
        }
    }

    private fun steadyGrowthRecords(): List<UsageSnapshotRecord> {
        return listOf(
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T15:00:00Z", 200, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T16:00:00Z", 400, 1000),
            record("Codex 5h", ApiSource.CODEX, "2026-04-28T17:00:00Z", 600, 1000)
        )
    }

    private fun resetRecords(): List<UsageSnapshotRecord> {
        return listOf(
            record("MiniMax-M*", ApiSource.MINIMAX, "2026-04-28T13:00:00Z", 700, 1000, "2026-04-28T18:30:00Z"),
            record("MiniMax-M*", ApiSource.MINIMAX, "2026-04-28T14:00:00Z", 900, 1000, "2026-04-28T18:30:00Z"),
            record("MiniMax-M*", ApiSource.MINIMAX, "2026-04-28T15:00:00Z", 30, 1000, "2026-04-28T23:30:00Z"),
            record("MiniMax-M*", ApiSource.MINIMAX, "2026-04-28T16:00:00Z", 120, 1000, "2026-04-28T23:30:00Z"),
            record("MiniMax-M*", ApiSource.MINIMAX, "2026-04-28T17:00:00Z", 210, 1000, "2026-04-28T23:30:00Z")
        )
    }

    private fun shortRecords(): List<UsageSnapshotRecord> {
        return listOf(
            record("Claude 5h", ApiSource.ANTHROPIC, "2026-04-28T17:40:00Z", 100, 1000),
            record("Claude 5h", ApiSource.ANTHROPIC, "2026-04-28T17:50:00Z", 150, 1000)
        )
    }

    private fun record(
        label: String,
        source: ApiSource,
        capturedAt: String,
        used: Long,
        total: Long,
        periodEndAt: String = "2026-04-28T20:00:00Z",
        periodType: PeriodType = PeriodType.INTERVAL,
        unit: UsageUnit = UsageUnit.REQUESTS
    ): UsageSnapshotRecord {
        return UsageSnapshotRecord(
            source = source,
            quotaLabel = label,
            periodType = periodType,
            unit = unit,
            used = used,
            total = total,
            rawUsed = used,
            rawTotal = total,
            periodEndAt = Instant.parse(periodEndAt),
            capturedAt = Instant.parse(capturedAt)
        )
    }

    private fun account(userId: String, workspaceId: String): UsageAccountContext {
        return UsageAccountContext(
            key = UsageAccountKey(
                source = ApiSource.CODEX,
                providerAccountId = userId,
                workspaceId = workspaceId
            ),
            email = "same@example.com",
            workspaceName = workspaceId
        )
    }
}
