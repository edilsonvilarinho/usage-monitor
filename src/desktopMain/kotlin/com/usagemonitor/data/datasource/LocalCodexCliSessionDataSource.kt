package com.usagemonitor.data.datasource

import com.usagemonitor.data.parser.CodexCliRolloutMetadata
import com.usagemonitor.data.parser.CodexCliRolloutParser
import com.usagemonitor.domain.entity.CodexCliRolloutSource
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.sql.Connection

/** Índice SQLite isolado dos rollouts locais do Codex CLI. */
class LocalCodexCliSessionDataSource(
    private val codexHomeProvider: () -> File = { defaultCodexHome() },
    databaseFile: File = defaultDatabaseFile()
) : CodexCliSessionDataSource, AutoCloseable {

    private val parser = CodexCliRolloutParser()
    private val connectionManager = SqliteConnectionManager(
        databaseFile = databaseFile,
        onOpen = ::initializeSchema
    )

    override suspend fun syncIndex(): CodexCliSessionIndexReport = withContext(Dispatchers.IO) {
        val discovered = listRolloutFiles()
        var updatedFiles = 0
        var skippedLines = 0
        var unknownLines = 0

        connectionManager.useConnection { connection ->
            val knownFiles = readFileStates(connection)
            for (file in discovered) {
                val path = file.absolutePath
                val known = knownFiles[path]
                val size = file.length()
                val modified = file.lastModified()
                if (known != null && known.sizeBytes == size && known.lastModified == modified) {
                    continue
                }

                var startOffset = known?.lastOffset ?: 0L
                if (startOffset > size) {
                    purgeFile(connection, path)
                    startOffset = 0L
                }

                val metadata = readMetadata(file)
                val parsed = parseFromOffset(file, startOffset, metadata)
                connection.autoCommit = false
                try {
                    val nextSequenceBySession = parsed.turns
                        .map { turn -> turn.sessionId }
                        .distinct()
                        .associateWith { sessionId -> readNextSequence(connection, sessionId) }
                        .toMutableMap()
                    val touchedSessions = parsed.turns.map { turn -> turn.sessionId }.toSet()
                    for (turn in parsed.turns) {
                        val nextSequence = nextSequenceBySession.getValue(turn.sessionId)
                        nextSequenceBySession[turn.sessionId] = nextSequence + 1
                        val indexedTurn = turn.copy(seq = nextSequence)
                        insertTurn(connection, path, indexedTurn)
                    }
                    for (sessionId in touchedSessions) {
                        upsertSession(connection, sessionId, path, parsed.metadata)
                    }
                    upsertFileState(
                        connection = connection,
                        path = path,
                        lastModified = modified,
                        sizeBytes = size,
                        lastOffset = parsed.endOffset,
                        skippedLines = parsed.skippedLines
                    )
                    connection.commit()
                } catch (error: Throwable) {
                    connection.rollback()
                    throw error
                } finally {
                    connection.autoCommit = true
                }

                updatedFiles++
                skippedLines += parsed.skippedLines
                unknownLines += parsed.unknownLines
            }
        }

        CodexCliSessionIndexReport(
            scannedFiles = discovered.size,
            updatedFiles = updatedFiles,
            skippedLines = skippedLines,
            unknownLines = unknownLines
        )
    }

    override suspend fun readSessions(sinceEpochMillis: Long?): List<CodexCliSessionSummary> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                readSessionIds(connection).mapNotNull { sessionId ->
                    val turns = readTurns(connection, sessionId, sinceEpochMillis)
                    if (turns.isEmpty()) null else buildSummary(connection, sessionId, turns)
                }.sortedByDescending { summary -> summary.lastTs }
            }
        }
    }

    override suspend fun readSession(sessionId: String): CodexCliSessionDetail? {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                val turns = readTurns(connection, sessionId, null)
                if (turns.isEmpty()) return@useConnection null
                CodexCliSessionDetail(
                    summary = buildSummary(connection, sessionId, turns),
                    turns = turns
                )
            }
        }
    }

    override fun close() {
        connectionManager.close()
    }

    private fun listRolloutFiles(): List<File> {
        val sessionsDirectory = File(codexHomeProvider(), "sessions")
        if (!sessionsDirectory.isDirectory) {
            return emptyList()
        }
        return sessionsDirectory.walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith("rollout-") && file.extension == "jsonl" }
            .toList()
    }

    private fun readMetadata(file: File): CodexCliRolloutMetadata? {
        val firstLine = runCatching {
            file.bufferedReader(StandardCharsets.UTF_8).use { reader -> reader.readLine() }
        }.getOrNull() ?: return null
        return parser.parse(listOf(firstLine)).metadata
    }

    private fun parseFromOffset(
        file: File,
        startOffset: Long,
        metadata: CodexCliRolloutMetadata?
    ): ParsedFile {
        val lines = mutableListOf<String>()
        val endOffset = forEachCompleteLine(file, startOffset) { line -> lines += line }
        val parsed = parser.parse(lines, knownMetadata = metadata)
        return ParsedFile(
            metadata = parsed.metadata ?: metadata,
            turns = parsed.turns,
            endOffset = endOffset,
            skippedLines = parsed.skippedLines,
            unknownLines = parsed.unknownLines
        )
    }

    private fun forEachCompleteLine(file: File, startOffset: Long, onLine: (String) -> Unit): Long {
        FileInputStream(file).use { input ->
            var skipped = 0L
            while (skipped < startOffset) {
                val amount = input.skip(startOffset - skipped)
                if (amount <= 0L) {
                    return startOffset
                }
                skipped += amount
            }

            val line = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var absoluteOffset = startOffset
            var lastCompleteOffset = startOffset
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                for (index in 0 until count) {
                    val byte = buffer[index]
                    absoluteOffset++
                    if (byte == '\n'.code.toByte()) {
                        val raw = line.toByteArray().toString(StandardCharsets.UTF_8).removeSuffix("\r")
                        onLine(raw)
                        line.reset()
                        lastCompleteOffset = absoluteOffset
                    } else {
                        line.write(byte.toInt())
                    }
                }
            }
            return lastCompleteOffset
        }
    }

    private fun initializeSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA busy_timeout = 5000")
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS codex_cli_session_files (
                  path TEXT PRIMARY KEY,
                  last_modified INTEGER NOT NULL,
                  size_bytes INTEGER NOT NULL,
                  last_offset INTEGER NOT NULL DEFAULT 0,
                  skipped_lines INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS codex_cli_sessions (
                  session_id TEXT PRIMARY KEY,
                  file_path TEXT NOT NULL,
                  cwd TEXT,
                  source TEXT,
                  raw_source TEXT,
                  thread_source TEXT,
                  cli_version TEXT
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS codex_cli_turns (
                  session_id TEXT NOT NULL,
                  response_id TEXT NOT NULL,
                  turn_id TEXT NOT NULL,
                  seq INTEGER NOT NULL,
                  ts INTEGER NOT NULL,
                  model TEXT,
                  cwd TEXT,
                  source TEXT,
                  raw_source TEXT,
                  thread_source TEXT,
                  input_tokens INTEGER NOT NULL,
                  cached_input_tokens INTEGER NOT NULL,
                  cache_write_input_tokens INTEGER NOT NULL,
                  output_tokens INTEGER NOT NULL,
                  reasoning_output_tokens INTEGER NOT NULL,
                  total_tokens INTEGER NOT NULL,
                  PRIMARY KEY (session_id, response_id)
                )
                """.trimIndent()
            )
            statement.execute("CREATE INDEX IF NOT EXISTS idx_codex_cli_turns_session_ts ON codex_cli_turns(session_id, ts)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_codex_cli_sessions_last_file ON codex_cli_sessions(file_path)")
        }
    }

    private fun readFileStates(connection: Connection): Map<String, FileState> {
        return connection.prepareStatement(
            "SELECT path, last_modified, size_bytes, last_offset FROM codex_cli_session_files"
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getString("path"),
                            FileState(
                                lastModified = rows.getLong("last_modified"),
                                sizeBytes = rows.getLong("size_bytes"),
                                lastOffset = rows.getLong("last_offset")
                            )
                        )
                    }
                }
            }
        }
    }

    private fun insertTurn(connection: Connection, path: String, turn: CodexCliSessionTurn) {
        connection.prepareStatement(
            """
            INSERT OR IGNORE INTO codex_cli_turns (
              session_id, response_id, turn_id, seq, ts, model, cwd, source,
              raw_source, thread_source, input_tokens, cached_input_tokens,
              cache_write_input_tokens, output_tokens, reasoning_output_tokens, total_tokens
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, turn.sessionId)
            statement.setString(2, turn.responseId)
            statement.setString(3, turn.turnId)
            statement.setInt(4, turn.seq)
            statement.setLong(5, turn.ts.toEpochMilliseconds())
            statement.setString(6, turn.model)
            statement.setString(7, turn.cwd)
            statement.setString(8, turn.source.name)
            statement.setString(9, turn.rawSource)
            statement.setString(10, turn.threadSource)
            statement.setLong(11, turn.usage.inputTokens)
            statement.setLong(12, turn.usage.cachedInputTokens)
            statement.setLong(13, turn.usage.cacheWriteInputTokens)
            statement.setLong(14, turn.usage.outputTokens)
            statement.setLong(15, turn.usage.reasoningOutputTokens)
            statement.setLong(16, turn.usage.totalTokens)
            statement.executeUpdate()
        }
    }

    private fun readNextSequence(connection: Connection, sessionId: String): Int {
        return connection.prepareStatement(
            "SELECT COALESCE(MAX(seq), -1) + 1 FROM codex_cli_turns WHERE session_id = ?"
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getInt(1) else 0
            }
        }
    }

    private fun upsertSession(
        connection: Connection,
        sessionId: String,
        filePath: String,
        metadata: CodexCliRolloutMetadata?
    ) {
        connection.prepareStatement(
            """
            INSERT INTO codex_cli_sessions(session_id, file_path, cwd, source, raw_source, thread_source, cli_version)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
              file_path = excluded.file_path,
              cwd = COALESCE(excluded.cwd, codex_cli_sessions.cwd),
              source = COALESCE(excluded.source, codex_cli_sessions.source),
              raw_source = COALESCE(excluded.raw_source, codex_cli_sessions.raw_source),
              thread_source = COALESCE(excluded.thread_source, codex_cli_sessions.thread_source),
              cli_version = COALESCE(excluded.cli_version, codex_cli_sessions.cli_version)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.setString(2, filePath)
            statement.setString(3, metadata?.cwd)
            statement.setString(4, metadata?.source?.name)
            statement.setString(5, metadata?.rawSource)
            statement.setString(6, metadata?.threadSource)
            statement.setString(7, metadata?.cliVersion)
            statement.executeUpdate()
        }
    }

    private fun upsertFileState(
        connection: Connection,
        path: String,
        lastModified: Long,
        sizeBytes: Long,
        lastOffset: Long,
        skippedLines: Int
    ) {
        connection.prepareStatement(
            """
            INSERT INTO codex_cli_session_files(path, last_modified, size_bytes, last_offset, skipped_lines)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
              last_modified = excluded.last_modified,
              size_bytes = excluded.size_bytes,
              last_offset = excluded.last_offset,
              skipped_lines = codex_cli_session_files.skipped_lines + excluded.skipped_lines
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, path)
            statement.setLong(2, lastModified)
            statement.setLong(3, sizeBytes)
            statement.setLong(4, lastOffset)
            statement.setInt(5, skippedLines)
            statement.executeUpdate()
        }
    }

    private fun purgeFile(connection: Connection, path: String) {
        connection.prepareStatement(
            "DELETE FROM codex_cli_turns WHERE session_id IN (SELECT session_id FROM codex_cli_sessions WHERE file_path = ?)"
        ).use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM codex_cli_sessions WHERE file_path = ?").use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM codex_cli_session_files WHERE path = ?").use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
    }

    private fun readSessionIds(connection: Connection): List<String> {
        return connection.prepareStatement("SELECT session_id FROM codex_cli_sessions").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.getString("session_id"))
                }
            }
        }
    }

    private fun readTurns(connection: Connection, sessionId: String, sinceEpochMillis: Long?): List<CodexCliSessionTurn> {
        val sql = if (sinceEpochMillis == null) {
            "SELECT * FROM codex_cli_turns WHERE session_id = ? ORDER BY seq"
        } else {
            "SELECT * FROM codex_cli_turns WHERE session_id = ? AND ts >= ? ORDER BY seq"
        }
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, sessionId)
            if (sinceEpochMillis != null) statement.setLong(2, sinceEpochMillis)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(readTurn(rows))
                }
            }
        }
    }

    private fun readTurn(rows: java.sql.ResultSet): CodexCliSessionTurn {
        return CodexCliSessionTurn(
            sessionId = rows.getString("session_id"),
            turnId = rows.getString("turn_id"),
            responseId = rows.getString("response_id"),
            seq = rows.getInt("seq"),
            ts = Instant.fromEpochMilliseconds(rows.getLong("ts")),
            model = rows.getString("model"),
            cwd = rows.getString("cwd"),
            source = runCatching { CodexCliRolloutSource.valueOf(rows.getString("source")) }
                .getOrDefault(CodexCliRolloutSource.UNKNOWN),
            rawSource = rows.getString("raw_source"),
            threadSource = rows.getString("thread_source"),
            usage = CodexCliUsageDelta(
                inputTokens = rows.getLong("input_tokens"),
                cachedInputTokens = rows.getLong("cached_input_tokens"),
                cacheWriteInputTokens = rows.getLong("cache_write_input_tokens"),
                outputTokens = rows.getLong("output_tokens"),
                reasoningOutputTokens = rows.getLong("reasoning_output_tokens"),
                totalTokens = rows.getLong("total_tokens")
            )
        )
    }

    private fun buildSummary(
        connection: Connection,
        sessionId: String,
        turns: List<CodexCliSessionTurn>
    ): CodexCliSessionSummary {
        val sessionMetadata = connection.prepareStatement(
            "SELECT file_path, cwd, source, raw_source, thread_source, cli_version FROM codex_cli_sessions WHERE session_id = ?"
        ).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return@use null
                SessionMetadata(
                    filePath = rows.getString("file_path"),
                    cwd = rows.getString("cwd"),
                    source = rows.getString("source"),
                    rawSource = rows.getString("raw_source"),
                    threadSource = rows.getString("thread_source"),
                    cliVersion = rows.getString("cli_version")
                )
            }
        }
        val first = turns.first()
        val last = turns.last()
        val usage = turns.map { turn -> turn.usage }
        val source = sessionMetadata?.source?.let { value -> runCatching { CodexCliRolloutSource.valueOf(value) }.getOrNull() }
            ?: first.source
        return CodexCliSessionSummary(
            sessionId = sessionId,
            filePath = sessionMetadata?.filePath ?: "",
            cwd = sessionMetadata?.cwd ?: first.cwd,
            firstTs = turns.minOf { turn -> turn.ts },
            lastTs = turns.maxOf { turn -> turn.ts },
            primaryModel = turns.firstNotNullOfOrNull { turn -> turn.model },
            source = source,
            rawSource = sessionMetadata?.rawSource ?: first.rawSource,
            threadSource = sessionMetadata?.threadSource ?: first.threadSource,
            cliVersion = sessionMetadata?.cliVersion,
            turnCount = turns.map { turn -> turn.turnId }.distinct().size,
            responseCount = turns.size,
            inputTokens = usage.sumOf { value -> value.inputTokens },
            cachedInputTokens = usage.sumOf { value -> value.cachedInputTokens },
            cacheWriteInputTokens = usage.sumOf { value -> value.cacheWriteInputTokens },
            outputTokens = usage.sumOf { value -> value.outputTokens },
            reasoningOutputTokens = usage.sumOf { value -> value.reasoningOutputTokens },
            totalTokens = usage.sumOf { value -> value.totalTokens }
        )
    }

    private data class FileState(
        val lastModified: Long,
        val sizeBytes: Long,
        val lastOffset: Long
    )

    private data class ParsedFile(
        val metadata: CodexCliRolloutMetadata?,
        val turns: List<CodexCliSessionTurn>,
        val endOffset: Long,
        val skippedLines: Int,
        val unknownLines: Int
    )

    private data class SessionMetadata(
        val filePath: String,
        val cwd: String?,
        val source: String?,
        val rawSource: String?,
        val threadSource: String?,
        val cliVersion: String?
    )

    companion object {
        fun defaultCodexHome(): File {
            val configured = System.getenv("CODEX_HOME")?.takeIf { value -> value.isNotBlank() }
            val home = configured ?: File(System.getProperty("user.home"), ".codex").absolutePath
            return File(home)
        }

        fun defaultDatabaseFile(): File {
            val home = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(home, ".usage-monitor/codex-cli-history.db")
        }
    }
}
