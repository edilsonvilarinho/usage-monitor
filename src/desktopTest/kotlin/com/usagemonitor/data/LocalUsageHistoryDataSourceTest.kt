package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File
import java.sql.DriverManager
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
                accountContext = account(ApiSource.ANTHROPIC, "anthropic-a", "org-a", "a@example.com"),
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

    @Test
    fun `migrates v2 database and assigns legacy snapshots to first active account`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val databaseFile = File(tempDir, "history.db")
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE usage_snapshots (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        source TEXT NOT NULL,
                        quota_label TEXT NOT NULL,
                        period_type TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        used INTEGER NOT NULL,
                        total INTEGER NOT NULL,
                        raw_used INTEGER NOT NULL,
                        raw_total INTEGER NOT NULL,
                        period_end_at INTEGER NOT NULL,
                        captured_at INTEGER NOT NULL
                    );
                    """.trimIndent()
                )
                statement.execute("PRAGMA user_version = 2;")
                statement.execute(
                    """
                    INSERT INTO usage_snapshots (
                        source, quota_label, period_type, unit, used, total,
                        raw_used, raw_total, period_end_at, captured_at
                    ) VALUES (
                        'CODEX', 'Codex atual', 'REPORTED', 'PERCENTAGE', 5, 100,
                        0, 0, 1777410000000, 1777399200000
                    );
                    """.trimIndent()
                )
            }
        }
        val dataSource = LocalUsageHistoryDataSource(databaseFile)
        val activeAccount = sampleStats().accountContext!!

        dataSource.insertSnapshot(sampleStats(), Instant.parse("2026-04-29T12:00:00Z"))

        val records = dataSource.readSnapshots(
            source = ApiSource.CODEX,
            accountKey = activeAccount.key,
            since = Instant.parse("2026-04-01T00:00:00Z")
        )
        assertEquals(3, records.size)
        assertEquals(listOf(activeAccount), dataSource.readAccounts(ApiSource.CODEX))
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version;").use { resultSet ->
                    assertEquals(3, resultSet.getInt(1))
                }
                statement.executeQuery(
                    "SELECT COUNT(*) FROM usage_snapshots WHERE source = 'CODEX' AND account_id IS NULL;"
                ).use { resultSet ->
                    assertEquals(0, resultSet.getInt(1))
                }
            }
        }
        dataSource.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `keeps same email isolated across different workspaces`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val databaseFile = File(tempDir, "history.db")
        val dataSource = LocalUsageHistoryDataSource(databaseFile)
        val accountA = account(ApiSource.CODEX, "same-user", "workspace-a", "same@example.com")
        val accountB = account(ApiSource.CODEX, "same-user", "workspace-b", "same@example.com")
        val statsA = sampleStats().copy(accountContext = accountA)
        val statsB = sampleStats().copy(accountContext = accountB)

        dataSource.insertSnapshot(statsA, Instant.parse("2026-04-28T18:00:00Z"))
        dataSource.insertSnapshot(statsB, Instant.parse("2026-04-29T18:00:00Z"))

        val recordsA = dataSource.readSnapshots(
            ApiSource.CODEX,
            accountA.key,
            Instant.parse("2026-04-01T00:00:00Z")
        )
        val recordsB = dataSource.readSnapshots(
            ApiSource.CODEX,
            accountB.key,
            Instant.parse("2026-04-01T00:00:00Z")
        )
        assertEquals(2, recordsA.size)
        assertEquals(2, recordsB.size)
        assertEquals(listOf(accountB, accountA), dataSource.readAccounts(ApiSource.CODEX))
        dataSource.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun `rejects authenticated source snapshot without account identity`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val dataSource = LocalUsageHistoryDataSource(File(tempDir, "history.db"))
        val statsWithoutAccount = sampleStats().copy(accountContext = null)

        val error = kotlin.test.assertFailsWith<IllegalStateException> {
            dataSource.insertSnapshot(statsWithoutAccount, Instant.parse("2026-04-29T18:00:00Z"))
        }

        assertTrue(error.message.orEmpty().contains("não informou a conta"))
        dataSource.close()
        tempDir.deleteRecursively()
    }

    private fun sampleStats(): ApiUsageStats {
        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            accountContext = account(ApiSource.CODEX, "codex-a", "workspace-a", "a@example.com"),
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

    private fun account(
        source: ApiSource,
        providerAccountId: String,
        workspaceId: String,
        email: String
    ): UsageAccountContext {
        return UsageAccountContext(
            key = UsageAccountKey(
                source = source,
                providerAccountId = providerAccountId,
                workspaceId = workspaceId
            ),
            email = email,
            workspaceName = workspaceId
        )
    }
}
