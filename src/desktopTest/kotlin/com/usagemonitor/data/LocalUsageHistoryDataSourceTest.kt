package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalUsageHistoryDataSourceTest {

    @Test
    fun `database is created automatically and snapshots can be read`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val databaseFile = File(tempDir, "history.db")
        val dataSource = LocalUsageHistoryDataSource(databaseFile)
        val capturedAt = Instant.parse("2026-04-28T18:00:00Z")

        dataSource.insertSnapshot(sampleStats(), capturedAt)

        assertTrue(databaseFile.exists())

        val records = dataSource.readSnapshots(
            source = ApiSource.CODEX,
            since = Instant.parse("2026-04-27T18:00:00Z")
        )

        assertEquals(2, records.size)
        assertEquals(
            setOf("Codex atual", "Codex 7d"),
            records.map { it.quotaLabel }.toSet()
        )
        assertEquals(PeriodType.REPORTED, records.first { it.quotaLabel == "Codex atual" }.periodType)
        tempDir.deleteRecursively()
    }

    @Test
    fun `older snapshots remain available after newer inserts`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val databaseFile = File(tempDir, "history.db")
        val dataSource = LocalUsageHistoryDataSource(databaseFile)

        dataSource.insertSnapshot(sampleStats(), Instant.parse("2026-03-01T10:00:00Z"))
        dataSource.insertSnapshot(sampleStats(), Instant.parse("2026-04-28T18:00:00Z"))

        val records = dataSource.readSnapshots(
            source = ApiSource.CODEX,
            since = Instant.parse("2026-02-20T00:00:00Z")
        )

        assertEquals(4, records.size)

        dataSource.insertSnapshot(sampleStats(), Instant.parse("2026-05-05T18:00:00Z"))

        val afterPrune = dataSource.readSnapshots(
            source = ApiSource.CODEX,
            since = Instant.parse("2026-02-20T00:00:00Z")
        )

        assertEquals(6, afterPrune.size)
        assertTrue(afterPrune.any { it.capturedAt == Instant.parse("2026-03-01T10:00:00Z") })
        tempDir.deleteRecursively()
    }

    @Test
    fun `snapshots persist all quotas regardless of hasKnownResetAt`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val databaseFile = File(tempDir, "history.db")
        val dataSource = LocalUsageHistoryDataSource(databaseFile)

        dataSource.insertSnapshot(
            ApiUsageStats(
                source = ApiSource.ANTHROPIC,
                apiName = "Anthropic",
                quotas = listOf(
                    QuotaInfo(
                        label = "Claude 5h",
                        used = 0L,
                        total = 100L,
                        periodEndAt = Instant.parse("2100-01-01T00:00:00Z"),
                        hasKnownResetAt = false,
                        periodType = PeriodType.INTERVAL,
                        unit = UsageUnit.TOKENS,
                        rawUsed = 0L,
                        rawTotal = 4500L
                    ),
                    QuotaInfo(
                        label = "Claude 7d",
                        used = 98L,
                        total = 100L,
                        periodEndAt = Instant.parse("2026-05-01T12:00:00Z"),
                        periodType = PeriodType.WEEKLY,
                        unit = UsageUnit.TOKENS,
                        rawUsed = 44100L,
                        rawTotal = 45000L
                    )
                )
            ),
            Instant.parse("2026-04-29T12:00:00Z")
        )

        val records = dataSource.readSnapshots(
            source = ApiSource.ANTHROPIC,
            since = Instant.parse("2026-04-29T00:00:00Z")
        )

        assertEquals(2, records.size)
        assertEquals(setOf("Claude 5h", "Claude 7d"), records.map { it.quotaLabel }.toSet())
        tempDir.deleteRecursively()
    }

    private fun sampleStats(): ApiUsageStats {
        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = listOf(
                QuotaInfo(
                    label = "Codex atual",
                    used = 45L,
                    total = 100L,
                    periodEndAt = Instant.parse("2026-04-28T20:00:00Z"),
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.PERCENTAGE,
                    rawUsed = 0L,
                    rawTotal = 0L
                ),
                QuotaInfo(
                    label = "Codex 7d",
                    used = 20L,
                    total = 100L,
                    periodEndAt = Instant.parse("2026-05-02T20:00:00Z"),
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE,
                    rawUsed = 0L,
                    rawTotal = 0L
                )
            )
        )
    }
}
