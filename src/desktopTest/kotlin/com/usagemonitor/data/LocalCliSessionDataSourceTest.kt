package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCliSessionDataSource
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.CliSessionHealth
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PROFILE_A = "default"
private const val PROFILE_B = "conta2"

private val LEGACY_FILES_TABLE_SQL = """
    CREATE TABLE cli_session_files (
      path TEXT PRIMARY KEY,
      last_modified INTEGER NOT NULL,
      size_bytes INTEGER NOT NULL,
      last_offset INTEGER NOT NULL DEFAULT 0
    );
"""

private val LEGACY_SESSIONS_TABLE_SQL = """
    CREATE TABLE cli_sessions (
      session_id TEXT PRIMARY KEY,
      file_path TEXT NOT NULL,
      cwd TEXT,
      git_branch TEXT,
      first_ts INTEGER NOT NULL DEFAULT 0,
      last_ts INTEGER NOT NULL DEFAULT 0,
      primary_model TEXT,
      turn_count INTEGER NOT NULL DEFAULT 0,
      input_tokens INTEGER NOT NULL DEFAULT 0,
      output_tokens INTEGER NOT NULL DEFAULT 0,
      cache_read_tokens INTEGER NOT NULL DEFAULT 0,
      cache_write_5m_tokens INTEGER NOT NULL DEFAULT 0,
      cache_write_1h_tokens INTEGER NOT NULL DEFAULT 0,
      cost_micros INTEGER NOT NULL DEFAULT 0,
      unpriced_turns INTEGER NOT NULL DEFAULT 0,
      hidden INTEGER NOT NULL DEFAULT 0
    );
"""

class LocalCliSessionDataSourceTest {

