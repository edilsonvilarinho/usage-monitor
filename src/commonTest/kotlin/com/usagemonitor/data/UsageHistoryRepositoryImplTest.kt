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
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
                        label = "Codex 5h",
                        used = 10,
                        total = 100,
                        periodEndAt = now,
                        unit = UsageUnit.REQUESTS
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
        periodEndAt: String = "2026-04-28T20:00:00Z"
    ): UsageSnapshotRecord {
        return UsageSnapshotRecord(
            source = source,
            quotaLabel = label,
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.REQUESTS,
            used = used,
            total = total,
            rawUsed = used,
            rawTotal = total,
            periodEndAt = Instant.parse(periodEndAt),
            capturedAt = Instant.parse(capturedAt)
        )
    }
}
