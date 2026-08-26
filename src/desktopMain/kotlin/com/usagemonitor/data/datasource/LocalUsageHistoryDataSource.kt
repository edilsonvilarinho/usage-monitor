package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.UsageSnapshotRecord
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.requiresUsageAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.minus
import java.io.File
import java.sql.Connection
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class LocalUsageHistoryDataSource(
    private val databaseFile: File = defaultDatabaseFile(),
    private val retentionPeriod: Duration = 90.days
) : UsageHistoryDataSource, AutoCloseable {

    private val connectionManager = SqliteConnectionManager(
        databaseFile = databaseFile,
        onOpen = { connection -> initializeSchema(connection) }
    )

    override suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
        val accountContext = stats.accountContext
        if (stats.source.requiresUsageAccount && accountContext == null) {
            throw IllegalStateException("A fonte ${stats.source.name} não informou a conta da coleta.")
        }
        if (accountContext != null && accountContext.key.source != stats.source) {
            throw IllegalStateException("A conta informada não pertence à fonte ${stats.source.name}.")
        }

        withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.autoCommit = false
                try {
                    val accountId = if (accountContext == null) {
                        null
                    } else {
                        val resolvedAccountId = upsertAccount(connection, accountContext, capturedAt)
                        claimLegacySnapshots(connection, stats.source, resolvedAccountId, capturedAt)
                        resolvedAccountId
                    }

                    connection.prepareStatement(INSERT_SQL).use { statement ->
                        for (quota in stats.quotas) {
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
                            if (accountId == null) {
                                statement.setNull(11, java.sql.Types.BIGINT)
                            } else {
                                statement.setLong(11, accountId)
                            }
                            statement.setInt(12, if (quota.hasKnownResetAt) 1 else 0)
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
        if (source.requiresUsageAccount) {
            val latestAccount = readAccounts(source).firstOrNull() ?: return emptyList()
            return readSnapshots(source, latestAccount.key, since)
        }

        return readSnapshotRows(SELECT_BY_SOURCE_SQL) { statement ->
            statement.setString(1, source.name)
            statement.setLong(2, since.toEpochMilliseconds())
        }
    }

    override suspend fun readSnapshots(
        source: ApiSource,
        accountKey: UsageAccountKey?,
        since: Instant
    ): List<UsageSnapshotRecord> {
        if (accountKey == null) {
            return if (source.requiresUsageAccount) emptyList() else readSnapshots(source, since)
        }
        require(accountKey.source == source) { "A conta selecionada não pertence à fonte solicitada." }

        return readSnapshotRows(SELECT_BY_ACCOUNT_SQL) { statement ->
            statement.setString(1, source.name)
            statement.setString(2, accountKey.providerAccountId)
            statement.setString(3, normalizeWorkspaceId(accountKey.workspaceId))
            statement.setLong(4, since.toEpochMilliseconds())
        }
    }

    override suspend fun readAccounts(source: ApiSource): List<UsageAccountContext> {
        if (!source.requiresUsageAccount) {
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_ACCOUNTS_SQL).use { statement ->
                    statement.setString(1, source.name)
                    val accounts = mutableListOf<UsageAccountContext>()
                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            val workspaceId = resultSet.getString("workspace_id").ifBlank { null }
                            val workspaceName = resultSet.getString("workspace_name")?.ifBlank { null }
                            accounts += UsageAccountContext(
                                key = UsageAccountKey(
                                    source = source,
                                    providerAccountId = resultSet.getString("provider_account_id"),
                                    workspaceId = workspaceId
                                ),
                                email = resultSet.getString("email"),
                                workspaceName = workspaceName
                            )
                        }
                    }
                    accounts
                }
            }
        }
    }

    override fun close() {
        connectionManager.close()
    }

    private suspend fun readSnapshotRows(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit
    ): List<UsageSnapshotRecord> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(sql).use { statement ->
                    bind(statement)
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
                                capturedAt = Instant.fromEpochMilliseconds(resultSet.getLong("captured_at")),
                                hasKnownResetAt = resultSet.getInt("has_known_reset_at") != 0
                            )
                        }
                    }
                    rows
                }
            }
        }
    }

    private fun initializeSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode = WAL;")
            statement.execute("PRAGMA synchronous = NORMAL;")
            statement.execute("PRAGMA foreign_keys = ON;")
            // O índice de sessões CLI escreve no mesmo arquivo por outra conexão,
            // em transações longas. Sem timeout, um snapshot que caia no meio de
            // uma indexação falha na hora em vez de esperar o writer lock.
            statement.execute("PRAGMA busy_timeout = 5000;")
            statement.execute(CREATE_ACCOUNTS_TABLE_SQL)
            statement.execute(CREATE_SNAPSHOTS_TABLE_SQL)
        }

        if (!hasColumn(connection, "usage_snapshots", "account_id")) {
            connection.createStatement().use { statement ->
                statement.execute(ADD_ACCOUNT_ID_COLUMN_SQL)
            }
        }

        if (!hasColumn(connection, "usage_snapshots", "has_known_reset_at")) {
            connection.createStatement().use { statement ->
                statement.execute(ADD_HAS_KNOWN_RESET_COLUMN_SQL)
            }
        }

        connection.createStatement().use { statement ->
            statement.execute(CREATE_LEGACY_ASSIGNMENTS_TABLE_SQL)
            statement.execute(CREATE_INDEX_BY_SOURCE_SQL)
            statement.execute(CREATE_INDEX_BY_SERIES_SQL)
            statement.execute(CREATE_INDEX_BY_ACCOUNT_SQL)
            statement.execute(CREATE_INDEX_BY_ACCOUNT_SERIES_SQL)
            // O `user_version` é um valor único por arquivo, e este arquivo é
            // compartilhado com o índice de sessões CLI. Ele pertence só ao
            // histórico: o índice já tentou usá-lo como contador de migração e
            // passou a ler este 3 como se fosse seu, concluindo que já havia
            // migrado. Quem precisar de versão própria aqui cria a sua tabela,
            // como `cli_index_meta`.
            statement.execute("PRAGMA user_version = 3;")
        }
    }

    private fun hasColumn(connection: Connection, table: String, column: String): Boolean {
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info($table);").use { resultSet ->
                while (resultSet.next()) {
                    if (resultSet.getString("name") == column) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun upsertAccount(
        connection: Connection,
        account: UsageAccountContext,
        capturedAt: Instant
    ): Long {
        connection.prepareStatement(UPSERT_ACCOUNT_SQL).use { statement ->
            statement.setString(1, account.key.source.name)
            statement.setString(2, account.key.providerAccountId)
            statement.setString(3, normalizeWorkspaceId(account.key.workspaceId))
            statement.setString(4, account.email)
            statement.setString(5, account.workspaceName)
            statement.setLong(6, capturedAt.toEpochMilliseconds())
            statement.executeUpdate()
        }

        connection.prepareStatement(SELECT_ACCOUNT_ID_SQL).use { statement ->
            statement.setString(1, account.key.source.name)
            statement.setString(2, account.key.providerAccountId)
            statement.setString(3, normalizeWorkspaceId(account.key.workspaceId))
            statement.executeQuery().use { resultSet ->
                if (!resultSet.next()) {
                    throw IllegalStateException("Conta de histórico não encontrada após o upsert.")
                }
                return resultSet.getLong("id")
            }
        }
    }

    private fun claimLegacySnapshots(
        connection: Connection,
        source: ApiSource,
        accountId: Long,
        capturedAt: Instant
    ) {
        connection.prepareStatement(INSERT_LEGACY_ASSIGNMENT_SQL).use { statement ->
            statement.setString(1, source.name)
            statement.setLong(2, accountId)
            statement.setLong(3, capturedAt.toEpochMilliseconds())
            val inserted = statement.executeUpdate()
            if (inserted == 0) {
                return
            }
        }

        connection.prepareStatement(ASSIGN_LEGACY_SNAPSHOTS_SQL).use { statement ->
            statement.setLong(1, accountId)
            statement.setString(2, source.name)
            statement.executeUpdate()
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
        const val CREATE_ACCOUNTS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS usage_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL,
                provider_account_id TEXT NOT NULL,
                workspace_id TEXT NOT NULL DEFAULT '',
                email TEXT NOT NULL,
                workspace_name TEXT,
                last_seen_at INTEGER NOT NULL,
                UNIQUE(source, provider_account_id, workspace_id)
            );
        """

        const val CREATE_SNAPSHOTS_TABLE_SQL = """
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
                captured_at INTEGER NOT NULL,
                account_id INTEGER REFERENCES usage_accounts(id),
                has_known_reset_at INTEGER NOT NULL DEFAULT 1
            );
        """

        const val CREATE_LEGACY_ASSIGNMENTS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS usage_legacy_account_assignments (
                source TEXT PRIMARY KEY,
                account_id INTEGER NOT NULL REFERENCES usage_accounts(id),
                assigned_at INTEGER NOT NULL
            );
        """

        const val ADD_ACCOUNT_ID_COLUMN_SQL = """
            ALTER TABLE usage_snapshots
            ADD COLUMN account_id INTEGER REFERENCES usage_accounts(id);
        """

        // `DEFAULT 1` e não `0`: a linha gravada antes desta coluna afirmava
        // implicitamente que havia reset, e é isso que ela continua afirmando.
        // A decisão de risco lê o **último** ponto da série, que é sempre de
        // agora — então a primeira coleta depois da migração já corrige a cota
        // que nunca teve reset, sem precisar reescrever o histórico.
        const val ADD_HAS_KNOWN_RESET_COLUMN_SQL = """
            ALTER TABLE usage_snapshots
            ADD COLUMN has_known_reset_at INTEGER NOT NULL DEFAULT 1;
        """

        const val CREATE_INDEX_BY_SOURCE_SQL = """
            CREATE INDEX IF NOT EXISTS idx_usage_snapshots_source_captured
            ON usage_snapshots(source, captured_at);
        """

        const val CREATE_INDEX_BY_SERIES_SQL = """
            CREATE INDEX IF NOT EXISTS idx_usage_snapshots_series
            ON usage_snapshots(source, quota_label, period_end_at, captured_at);
        """

        const val CREATE_INDEX_BY_ACCOUNT_SQL = """
            CREATE INDEX IF NOT EXISTS idx_usage_snapshots_account_captured
            ON usage_snapshots(source, account_id, captured_at);
        """

        const val CREATE_INDEX_BY_ACCOUNT_SERIES_SQL = """
            CREATE INDEX IF NOT EXISTS idx_usage_snapshots_account_series
            ON usage_snapshots(source, account_id, quota_label, period_end_at, captured_at);
        """

        const val UPSERT_ACCOUNT_SQL = """
            INSERT INTO usage_accounts (
                source, provider_account_id, workspace_id, email, workspace_name, last_seen_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(source, provider_account_id, workspace_id) DO UPDATE SET
                email = excluded.email,
                workspace_name = excluded.workspace_name,
                last_seen_at = excluded.last_seen_at;
        """

        const val SELECT_ACCOUNT_ID_SQL = """
            SELECT id
            FROM usage_accounts
            WHERE source = ? AND provider_account_id = ? AND workspace_id = ?;
        """

        const val INSERT_LEGACY_ASSIGNMENT_SQL = """
            INSERT OR IGNORE INTO usage_legacy_account_assignments (source, account_id, assigned_at)
            VALUES (?, ?, ?);
        """

        const val ASSIGN_LEGACY_SNAPSHOTS_SQL = """
            UPDATE usage_snapshots
            SET account_id = ?
            WHERE source = ? AND account_id IS NULL;
        """

        const val INSERT_SQL = """
            INSERT INTO usage_snapshots (
                source, quota_label, period_type, unit, used, total, raw_used, raw_total,
                period_end_at, captured_at, account_id, has_known_reset_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        const val SELECT_COLUMNS = """
            source, quota_label, period_type, unit, used, total, raw_used, raw_total,
            period_end_at, captured_at, has_known_reset_at
        """

        const val SELECT_BY_SOURCE_SQL = """
            SELECT $SELECT_COLUMNS
            FROM usage_snapshots
            WHERE source = ? AND captured_at >= ?
            ORDER BY quota_label ASC, captured_at ASC;
        """

        const val SELECT_BY_ACCOUNT_SQL = """
            SELECT s.source, s.quota_label, s.period_type, s.unit, s.used, s.total,
                   s.raw_used, s.raw_total, s.period_end_at, s.captured_at, s.has_known_reset_at
            FROM usage_snapshots s
            INNER JOIN usage_accounts a ON a.id = s.account_id
            WHERE a.source = ?
              AND a.provider_account_id = ?
              AND a.workspace_id = ?
              AND s.captured_at >= ?
            ORDER BY s.quota_label ASC, s.captured_at ASC;
        """

        const val SELECT_ACCOUNTS_SQL = """
            SELECT a.provider_account_id, a.workspace_id, a.email, a.workspace_name,
                   MAX(s.captured_at) AS latest_snapshot_at
            FROM usage_accounts a
            INNER JOIN usage_snapshots s ON s.account_id = a.id
            WHERE a.source = ?
            GROUP BY a.id, a.provider_account_id, a.workspace_id, a.email, a.workspace_name
            ORDER BY latest_snapshot_at DESC, a.email ASC;
        """

        const val DELETE_EXPIRED_SQL = """
            DELETE FROM usage_snapshots
            WHERE captured_at < ?;
        """

        fun normalizeWorkspaceId(workspaceId: String?): String = workspaceId?.trim().orEmpty()

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/usage-history.db")
        }
    }
}
