package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.data.datasource.LocalTeamSyncStateDataSource
import com.usagemonitor.domain.entity.CliProjectRoot
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val PROFILE_ID = "default"
private const val SESSION_ID = "session-a"
private const val LEGACY_TEAM_ID = "team-legacy"
private const val OTHER_TEAM_ID = "team-b"

class LocalTeamSyncStateDataSourceTest {

    @Test
    fun `marcador de team_sync_state antigo passa a valer para o time legado`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                assistantLine("msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("msg-2", "2026-08-01T10:05:00Z")
            )
            indexer.syncIndex()
            createLegacyState(indexer, lastSeqSent = 1)

            val batch = syncState.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100)

            assertEquals(listOf(2), batch.turns.map { turn -> turn.seq })
        }
    }

    @Test
    fun `migracao deixa team_sync_state intacta para o downgrade`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                assistantLine("msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("msg-2", "2026-08-01T10:05:00Z")
            )
            indexer.syncIndex()
            createLegacyState(indexer, lastSeqSent = 1)

            syncState.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100)

            assertEquals(
                1,
                scalar(indexer, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'team_sync_state';")
            )
            assertEquals(
                1,
                scalar(indexer, "SELECT COUNT(*) FROM team_sync_state WHERE session_id = '$SESSION_ID' AND last_seq_sent = 1;")
            )
        }
    }

    @Test
    fun `reabertura apos avanco do time legado nao volta o marcador antigo`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                assistantLine("msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("msg-2", "2026-08-01T10:05:00Z")
            )
            indexer.syncIndex()
            createLegacyState(indexer, lastSeqSent = 1)
            val first = syncState.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100)
            syncState.markPushed(LEGACY_TEAM_ID, first.turns, Clock.System.now())

            val reopened = LocalTeamSyncStateDataSource(indexer.sharedConnectionManager)

            assertEquals(0, reopened.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100).turns.size)
            assertEquals(
                2,
                scalar(
                    indexer,
                    "SELECT last_seq_sent FROM team_sync_watermarks " +
                        "WHERE team_id = '$LEGACY_TEAM_ID' AND session_id = '$SESSION_ID';"
                )
            )
        }
    }

    @Test
    fun `resetAll nao ressuscita o marcador do time legado da tabela antiga`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                assistantLine("msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("msg-2", "2026-08-01T10:05:00Z")
            )
            indexer.syncIndex()
            createLegacyState(indexer, lastSeqSent = 1)
            syncState.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100)
            syncState.resetAll()

            val reopened = LocalTeamSyncStateDataSource(indexer.sharedConnectionManager)

            assertEquals(
                listOf(1, 2),
                reopened.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100).turns.map { turn -> turn.seq }
            )
        }
    }

    @Test
    fun `marcador de um time nao avanca o do outro`() = runTest {
        withFixture { root, indexer, syncState ->
            writeTranscript(
                root,
                assistantLine("msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("msg-2", "2026-08-01T10:05:00Z")
            )
            indexer.syncIndex()
            val legacy = syncState.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100)
            syncState.markPushed(LEGACY_TEAM_ID, legacy.turns, Clock.System.now())

            val other = syncState.readPendingBatch(OTHER_TEAM_ID, PROFILE_ID, 100)

            assertEquals(2, other.turns.size)
            assertEquals(0, syncState.readPendingBatch(LEGACY_TEAM_ID, PROFILE_ID, 100).turns.size)
        }
    }

    private fun createLegacyState(indexer: LocalCliSessionDataSource, lastSeqSent: Int) {
        indexer.sharedConnectionManager.useConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE team_sync_state (session_id TEXT PRIMARY KEY, " +
                        "last_seq_sent INTEGER NOT NULL DEFAULT -1, last_pushed_at INTEGER NOT NULL DEFAULT 0);"
                )
                statement.execute(
                    "INSERT INTO team_sync_state (session_id, last_seq_sent, last_pushed_at) " +
                        "VALUES ('$SESSION_ID', $lastSeqSent, 1);"
                )
            }
        }
    }

    private fun scalar(indexer: LocalCliSessionDataSource, sql: String): Int {
        return indexer.sharedConnectionManager.useConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
    }

    private suspend fun withFixture(
        block: suspend (File, LocalCliSessionDataSource, LocalTeamSyncStateDataSource) -> Unit
    ) {
        val tempDir = createTempDirectory().toFile()
        val projectsRoot = File(tempDir, "projects").also { it.mkdirs() }
        val indexer = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PROFILE_ID, projectsRoot.absolutePath)) },
            databaseFile = File(tempDir, "usage-history.db")
        )
        val syncState = LocalTeamSyncStateDataSource(indexer.sharedConnectionManager)
        try {
            block(projectsRoot, indexer, syncState)
        } finally {
            indexer.close()
            tempDir.deleteRecursively()
        }
    }

    private fun writeTranscript(root: File, vararg lines: String) {
        val projectDir = File(root, "-workspace-usage-monitor").also { it.mkdirs() }
        File(projectDir, "$SESSION_ID.jsonl").writeText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    private fun assistantLine(messageId: String, timestamp: String): String {
        return """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$SESSION_ID",""" +
            """"timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":false,""" +
            """"message":{"id":"$messageId","model":"claude-opus-5","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":0,"output_tokens":100,""" +
            """"cache_read_input_tokens":0,""" +
            """"cache_creation_input_tokens":0,""" +
            """"cache_creation":{"ephemeral_5m_input_tokens":0,""" +
            """"ephemeral_1h_input_tokens":0},"service_tier":"standard"}}}"""
    }
}