    @Test
    fun `indexes a simple session`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", outputTokens = 100L, cacheReadTokens = 1_000L),
                assistantLine("session-a", "msg-2", "2026-08-01T10:05:00Z", outputTokens = 200L, cacheReadTokens = 2_000L)
            )

            val report = dataSource.syncIndex()

            assertEquals(1, report.scannedFiles)
            assertEquals(1, report.updatedFiles)
            assertEquals(0, report.skippedLines)

            val sessions = dataSource.readSessions()
            assertEquals(1, sessions.size)

            val session = sessions.single()
            assertEquals("session-a", session.sessionId)
            assertEquals(2, session.turnCount)
            assertEquals(300L, session.outputTokens)
            assertEquals(3_000L, session.cacheReadTokens)
            assertEquals("claude-opus-5", session.primaryModel)
            assertEquals("usage-monitor", session.projectName)
            assertEquals("main", session.gitBranch)
            assertFalse(session.stale)
            assertTrue(session.isCostComplete)
        }
    }

    @Test
    fun `unchanged file is skipped on the second pass`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))

            dataSource.syncIndex()
            val second = dataSource.syncIndex()

            assertEquals(1, second.scannedFiles)
            assertEquals(0, second.updatedFiles)
        }
    }

    @Test
    fun `incremental pass reads only the appended delta`() = runTest {
        withFixture { root, dataSource ->
            val first = assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", outputTokens = 100L)
            val second = assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z", outputTokens = 100L)
            val file = writeTranscript(root, "session-a", first, second)

            dataSource.syncIndex()

            // Corrompe o trecho já consumido mantendo o mesmo tamanho em bytes e
            // acrescenta uma linha válida. Se a segunda passada relesse do início,
            // essas linhas apareceriam como ignoradas.
            val corruptedPrefix = "x".repeat(first.length) + "\n" + "x".repeat(second.length) + "\n"
            val third = assistantLine("session-a", "msg-3", "2026-08-01T10:02:00Z", outputTokens = 100L)
            file.writeText(corruptedPrefix + third + "\n")

            val report = dataSource.syncIndex()

            assertEquals(1, report.updatedFiles)
            assertEquals(0, report.skippedLines)
            assertEquals(3, dataSource.readSessions().single().turnCount)
        }
    }

    @Test
    fun `partial trailing line is only consumed once complete`() = runTest {
        withFixture { root, dataSource ->
            val complete = assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z")
            val partial = assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z")
            val file = writeTranscript(root, "session-a", complete)
            // Sessão em escrita: última linha ainda sem `\n`.
            file.appendText(partial.dropLast(20))

            dataSource.syncIndex()
            assertEquals(1, dataSource.readSessions().single().turnCount)

            file.writeText(complete + "\n" + partial + "\n")
            dataSource.syncIndex()

            assertEquals(2, dataSource.readSessions().single().turnCount)
        }
    }

    @Test
    fun `corrupted line is skipped without breaking the session`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"),
                """{"type":"assistant","message":{"usage":{"input_tokens":""",
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z")
            )

            val report = dataSource.syncIndex()

            assertEquals(1, report.skippedLines)
            assertEquals(2, dataSource.readSessions().single().turnCount)
        }
    }

    @Test
    fun `duplicated message id is counted once`() = runTest {
        withFixture { root, dataSource ->
            val line = assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", outputTokens = 500L)
            writeTranscript(root, "session-a", line, line)

            dataSource.syncIndex()

            val session = dataSource.readSessions().single()
            assertEquals(1, session.turnCount)
            assertEquals(500L, session.outputTokens)
        }
    }

    @Test
    fun `cost uses the model of each turn`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = "claude-opus-5", outputTokens = 1_000_000L),
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z", model = "claude-sonnet-5", outputTokens = 1_000_000L),
                assistantLine("session-a", "msg-3", "2026-08-01T10:02:00Z", model = "claude-opus-5", outputTokens = 1_000_000L)
            )

            dataSource.syncIndex()

            val session = dataSource.readSessions().single()
            // 25,00 + 15,00 + 25,00 USD. A fórmula Sonnet fixa do legado daria 45,00.
            assertEquals(65_000_000L, session.costMicros)
            assertEquals("claude-opus-5", session.primaryModel)
        }
    }

    @Test
    fun `unknown model is counted as unpriced`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = "claude-opus-5", outputTokens = 1_000_000L),
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z", model = "gpt-5-codex", outputTokens = 1_000_000L)
            )

            dataSource.syncIndex()

            val session = dataSource.readSessions().single()
            assertEquals(1, session.unpricedTurnCount)
            assertFalse(session.isCostComplete)
            assertEquals(25_000_000L, session.costMicros)
        }
    }

    @Test
    fun `truncated file is reindexed from scratch`() = runTest {
        withFixture { root, dataSource ->
            val file = writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z"),
                assistantLine("session-a", "msg-3", "2026-08-01T10:02:00Z")
            )
            dataSource.syncIndex()

            file.writeText(assistantLine("session-b", "msg-9", "2026-08-02T10:00:00Z") + "\n")
            dataSource.syncIndex()

            val sessions = dataSource.readSessions()
            assertEquals(1, sessions.size)
            assertEquals("session-b", sessions.single().sessionId)
            assertEquals(1, sessions.single().turnCount)
        }
    }

    @Test
    fun `non assistant lines are ignored`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                """{"type":"user","sessionId":"session-a","message":{"role":"user","content":"oi"}}""",
                """{"type":"file-history-snapshot","sessionId":"session-a"}""",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z")
            )

            val report = dataSource.syncIndex()

            assertEquals(0, report.skippedLines)
            assertEquals(1, dataSource.readSessions().single().turnCount)
        }
    }

    @Test
    fun `sidechain turns are flagged in the detail`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"),
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z", isSidechain = true)
            )
            dataSource.syncIndex()

            val detail = assertNotNull(dataSource.readSession("session-a"))
            assertEquals(2, detail.turns.size)
            assertFalse(detail.turns[0].isSidechain)
            assertTrue(detail.turns[1].isSidechain)
        }
    }

    @Test
    fun `live context comes from the last main turn`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", cacheReadTokens = 10_000L),
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z", cacheReadTokens = 90_000L),
                // Subagente depois do último turno principal: tem contexto próprio.
                assistantLine(
                    "session-a",
                    "msg-3",
                    "2026-08-01T10:02:00Z",
                    model = "claude-haiku-4-5",
                    cacheReadTokens = 3_000L,
                    isSidechain = true
                )
            )
            dataSource.syncIndex()

            val session = dataSource.readSessions().single()
            assertEquals(90_000L, session.liveContextTokens)
            assertEquals("claude-opus-5", session.liveContextModel)
            assertEquals(90_000L, assertNotNull(dataSource.readSession("session-a")).summary.liveContextTokens)
        }
    }

    @Test
    fun `a session with only sidechain turns has no live context`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", cacheReadTokens = 7_000L, isSidechain = true)
            )
            dataSource.syncIndex()

            val session = dataSource.readSessions().single()
            assertEquals(0L, session.liveContextTokens)
            assertNull(session.liveContextModel)
            assertEquals(CliSessionHealth.HEALTHY, session.contextStatus.health)
        }
    }

    /**
     * O contexto vivo é o estado atual da sessão, não o do recorte: recortá-lo
     * pela janela de quota daria um número sem significado.
     */
    @Test
    fun `live context ignores the time window`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", cacheReadTokens = 10_000L),
                assistantLine("session-a", "msg-2", "2026-08-01T20:00:00Z", cacheReadTokens = 90_000L)
            )
            dataSource.syncIndex()

            val windowed = dataSource.readSessions(
                sinceEpochMillis = epochMillis("2026-08-01T15:00:00Z")
            ).single()

            assertEquals(1, windowed.turnCount)
            assertEquals(90_000L, windowed.liveContextTokens)
        }
    }

    @Test
    fun `cache creation tiers are read separately`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine(
                    "session-a",
                    "msg-1",
                    "2026-08-01T10:00:00Z",
                    cacheWrite5mTokens = 700L,
                    cacheWrite1hTokens = 300L
                )
            )
            dataSource.syncIndex()

            val session = dataSource.readSessions().single()
            assertEquals(700L, session.cacheWrite5mTokens)
            assertEquals(300L, session.cacheWrite1hTokens)
            assertEquals(1_000L, session.cacheWriteTokens)
        }
    }

    @Test
    fun `sessions are listed from newest to oldest`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(root, "session-old", assistantLine("session-old", "msg-1", "2026-07-01T10:00:00Z"))
            writeTranscript(root, "session-new", assistantLine("session-new", "msg-1", "2026-08-01T10:00:00Z"))

            dataSource.syncIndex()

            assertEquals(
                listOf("session-new", "session-old"),
                dataSource.readSessions().map { session -> session.sessionId }
            )
        }
    }

    @Test
    fun `turns older than the window are left out of the aggregates`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", outputTokens = 1_000L),
                assistantLine("session-a", "msg-2", "2026-08-01T20:00:00Z", outputTokens = 300L)
            )
            dataSource.syncIndex()

            val windowed = dataSource.readSessions(
                sinceEpochMillis = epochMillis("2026-08-01T15:00:00Z")
            ).single()

            assertEquals(1, windowed.turnCount)
            assertEquals(300L, windowed.outputTokens)
            assertEquals(epochMillis("2026-08-01T20:00:00Z"), windowed.firstTs.toEpochMilliseconds())
            assertEquals(epochMillis("2026-08-01T20:00:00Z"), windowed.lastTs.toEpochMilliseconds())
            // 300 tokens de output a 25 USD/M.
            assertEquals(7_500L, windowed.costMicros)
            assertTrue(windowed.isCostComplete)
        }
    }

    @Test
    fun `session without turns in the window is not listed`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(root, "session-old", assistantLine("session-old", "msg-1", "2026-07-01T10:00:00Z"))
            writeTranscript(root, "session-new", assistantLine("session-new", "msg-1", "2026-08-01T10:00:00Z"))
            dataSource.syncIndex()

            val windowed = dataSource.readSessions(sinceEpochMillis = epochMillis("2026-07-15T00:00:00Z"))

            assertEquals(listOf("session-new"), windowed.map { session -> session.sessionId })
        }
    }

    @Test
    fun `a model switch inside the window is priced per stretch`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine(
                    "session-a",
                    "msg-1",
                    "2026-08-01T10:00:00Z",
                    model = "claude-opus-5",
                    outputTokens = 1_000L
                ),
                assistantLine(
                    "session-a",
                    "msg-2",
                    "2026-08-01T10:05:00Z",
                    model = "claude-haiku-4-5-20251001",
                    outputTokens = 1_000L
                ),
                assistantLine(
                    "session-a",
                    "msg-3",
                    "2026-08-01T10:06:00Z",
                    model = "claude-haiku-4-5-20251001",
                    outputTokens = 1_000L
                )
            )
            dataSource.syncIndex()

            val windowed = dataSource.readSessions(
                sinceEpochMillis = epochMillis("2026-08-01T09:00:00Z")
            ).single()

            // 1K de output a 25 USD/M (opus) + 2K a 5 USD/M (haiku).
            assertEquals(25_000L + 10_000L, windowed.costMicros)
            assertEquals(3, windowed.turnCount)
            // O modelo dominante da janela é o que aparece na lista.
            assertEquals("claude-haiku-4-5-20251001", windowed.primaryModel)
        }
    }

    @Test
    fun `turns with an unknown model are counted as unpriced inside the window`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", model = "modelo-fantasma"),
                assistantLine("session-a", "msg-2", "2026-08-01T10:01:00Z", outputTokens = 1_000L)
            )
            dataSource.syncIndex()

            val windowed = dataSource.readSessions(
                sinceEpochMillis = epochMillis("2026-08-01T09:00:00Z")
            ).single()

            assertEquals(1, windowed.unpricedTurnCount)
            assertFalse(windowed.isCostComplete)
            assertEquals(25_000L, windowed.costMicros)
        }
    }

    @Test
    fun `a window covering the whole session matches the stored aggregates`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(
                root,
                "session-a",
                assistantLine(
                    "session-a",
                    "msg-1",
                    "2026-08-01T10:00:00Z",
                    inputTokens = 137L,
                    outputTokens = 991L,
                    cacheReadTokens = 33_333L,
                    cacheWrite5mTokens = 777L
                ),
                assistantLine(
                    "session-a",
                    "msg-2",
                    "2026-08-01T10:05:00Z",
                    inputTokens = 41L,
                    outputTokens = 613L,
                    cacheReadTokens = 12_345L,
                    cacheWrite1hTokens = 99L
                )
            )
            dataSource.syncIndex()

            val stored = dataSource.readSessions().single()
            val windowed = dataSource.readSessions(
                sinceEpochMillis = epochMillis("2026-07-01T00:00:00Z")
            ).single()

            assertEquals(stored.turnCount, windowed.turnCount)
            assertEquals(stored.totalTokens, windowed.totalTokens)
            assertEquals(stored.firstTs, windowed.firstTs)
            assertEquals(stored.lastTs, windowed.lastTs)
            assertEquals(stored.primaryModel, windowed.primaryModel)
            // O agregado gravado trunca o custo turno a turno; a janela divide uma
            // única vez. A diferença é de micros e nunca a favor do erro.
            assertTrue(
                windowed.costMicros - stored.costMicros in 0L..2L,
                "custo janelado ${windowed.costMicros} destoa do gravado ${stored.costMicros}"
            )
        }
    }

    @Test
    fun `a session whose transcript is gone stays listed inside the window`() = runTest {
        withFixture { root, dataSource ->
            val file = writeTranscript(
                root,
                "session-a",
                assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z", outputTokens = 100L)
            )
            dataSource.syncIndex()

            assertTrue(file.delete())
            dataSource.syncIndex()

            val windowed = dataSource.readSessions(
                sinceEpochMillis = epochMillis("2026-08-01T09:00:00Z")
            ).single()

            assertEquals(1, windowed.turnCount)
            assertTrue(windowed.stale)
        }
    }

    @Test
    fun `the window respects the account filter`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val rootA = File(tempDir, "claude/projects").also { it.mkdirs() }
        val rootB = File(tempDir, "claude-conta2/projects").also { it.mkdirs() }
        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = {
                listOf(
                    CliProjectRoot(PROFILE_A, rootA.absolutePath),
                    CliProjectRoot(PROFILE_B, rootB.absolutePath)
                )
            },
            databaseFile = File(tempDir, "cli.db")
        )
        try {
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-1", "2026-08-01T11:00:00Z"))
            dataSource.syncIndex()

            val since = epochMillis("2026-08-01T09:00:00Z")

            assertEquals(
                listOf("session-a"),
                dataSource.readSessions(profileId = PROFILE_A, sinceEpochMillis = since)
                    .map { session -> session.sessionId }
            )
            assertEquals(2, dataSource.readSessions(profileId = null, sinceEpochMillis = since).size)
        } finally {
            dataSource.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `windowed sessions are listed from newest to oldest`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(root, "session-old", assistantLine("session-old", "msg-1", "2026-08-01T10:00:00Z"))
            writeTranscript(root, "session-new", assistantLine("session-new", "msg-1", "2026-08-02T10:00:00Z"))
            dataSource.syncIndex()

            assertEquals(
                listOf("session-new", "session-old"),
                dataSource.readSessions(sinceEpochMillis = epochMillis("2026-07-01T00:00:00Z"))
                    .map { session -> session.sessionId }
            )
        }
    }

    @Test
    fun `the indexing machine is recorded and served by both read paths`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            dataSource.syncIndex()

            // O transcript não carrega máquina: o valor é o hostname de quem indexou.
            val expected = expectedHostName()
            assertEquals(expected, dataSource.readSessions().single().hostName)
            assertEquals(
                expected,
                dataSource.readSessions(sinceEpochMillis = epochMillis("2026-08-01T09:00:00Z"))
                    .single().hostName
            )
            assertEquals(expected, dataSource.readSession("session-a")?.summary?.hostName)
        }
    }

    @Test
    fun `sessions indexed before the column existed are backfilled`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "legacy.db")
        writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
        createLegacySchema(databaseFile)
        seedLegacySession(databaseFile, "session-a", File(root, "-workspace-usage-monitor/session-a.jsonl").absolutePath)

        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PROFILE_A, root.absolutePath)) },
            databaseFile = databaseFile
        )
        try {
            // Sem reindexar: o backfill roda na migração do schema.
            assertEquals(expectedHostName(), dataSource.readSessions().single().hostName)
        } finally {
            dataSource.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `session missing from the index returns null`() = runTest {
        withFixture { _, dataSource ->
            assertNull(dataSource.readSession("nope"))
        }
    }

    @Test
    fun `missing projects root yields an empty index`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PROFILE_A, File(tempDir, "missing").absolutePath)) },
            databaseFile = File(tempDir, "cli.db")
        )
        try {
            assertEquals(0, dataSource.syncIndex().scannedFiles)
            assertTrue(dataSource.readSessions().isEmpty())
        } finally {
            dataSource.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `each account only sees its own transcripts`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val rootA = File(tempDir, "claude/projects").also { it.mkdirs() }
        val rootB = File(tempDir, "claude-conta2/projects").also { it.mkdirs() }
        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = {
                listOf(
                    CliProjectRoot(PROFILE_A, rootA.absolutePath),
                    CliProjectRoot(PROFILE_B, rootB.absolutePath)
                )
            },
            databaseFile = File(tempDir, "cli.db")
        )

        try {
            writeTranscript(rootA, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            writeTranscript(rootB, "session-b", assistantLine("session-b", "msg-1", "2026-08-02T10:00:00Z"))

            dataSource.syncIndex()

            assertEquals(
                listOf("session-a"),
                dataSource.readSessions(profileId = PROFILE_A).map { session -> session.sessionId }
            )
            assertEquals(
                listOf("session-b"),
                dataSource.readSessions(profileId = PROFILE_B).map { session -> session.sessionId }
            )
            assertEquals(2, dataSource.readSessions(profileId = null).size)
            assertEquals(PROFILE_B, dataSource.readSessions(profileId = PROFILE_B).single().profileId)
        } finally {
            dataSource.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `unknown account yields no sessions`() = runTest {
        withFixture { root, dataSource ->
            writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
            dataSource.syncIndex()

            assertTrue(dataSource.readSessions(profileId = "inexistente").isEmpty())
        }
    }

    @Test
    fun `legacy schema is migrated assigning the account to indexed sessions`() = runTest {
        val tempDir = createTempDirectory().toFile()
        val root = File(tempDir, "projects").also { it.mkdirs() }
        val databaseFile = File(tempDir, "legacy.db")
        writeTranscript(root, "session-a", assistantLine("session-a", "msg-1", "2026-08-01T10:00:00Z"))
        createLegacySchema(databaseFile)

        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PROFILE_A, root.absolutePath)) },
            databaseFile = databaseFile
        )
        try {
            dataSource.syncIndex()
            dataSource.syncIndex()

            val session = dataSource.readSessions(profileId = PROFILE_A).single()
            assertEquals(PROFILE_A, session.profileId)
        } finally {
            dataSource.close()
            tempDir.deleteRecursively()
        }
    }

    /** Mesma cadeia de resolução usada pelo datasource. */
    private fun expectedHostName(): String? {
        val fromEnvironment = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
        if (fromEnvironment != null) {
            return fromEnvironment
        }
        return runCatching { java.net.InetAddress.getLocalHost().hostName }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    /** Linha gravada por uma versão anterior, sem `host_name`. */
    private fun seedLegacySession(databaseFile: File, sessionId: String, filePath: String) {
        DriverManager.getConnection("jdbc:sqlite:" + databaseFile.absolutePath).use { connection ->
            connection.prepareStatement(
                "INSERT INTO cli_sessions (session_id, file_path, first_ts, last_ts) VALUES (?, ?, 0, 0);"
            ).use { statement ->
                statement.setString(1, sessionId)
                statement.setString(2, filePath)
                statement.executeUpdate()
            }
        }
    }

    /** Recria o schema anterior ao suporte a múltiplas contas, sem `profile_id`. */
    private fun createLegacySchema(databaseFile: File) {
        databaseFile.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:" + databaseFile.absolutePath).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(LEGACY_FILES_TABLE_SQL)
                statement.execute(LEGACY_SESSIONS_TABLE_SQL)
            }
        }
    }

    private suspend fun withFixture(block: suspend (File, LocalCliSessionDataSource) -> Unit) {
        val tempDir = createTempDirectory().toFile()
        val projectsRoot = File(tempDir, "projects").also { it.mkdirs() }
        val dataSource = LocalCliSessionDataSource(
            projectRootsProvider = { listOf(CliProjectRoot(PROFILE_A, projectsRoot.absolutePath)) },
            databaseFile = File(tempDir, "cli-sessions.db")
        )
        try {
            block(projectsRoot, dataSource)
        } finally {
            dataSource.close()
            tempDir.deleteRecursively()
        }
    }

    private fun epochMillis(timestamp: String): Long = Instant.parse(timestamp).toEpochMilliseconds()

    private fun writeTranscript(root: File, sessionId: String, vararg lines: String): File {
        val projectDir = File(root, "-workspace-usage-monitor").also { it.mkdirs() }
        val file = File(projectDir, "$sessionId.jsonl")
        file.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        return file
    }

    private fun assistantLine(
        sessionId: String,
        messageId: String,
        timestamp: String,
        model: String = "claude-opus-5",
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L,
        isSidechain: Boolean = false
    ): String {
        return """{"type":"assistant","uuid":"uuid-$messageId","sessionId":"$sessionId",""" +
            """"timestamp":"$timestamp","cwd":"/workspace/usage-monitor","gitBranch":"main",""" +
            """"version":"2.1.226","isSidechain":$isSidechain,""" +
            """"message":{"id":"$messageId","model":"$model","stop_reason":"end_turn","usage":{""" +
            """"input_tokens":$inputTokens,"output_tokens":$outputTokens,""" +
            """"cache_read_input_tokens":$cacheReadTokens,""" +
            """"cache_creation_input_tokens":${cacheWrite5mTokens + cacheWrite1hTokens},""" +
            """"cache_creation":{"ephemeral_5m_input_tokens":$cacheWrite5mTokens,""" +
            """"ephemeral_1h_input_tokens":$cacheWrite1hTokens},"service_tier":"standard"}}}"""
    }
}
