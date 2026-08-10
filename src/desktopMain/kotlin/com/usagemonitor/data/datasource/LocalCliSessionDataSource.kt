package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.ClaudeTranscriptLineDto
import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.sql.Connection
import java.sql.ResultSet

/**
 * Indexa os transcripts do Claude Code (`~/.claude/projects/**/*.jsonl`) num
 * índice SQLite e serve os agregados a partir dele.
 *
 * A indexação é incremental: o `.jsonl` é append-only, então cada passada lê
 * apenas o trecho a partir de `last_offset`. Se o arquivo encolheu (truncado ou
 * recriado) a sessão é reindexada do zero. Uma linha parcial no fim do arquivo
 * — sessão em escrita — não é consumida: o offset fica antes dela e a próxima
 * passada a lê completa.
 */
class LocalCliSessionDataSource(
    private val projectRootsProvider: () -> List<CliProjectRoot> = { listOf(defaultProjectRoot()) },
    databaseFile: File = defaultDatabaseFile()
) : CliSessionDataSource, AutoCloseable {

    private val connectionManager = SqliteConnectionManager(
        databaseFile = databaseFile,
        onOpen = { connection -> initializeSchema(connection) }
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun syncIndex(): CliSessionIndexReport = withContext(Dispatchers.IO) {
        val discovered = projectRootsProvider().flatMap { root -> listTranscriptFiles(root) }
        var updatedFiles = 0
        var skippedLines = 0

        connectionManager.useConnection { connection ->
            val knownFiles = readFileStates(connection)

            for (discoveredFile in discovered) {
                val file = discoveredFile.file
                val path = file.absolutePath
                val known = knownFiles[path]
                val sizeBytes = file.length()
                val lastModified = file.lastModified()

                val alreadyIndexed = known != null &&
                    known.sizeBytes == sizeBytes &&
                    known.lastModified == lastModified
                // Linha vinda de uma versão anterior do schema ainda não tem perfil:
                // precisa de uma passada para ser reatribuída, mesmo sem mudança no arquivo.
                if (alreadyIndexed && known.profileId == discoveredFile.profileId) {
                    continue
                }

                var startOffset = known?.lastOffset ?: 0L
                if (startOffset > sizeBytes) {
                    // Arquivo truncado ou recriado: o offset guardado não vale mais.
                    purgeIndexedFile(connection, path)
                    startOffset = 0L
                }

                skippedLines += indexFile(
                    connection = connection,
                    file = file,
                    profileId = discoveredFile.profileId,
                    startOffset = startOffset,
                    sizeBytes = sizeBytes,
                    lastModified = lastModified
                )
                updatedFiles++
            }
        }

        CliSessionIndexReport(
            scannedFiles = discovered.size,
            updatedFiles = updatedFiles,
            skippedLines = skippedLines
        )
    }

    override suspend fun readSessions(profileId: String?, includeHidden: Boolean): List<CliSessionSummary> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_SESSIONS_SQL).use { statement ->
                    statement.setInt(1, if (includeHidden) 1 else 0)
                    statement.setInt(2, if (profileId == null) 1 else 0)
                    statement.setString(3, profileId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(readSummary(rows))
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun readSession(sessionId: String): CliSessionDetail? {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                val summary = connection.prepareStatement(SELECT_SESSION_BY_ID_SQL).use { statement ->
                    statement.setString(1, sessionId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) readSummary(rows) else null
                    }
                } ?: return@useConnection null

                CliSessionDetail(summary = summary, turns = readTurns(connection, sessionId))
            }
        }
    }

    override suspend fun setHidden(sessionId: String, hidden: Boolean) {
        withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(UPDATE_HIDDEN_SQL).use { statement ->
                    statement.setInt(1, if (hidden) 1 else 0)
                    statement.setString(2, sessionId)
                    statement.executeUpdate()
                }
            }
        }
    }

    override fun close() {
        connectionManager.close()
    }

    private fun listTranscriptFiles(root: CliProjectRoot): List<DiscoveredTranscript> {
        val directory = File(root.directoryPath)
        if (!directory.isDirectory) {
            return emptyList()
        }
        return directory.walkTopDown()
            .filter { candidate -> candidate.isFile && candidate.name.endsWith(TRANSCRIPT_EXTENSION) }
            .map { file -> DiscoveredTranscript(file = file, profileId = root.profileId) }
            .toList()
    }

    /** Lê o delta do arquivo, grava os turnos novos e recalcula os agregados. Devolve as linhas ignoradas. */
    private fun indexFile(
        connection: Connection,
        file: File,
        profileId: String,
        startOffset: Long,
        sizeBytes: Long,
        lastModified: Long
    ): Int {
        val parsed = parseTranscript(file, startOffset)

        connection.autoCommit = false
        try {
            val touchedSessions = insertTurns(connection, file.absolutePath, profileId, parsed.turns)
            for (sessionId in touchedSessions) {
                recomputeSession(
                    connection = connection,
                    sessionId = sessionId,
                    filePath = file.absolutePath,
                    profileId = profileId,
                    metadata = parsed.metadataBySession[sessionId]
                )
            }
            upsertFileState(
                connection = connection,
                path = file.absolutePath,
                profileId = profileId,
                lastModified = lastModified,
                sizeBytes = sizeBytes,
                lastOffset = parsed.endOffset
            )
            connection.commit()
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }

        return parsed.skippedLines
    }

    private fun parseTranscript(file: File, startOffset: Long): ParsedTranscript {
        val turns = mutableListOf<ParsedTurn>()
        val metadata = mutableMapOf<String, SessionMetadata>()
        var skippedLines = 0

        val endOffset = forEachCompleteLine(file, startOffset) { rawLine ->
            // Rejeição barata: linhas sem `usage` nunca são turnos com consumo.
            if (!rawLine.contains(USAGE_MARKER)) {
                return@forEachCompleteLine
            }

            val line = runCatching { json.decodeFromString<ClaudeTranscriptLineDto>(rawLine) }.getOrNull()
            if (line == null) {
                skippedLines++
                return@forEachCompleteLine
            }

            val parsedTurn = line.toParsedTurn()
            if (parsedTurn == null) {
                return@forEachCompleteLine
            }

            turns.add(parsedTurn)
            metadata[parsedTurn.sessionId] = SessionMetadata(cwd = line.cwd, gitBranch = line.gitBranch)
        }

        return ParsedTranscript(
            turns = turns,
            metadataBySession = metadata,
            endOffset = endOffset,
            skippedLines = skippedLines
        )
    }

    private fun ClaudeTranscriptLineDto.toParsedTurn(): ParsedTurn? {
        if (type != ASSISTANT_LINE_TYPE) {
            return null
        }
        val usage = message?.usage ?: return null
        val messageId = message.id?.takeIf { it.isNotBlank() } ?: uuid?.takeIf { it.isNotBlank() } ?: return null
        val session = sessionId?.takeIf { it.isNotBlank() } ?: return null
        val instant = timestamp?.let { value -> runCatching { Instant.parse(value) }.getOrNull() } ?: return null

        return ParsedTurn(
            sessionId = session,
            messageId = messageId,
            timestampMillis = instant.toEpochMilliseconds(),
            model = message.model,
            isSidechain = isSidechain,
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
            cacheReadTokens = usage.cacheReadInputTokens,
            cacheWrite5mTokens = usage.cacheWrite5mTokens,
            cacheWrite1hTokens = usage.cacheWrite1hTokens
        )
    }

    /**
     * Percorre o arquivo a partir de [startOffset] entregando apenas linhas terminadas
     * em `\n`. Devolve o offset em bytes logo após a última linha completa.
     */
    private fun forEachCompleteLine(file: File, startOffset: Long, onLine: (String) -> Unit): Long {
        var offset = startOffset

        FileInputStream(file).use { stream ->
            stream.channel.position(startOffset)

            val buffer = ByteArray(READ_BUFFER_BYTES)
            val pendingBytes = ByteArrayOutputStream()

            while (true) {
                val readCount = stream.read(buffer)
                if (readCount <= 0) {
                    break
                }

                var lineStart = 0
                for (index in 0 until readCount) {
                    if (buffer[index] != NEW_LINE_BYTE) {
                        continue
                    }

                    pendingBytes.write(buffer, lineStart, index - lineStart + 1)
                    offset += pendingBytes.size().toLong()
                    val rawLine = pendingBytes.toByteArray().toString(Charsets.UTF_8).trim()
                    pendingBytes.reset()
                    lineStart = index + 1

                    if (rawLine.isNotEmpty()) {
                        onLine(rawLine)
                    }
                }

                if (lineStart < readCount) {
                    pendingBytes.write(buffer, lineStart, readCount - lineStart)
                }
            }
        }

        return offset
    }

    /** Insere os turnos ignorando `message_id` repetido. Devolve as sessões tocadas. */
    private fun insertTurns(
        connection: Connection,
        filePath: String,
        profileId: String,
        turns: List<ParsedTurn>
    ): Set<String> {
        if (turns.isEmpty()) {
            return emptySet()
        }

        val nextSeqBySession = mutableMapOf<String, Int>()
        val touched = linkedSetOf<String>()

        connection.prepareStatement(INSERT_TURN_SQL).use { statement ->
            for (turn in turns) {
                val seq = nextSeqBySession.getOrPut(turn.sessionId) {
                    readMaxSeq(connection, turn.sessionId) + 1
                }
                nextSeqBySession[turn.sessionId] = seq + 1
                touched.add(turn.sessionId)

                statement.setString(1, turn.sessionId)
                statement.setInt(2, seq)
                statement.setString(3, turn.messageId)
                statement.setLong(4, turn.timestampMillis)
                statement.setString(5, turn.model)
                statement.setInt(6, if (turn.isSidechain) 1 else 0)
                statement.setLong(7, turn.inputTokens)
                statement.setLong(8, turn.outputTokens)
                statement.setLong(9, turn.cacheReadTokens)
                statement.setLong(10, turn.cacheWrite5mTokens)
                statement.setLong(11, turn.cacheWrite1hTokens)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        // Garante que a sessão existe antes do recálculo, mesmo sem turnos novos aceitos.
        connection.prepareStatement(INSERT_SESSION_SHELL_SQL).use { statement ->
            for (sessionId in touched) {
                statement.setString(1, sessionId)
                statement.setString(2, filePath)
                statement.setString(3, profileId)
                statement.addBatch()
            }
            statement.executeBatch()
        }

        return touched
    }

    private fun readMaxSeq(connection: Connection, sessionId: String): Int {
        return connection.prepareStatement(SELECT_MAX_SEQ_SQL).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getInt(1) else 0
            }
        }
    }

    /**
     * Recalcula os agregados da sessão a partir dos turnos gravados. O custo é
     * somado turno a turno com o preço do modelo daquele turno — uma sessão que
     * trocou de modelo no meio fica correta.
     */
    private fun recomputeSession(
        connection: Connection,
        sessionId: String,
        filePath: String,
        profileId: String,
        metadata: SessionMetadata?
    ) {
        val turns = readTurns(connection, sessionId)
        if (turns.isEmpty()) {
            return
        }

        var costMicros = 0L
        var unpricedTurns = 0
        val modelCounts = mutableMapOf<String, Int>()

        for (turn in turns) {
            val turnCost = turn.costMicros
            if (turnCost == null) {
                unpricedTurns++
            } else {
                costMicros += turnCost
            }
            val model = turn.model
            if (model != null) {
                modelCounts[model] = (modelCounts[model] ?: 0) + 1
            }
        }

        val primaryModel = modelCounts.maxByOrNull { entry -> entry.value }?.key

        connection.prepareStatement(UPSERT_SESSION_SQL).use { statement ->
            statement.setString(1, sessionId)
            statement.setString(2, filePath)
            statement.setString(3, metadata?.cwd)
            statement.setString(4, metadata?.gitBranch)
            statement.setLong(5, turns.minOf { turn -> turn.ts.toEpochMilliseconds() })
            statement.setLong(6, turns.maxOf { turn -> turn.ts.toEpochMilliseconds() })
            statement.setString(7, primaryModel)
            statement.setInt(8, turns.size)
            statement.setLong(9, turns.sumOf { turn -> turn.inputTokens })
            statement.setLong(10, turns.sumOf { turn -> turn.outputTokens })
            statement.setLong(11, turns.sumOf { turn -> turn.cacheReadTokens })
            statement.setLong(12, turns.sumOf { turn -> turn.cacheWrite5mTokens })
            statement.setLong(13, turns.sumOf { turn -> turn.cacheWrite1hTokens })
            statement.setLong(14, costMicros)
            statement.setInt(15, unpricedTurns)
            statement.setString(16, profileId)
            statement.executeUpdate()
        }
    }

    private fun readTurns(connection: Connection, sessionId: String): List<CliSessionTurn> {
        return connection.prepareStatement(SELECT_TURNS_SQL).use { statement ->
            statement.setString(1, sessionId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            CliSessionTurn(
                                sessionId = sessionId,
                                seq = rows.getInt("seq"),
                                messageId = rows.getString("message_id"),
                                ts = Instant.fromEpochMilliseconds(rows.getLong("ts")),
                                model = rows.getString("model"),
                                isSidechain = rows.getInt("is_sidechain") == 1,
                                inputTokens = rows.getLong("input_tokens"),
                                outputTokens = rows.getLong("output_tokens"),
                                cacheReadTokens = rows.getLong("cache_read_tokens"),
                                cacheWrite5mTokens = rows.getLong("cache_write_5m_tokens"),
                                cacheWrite1hTokens = rows.getLong("cache_write_1h_tokens")
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readSummary(rows: ResultSet): CliSessionSummary {
        val filePath = rows.getString("file_path")
        return CliSessionSummary(
            sessionId = rows.getString("session_id"),
            filePath = filePath,
            profileId = rows.getString("profile_id"),
            cwd = rows.getString("cwd"),
            gitBranch = rows.getString("git_branch"),
            firstTs = Instant.fromEpochMilliseconds(rows.getLong("first_ts")),
            lastTs = Instant.fromEpochMilliseconds(rows.getLong("last_ts")),
            primaryModel = rows.getString("primary_model"),
            turnCount = rows.getInt("turn_count"),
            inputTokens = rows.getLong("input_tokens"),
            outputTokens = rows.getLong("output_tokens"),
            cacheReadTokens = rows.getLong("cache_read_tokens"),
            cacheWrite5mTokens = rows.getLong("cache_write_5m_tokens"),
            cacheWrite1hTokens = rows.getLong("cache_write_1h_tokens"),
            costMicros = rows.getLong("cost_micros"),
            unpricedTurnCount = rows.getInt("unpriced_turns"),
            hidden = rows.getInt("hidden") == 1,
            stale = !File(filePath).isFile
        )
    }

    private fun readFileStates(connection: Connection): Map<String, IndexedFileState> {
        return connection.prepareStatement(SELECT_FILES_SQL).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        val path = rows.getString("path")
                        put(
                            path,
                            IndexedFileState(
                                profileId = rows.getString("profile_id"),
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

    private fun upsertFileState(
        connection: Connection,
        path: String,
        profileId: String,
        lastModified: Long,
        sizeBytes: Long,
        lastOffset: Long
    ) {
        connection.prepareStatement(UPSERT_FILE_SQL).use { statement ->
            statement.setString(1, path)
            statement.setString(2, profileId)
            statement.setLong(3, lastModified)
            statement.setLong(4, sizeBytes)
            statement.setLong(5, lastOffset)
            statement.executeUpdate()
        }
    }

    /** Apaga sessões e turnos vindos de um arquivo que precisa ser reindexado do zero. */
    private fun purgeIndexedFile(connection: Connection, path: String) {
        connection.prepareStatement(DELETE_TURNS_BY_FILE_SQL).use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
        connection.prepareStatement(DELETE_SESSIONS_BY_FILE_SQL).use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
        connection.prepareStatement(DELETE_FILE_SQL).use { statement ->
            statement.setString(1, path)
            statement.executeUpdate()
        }
    }

    private fun initializeSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA journal_mode = WAL;")
            statement.execute("PRAGMA synchronous = NORMAL;")
            statement.execute(CREATE_FILES_TABLE_SQL)
            statement.execute(CREATE_SESSIONS_TABLE_SQL)
            statement.execute(CREATE_TURNS_TABLE_SQL)
        }

        // Bases criadas antes do suporte a múltiplas contas não têm `profile_id`.
        // Recriar as tabelas perderia o `hidden`, que é escolha do usuário — por
        // isso a coluna é acrescentada e as linhas antigas ficam nulas até a
        // próxima sincronização reatribuí-las pelo caminho do arquivo.
        addColumnIfMissing(connection, "cli_session_files", "profile_id")
        addColumnIfMissing(connection, "cli_sessions", "profile_id")

        connection.createStatement().use { statement ->
            statement.execute(CREATE_SESSIONS_INDEX_SQL)
            statement.execute(CREATE_TURNS_INDEX_SQL)
            statement.execute(CREATE_SESSIONS_PROFILE_INDEX_SQL)
        }
    }

    private fun addColumnIfMissing(connection: Connection, table: String, column: String) {
        val existing = connection.prepareStatement("PRAGMA table_info($table);").use { statement ->
            statement.executeQuery().use { rows ->
                buildSet {
                    while (rows.next()) {
                        add(rows.getString("name"))
                    }
                }
            }
        }
        if (column in existing) {
            return
        }

        connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE $table ADD COLUMN $column TEXT;")
        }
    }

    private data class DiscoveredTranscript(
        val file: File,
        val profileId: String
    )

    private data class IndexedFileState(
        val profileId: String?,
        val lastModified: Long,
        val sizeBytes: Long,
        val lastOffset: Long
    )

    private data class SessionMetadata(
        val cwd: String?,
        val gitBranch: String?
    )

    private data class ParsedTurn(
        val sessionId: String,
        val messageId: String,
        val timestampMillis: Long,
        val model: String?,
        val isSidechain: Boolean,
        val inputTokens: Long,
        val outputTokens: Long,
        val cacheReadTokens: Long,
        val cacheWrite5mTokens: Long,
        val cacheWrite1hTokens: Long
    )

    private data class ParsedTranscript(
        val turns: List<ParsedTurn>,
        val metadataBySession: Map<String, SessionMetadata>,
        val endOffset: Long,
        val skippedLines: Int
    )

    companion object {
        private const val TRANSCRIPT_EXTENSION = ".jsonl"
        private const val ASSISTANT_LINE_TYPE = "assistant"
        private const val USAGE_MARKER = "\"usage\""
        private const val READ_BUFFER_BYTES = 64 * 1024
        private val NEW_LINE_BYTE = '\n'.code.toByte()

        /** Raiz do perfil padrão, usada quando nenhum registry de contas é fornecido. */
        fun defaultProjectRoot(): CliProjectRoot {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return CliProjectRoot(
                profileId = DEFAULT_ANTHROPIC_PROFILE_ID,
                directoryPath = File(homeDir, ".claude/projects").absolutePath
            )
        }

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/usage-history.db")
        }

        private val CREATE_FILES_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS cli_session_files (
              path TEXT PRIMARY KEY,
              profile_id TEXT,
              last_modified INTEGER NOT NULL,
              size_bytes INTEGER NOT NULL,
              last_offset INTEGER NOT NULL DEFAULT 0
            );
        """

        private val CREATE_SESSIONS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS cli_sessions (
              session_id TEXT PRIMARY KEY,
              file_path TEXT NOT NULL,
              profile_id TEXT,
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

        private val CREATE_TURNS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS cli_turns (
              session_id TEXT NOT NULL,
              seq INTEGER NOT NULL,
              message_id TEXT NOT NULL,
              ts INTEGER NOT NULL,
              model TEXT,
              is_sidechain INTEGER NOT NULL DEFAULT 0,
              input_tokens INTEGER NOT NULL DEFAULT 0,
              output_tokens INTEGER NOT NULL DEFAULT 0,
              cache_read_tokens INTEGER NOT NULL DEFAULT 0,
              cache_write_5m_tokens INTEGER NOT NULL DEFAULT 0,
              cache_write_1h_tokens INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (session_id, message_id)
            );
        """

        private val CREATE_SESSIONS_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_cli_sessions_last_ts
            ON cli_sessions(hidden, last_ts DESC);
        """

        private val CREATE_SESSIONS_PROFILE_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_cli_sessions_profile
            ON cli_sessions(profile_id, hidden, last_ts DESC);
        """

        private val CREATE_TURNS_INDEX_SQL = """
            CREATE INDEX IF NOT EXISTS idx_cli_turns_session_seq
            ON cli_turns(session_id, seq);
        """

        private val SELECT_FILES_SQL = """
            SELECT path, profile_id, last_modified, size_bytes, last_offset FROM cli_session_files;
        """

        private val UPSERT_FILE_SQL = """
            INSERT INTO cli_session_files (path, profile_id, last_modified, size_bytes, last_offset)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(path) DO UPDATE SET
              profile_id = excluded.profile_id,
              last_modified = excluded.last_modified,
              size_bytes = excluded.size_bytes,
              last_offset = excluded.last_offset;
        """

        private val INSERT_TURN_SQL = """
            INSERT OR IGNORE INTO cli_turns (
              session_id, seq, message_id, ts, model, is_sidechain,
              input_tokens, output_tokens, cache_read_tokens,
              cache_write_5m_tokens, cache_write_1h_tokens
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        private val INSERT_SESSION_SHELL_SQL = """
            INSERT OR IGNORE INTO cli_sessions (session_id, file_path, profile_id) VALUES (?, ?, ?);
        """

        private val UPSERT_SESSION_SQL = """
            INSERT INTO cli_sessions (
              session_id, file_path, cwd, git_branch, first_ts, last_ts, primary_model,
              turn_count, input_tokens, output_tokens, cache_read_tokens,
              cache_write_5m_tokens, cache_write_1h_tokens, cost_micros, unpriced_turns, profile_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(session_id) DO UPDATE SET
              file_path = excluded.file_path,
              profile_id = excluded.profile_id,
              cwd = COALESCE(excluded.cwd, cli_sessions.cwd),
              git_branch = COALESCE(excluded.git_branch, cli_sessions.git_branch),
              first_ts = excluded.first_ts,
              last_ts = excluded.last_ts,
              primary_model = excluded.primary_model,
              turn_count = excluded.turn_count,
              input_tokens = excluded.input_tokens,
              output_tokens = excluded.output_tokens,
              cache_read_tokens = excluded.cache_read_tokens,
              cache_write_5m_tokens = excluded.cache_write_5m_tokens,
              cache_write_1h_tokens = excluded.cache_write_1h_tokens,
              cost_micros = excluded.cost_micros,
              unpriced_turns = excluded.unpriced_turns;
        """

        private val SELECT_MAX_SEQ_SQL = """
            SELECT COALESCE(MAX(seq), 0) FROM cli_turns WHERE session_id = ?;
        """

        private val SELECT_TURNS_SQL = """
            SELECT seq, message_id, ts, model, is_sidechain,
                   input_tokens, output_tokens, cache_read_tokens,
                   cache_write_5m_tokens, cache_write_1h_tokens
            FROM cli_turns
            WHERE session_id = ?
            ORDER BY seq ASC;
        """

        private val SESSION_COLUMNS = """
            session_id, file_path, profile_id, cwd, git_branch, first_ts, last_ts, primary_model,
            turn_count, input_tokens, output_tokens, cache_read_tokens,
            cache_write_5m_tokens, cache_write_1h_tokens, cost_micros, unpriced_turns, hidden
        """

        private val SELECT_SESSIONS_SQL = """
            SELECT $SESSION_COLUMNS
            FROM cli_sessions
            WHERE (? = 1 OR hidden = 0)
              AND (? = 1 OR profile_id = ?)
            ORDER BY last_ts DESC;
        """

        private val SELECT_SESSION_BY_ID_SQL = """
            SELECT $SESSION_COLUMNS FROM cli_sessions WHERE session_id = ?;
        """

        private val UPDATE_HIDDEN_SQL = """
            UPDATE cli_sessions SET hidden = ? WHERE session_id = ?;
        """

        private val DELETE_TURNS_BY_FILE_SQL = """
            DELETE FROM cli_turns
            WHERE session_id IN (SELECT session_id FROM cli_sessions WHERE file_path = ?);
        """

        private val DELETE_SESSIONS_BY_FILE_SQL = """
            DELETE FROM cli_sessions WHERE file_path = ?;
        """

        private val DELETE_FILE_SQL = """
            DELETE FROM cli_session_files WHERE path = ?;
        """
    }
}
