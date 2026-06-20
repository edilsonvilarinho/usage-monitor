package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.UsageSnapshotRecord
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import java.io.File
import java.sql.Connection

class LocalUsageHistoryDataSource(
    private val databaseFile: File = defaultDatabaseFile(),
    private val retentionPeriod: Duration = 90.days
) : UsageHistoryDataSource, AutoCloseable {

    private val connectionManager = SqliteConnectionManager(
        databaseFile = databaseFile,
        onOpen = { connection -> initializeSchema(connection) }
    )

    override suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
        withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.autoCommit = false
                try {
                    connection.prepareStatement(INSERT_SQL).use { statement ->
                        stats.quotas
                            .forEach { quota ->
                            statement.setString(1, stats.source.name)
                            statement.setString(2, quota.label)
                            statement.setString(3, quota.periodType.name)
                            statement.setString(4, quota.unit.name)
                            statement.setLong(5, quota.used)
                            statement.setLong(6, quota.total)
                            statement.setLong(7, quota.rawUsed)
                            statement.setLong(8, quota.rawTotal)
                            statement.setLong(9, quota.periodEndAt.toEpochMilliseconds())
                            statement.setLong(10, capturedAt.toEpochMilliseconds())
                            statement.addBatch()
                        }
                        statement.executeBatch()
                    }
                    pruneExpiredSnapshots(connection, capturedAt)

                    connection.commit()
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                } finally {
                    connection.autoCommit = true
                }
            }
        }
    }

    override suspend fun readSnapshots(source: ApiSource, since: Instant): List<UsageSnapshotRecord> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_SQL).use { statement ->
                    statement.setString(1, source.name)
                    statement.setLong(2, since.toEpochMilliseconds())
                    val rows = mutableListOf<UsageSnapshotRecord>()
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            rows += UsageSnapshotRecord(
                                source = ApiSource.valueOf(resultSet.getString("source")),
                                quotaLabel = resultSet.getString("quota_label"),
                                periodType = PeriodType.valueOf(resultSet.getString("period_type")),
                                unit = UsageUnit.valueOf(resultSet.getString("unit")),
                                used = resultSet.getLong("used"),
                                total = resultSet.getLong("total"),
                                rawUsed = resultSet.getLong("raw_used"),
                                rawTotal = resultSet.getLong("raw_total"),
                                periodEndAt = Instant.fromEpochMilliseconds(resultSet.getLong("period_end_at")),
                                capturedAt = Instant.fromEpochMilliseconds(resultSet.getLong("captured_at"))
                            )
                        }
                    }
                    rows
                }
            }
        }
    }

    override fun close() {
        connectionManager.close()
    }

    private fun initializeSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode = WAL;")
            statement.execute("PRAGMA synchronous = NORMAL;")
            statement.execute("PRAGMA user_version = 2;")
            statement.execute(CREATE_TABLE_SQL)
            statement.execute(CREATE_INDEX_BY_SOURCE_SQL)
            statement.execute(CREATE_INDEX_BY_SERIES_SQL)
        }
    }

    private fun pruneExpiredSnapshots(connection: Connection, capturedAt: Instant) {
        val cutoff = capturedAt.minus(retentionPeriod)
        connection.prepareStatement(DELETE_EXPIRED_SQL).use { statement ->
            statement.setLong(1, cutoff.toEpochMilliseconds())
            statement.executeUpdate()
        }
    }

    private companion object {
        const val CREATE_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS usage_snapshots (
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
        """

        const val CREATE_INDEX_BY_SOURCE_SQL = """
            CREATE INDEX IF NOT EXISTS idx_usage_snapshots_source_captured
            ON usage_snapshots(source, captured_at);
        """

        const val CREATE_INDEX_BY_SERIES_SQL = """
            CREATE INDEX IF NOT EXISTS idx_usage_snapshots_series
            ON usage_snapshots(source, quota_label, period_end_at, captured_at);
        """

        const val INSERT_SQL = """
            INSERT INTO usage_snapshots (
                source,
                quota_label,
                period_type,
                unit,
                used,
                total,
                raw_used,
                raw_total,
                period_end_at,
                captured_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        const val SELECT_SQL = """
            SELECT
                source,
                quota_label,
                period_type,
                unit,
                used,
                total,
                raw_used,
                raw_total,
                period_end_at,
                captured_at
            FROM usage_snapshots
            WHERE source = ? AND captured_at >= ?
            ORDER BY quota_label ASC, captured_at ASC;
        """

        const val DELETE_EXPIRED_SQL = """
            DELETE FROM usage_snapshots
            WHERE captured_at < ?;
        """

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".usage-monitor/usage-history.db")
        }
    }
}
