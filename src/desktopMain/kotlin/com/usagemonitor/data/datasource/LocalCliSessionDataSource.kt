package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CliProjectRoot
import com.usagemonitor.domain.entity.CliSessionActiveTime
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliSessionTailOutcome
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TURN_GAP_CUTOFF_MILLIS
import com.usagemonitor.domain.entity.WindowedSessionAccumulator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap

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
        onOpen = { connection -> initializeCliIndexSchema(connection, hostName = { hostName }) }
    )

    /**
     * Conexão do índice, para quem precisa ler as mesmas tabelas.
     *
     * Compartilhar a instância em vez de abrir uma segunda conexão para o mesmo
     * arquivo é deliberado: `useConnection` é `synchronized`, então a leitura
     * espera a indexação terminar em vez de disputar o arquivo e receber
     * `SQLITE_BUSY` — o mesmo problema que já apareceu entre este datasource e o
     * histórico de uso.
     */
    internal val sharedConnectionManager: SqliteConnectionManager
        get() = connectionManager

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Máquina desta instância, resolvida uma única vez: `InetAddress` consulta DNS
     * e não pode entrar no laço de indexação.
     */
    private val hostName: String? by lazy { resolveHostName() }

    /**
     * Vereditos de cauda já calculados, por caminho de transcript.
     *
     * `ConcurrentHashMap` porque a leitura roda no dispatcher de I/O e pode ser
     * chamada por dois laços — o do semáforo e o da tela de Sessões CLI.
     */
    private val tailCache = ConcurrentHashMap<String, CachedTail>()

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

    override suspend fun readSessions(profileId: String?, sinceEpochMillis: Long?): List<CliSessionSummary> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                val liveContexts = readLiveContexts(connection)
                // Segunda consulta juntada por `session_id` em memória, pelo mesmo
                // desenho do contexto vivo: o tempo ativo não é agregado de
                // `cli_sessions`, sai da distância entre turnos.
                val activeTimes = readActiveTimes(connection, profileId, sinceEpochMillis ?: 0L)
                val sessions = if (sinceEpochMillis == null) {
                    readStoredSessions(connection, profileId, liveContexts)
                } else {
                    readWindowedSessions(connection, profileId, sinceEpochMillis, liveContexts)
                }
                // Sessão fora do mapa recebe zero, e não nulo: a consulta rodou, e
                // ausência ali significa "nenhum intervalo medido" — o caso da
                // sessão de um turno só.
                sessions.map { session -> session.copy(activeMillis = activeTimes[session.sessionId] ?: 0L) }
            }
        }
    }

    override suspend fun readSession(sessionId: String): CliSessionDetail? {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                val liveContexts = readLiveContexts(connection, sessionId)
                val summary = connection.prepareStatement(SELECT_SESSION_BY_ID_SQL).use { statement ->
                    statement.setString(1, sessionId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) readSummary(rows, liveContexts) else null
                    }
                } ?: return@useConnection null

                CliSessionDetail(summary = summary, turns = readTurns(connection, sessionId))
            }
        }
    }

    override suspend fun readUsageGroups(
        profileId: String?,
        sinceEpochMillis: Long
    ): List<CliUsageGroupRow> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_USAGE_GROUPS_SQL).use { statement ->
                    statement.setLong(1, sinceEpochMillis)
                    statement.setInt(2, if (profileId == null) 1 else 0)
                    statement.setString(3, profileId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    CliUsageGroupRow(
                                        sessionId = rows.getString("session_id"),
                                        cwd = rows.getString("cwd"),
                                        gitBranch = rows.getString("git_branch"),
                                        model = rows.getString("model"),
                                        turnCount = rows.getInt("turn_count"),
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
        }
    }

    override suspend fun readHourlyUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): List<CliHourlyUsageRow> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_HOURLY_USAGE_SQL).use { statement ->
                    statement.setLong(1, sinceEpochMillis)
                    statement.setInt(2, if (profileId == null) 1 else 0)
                    statement.setString(3, profileId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    CliHourlyUsageRow(
                                        hourStartMillis = rows.getLong("hour_bucket") * MILLIS_PER_HOUR,
                                        model = rows.getString("model"),
                                        turnCount = rows.getInt("turn_count"),
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
        }
    }

    override suspend fun readToolUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): List<CliToolUsage> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_TOOL_USAGE_SQL).use { statement ->
                    statement.setLong(1, sinceEpochMillis)
                    statement.setInt(2, if (profileId == null) 1 else 0)
                    statement.setString(3, profileId)
                    statement.executeQuery().use { rows ->
                        buildList {
                            while (rows.next()) {
                                add(
                                    CliToolUsage(
                                        toolName = rows.getString("tool_name"),
                                        callCount = rows.getInt("call_count"),
                                        turnCount = rows.getInt("turn_count")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun readSessionActiveTimes(
        profileId: String?,
        sinceEpochMillis: Long,
        gapCutoffMillis: Long
    ): List<CliSessionActiveTime> {
        return withContext(Dispatchers.IO) {
            connectionManager.useConnection { connection ->
                readActiveTimes(connection, profileId, sinceEpochMillis, gapCutoffMillis)
                    .map { entry -> CliSessionActiveTime(entry.key, entry.value) }
            }
        }
    }

    override suspend fun readSessionTails(sessionIds: Collection<String>): List<CliSessionTail> {
        if (sessionIds.isEmpty()) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val paths = connectionManager.useConnection { connection ->
                readSessionFilePaths(connection, sessionIds)
            }
            sessionIds.map { sessionId ->
                val transcript = paths[sessionId]?.let { path -> mainTranscriptOf(path, sessionId) }
                if (transcript == null || !transcript.isFile) {
                    CliSessionTail(sessionId, CliSessionTailOutcome.NOT_EVALUATED)
                } else {
                    readCachedTail(sessionId, transcript)
                }
            }
        }
    }

    override fun close() {
        connectionManager.close()
    }

    /**
     * Veredito da cauda, relido apenas quando o arquivo mudou.
     *
     * Sessão travada, por definição, não escreve mais nada: sem o cache, cada
     * passada do laço de trinta segundos releria os mesmos 256 KB para chegar à
     * mesma conclusão. A chave é `(tamanho, data de modificação)`, o mesmo par que
     * `syncIndex` já usa para decidir se um arquivo precisa ser relido.
     */
    private fun readCachedTail(sessionId: String, file: File): CliSessionTail {
        val sizeBytes = file.length()
        val lastModified = file.lastModified()

        val cached = tailCache[file.absolutePath]
        if (cached != null && cached.sizeBytes == sizeBytes && cached.lastModified == lastModified) {
            return cached.tail.copy(sessionId = sessionId)
        }

        val tail = readTail(sessionId, file, sizeBytes, json)
        tailCache[file.absolutePath] = CachedTail(sizeBytes, lastModified, tail)
        return tail
    }

    private fun readSessionFilePaths(
        connection: Connection,
        sessionIds: Collection<String>
    ): Map<String, String> {
        val placeholders = sessionIds.joinToString(separator = ",") { "?" }
        val sql = "SELECT session_id, file_path FROM cli_sessions WHERE session_id IN ($placeholders);"
        return connection.prepareStatement(sql).use { statement ->
            sessionIds.forEachIndexed { index, sessionId -> statement.setString(index + 1, sessionId) }
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(rows.getString("session_id"), rows.getString("file_path"))
                    }
                }
            }
        }
    }

    /** Agregados históricos completos, lidos direto de `cli_sessions`. */
    private fun readStoredSessions(
        connection: Connection,
        profileId: String?,
        liveContexts: Map<String, LiveContext>
    ): List<CliSessionSummary> {
        return connection.prepareStatement(SELECT_SESSIONS_SQL).use { statement ->
            statement.setInt(1, if (profileId == null) 1 else 0)
            statement.setString(2, profileId)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(readSummary(rows, liveContexts))
                    }
                }
            }
        }
    }

    /**
     * Contexto vivo de cada sessão: o `cache_read` e o modelo do último turno da
     * thread principal.
     *
     * Fica fora do recorte temporal de propósito — é o estado atual da sessão que
     * diz quanto custa a próxima mensagem, não o que aconteceu dentro da janela.
     * Turnos de subagente ficam de fora: o subagente tem contexto próprio.
     */
    private fun readLiveContexts(connection: Connection, sessionId: String? = null): Map<String, LiveContext> {
        val sql = if (sessionId == null) SELECT_LIVE_CONTEXTS_SQL else SELECT_LIVE_CONTEXT_BY_SESSION_SQL
        return connection.prepareStatement(sql).use { statement ->
            if (sessionId != null) {
                statement.setString(1, sessionId)
                statement.setString(2, sessionId)
            }
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getString("session_id"),
                            LiveContext(
                                cacheReadTokens = rows.getLong("cache_read_tokens"),
                                model = rows.getString("model")
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Tempo de trabalho de cada sessão na janela, por `session_id`.
     *
     * O corte entre turnos vai **ligado como parâmetro**, nunca escrito no SQL:
     * `TURN_GAP_CUTOFF_MILLIS` continua sendo o único dono do valor, e a consulta
     * só o aplica. Sessão sem intervalo dentro do corte não aparece no resultado.
     */
    private fun readActiveTimes(
        connection: Connection,
        profileId: String?,
        sinceEpochMillis: Long,
        gapCutoffMillis: Long = TURN_GAP_CUTOFF_MILLIS
    ): Map<String, Long> {
        return connection.prepareStatement(SELECT_SESSION_ACTIVE_TIME_SQL).use { statement ->
            statement.setLong(1, sinceEpochMillis)
            statement.setInt(2, if (profileId == null) 1 else 0)
            statement.setString(3, profileId)
            statement.setLong(4, gapCutoffMillis)
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(rows.getString("session_id"), rows.getLong("active_millis"))
                    }
                }
            }
        }
    }

    /**
     * Reagrega cada sessão somando apenas os turnos a partir de [sinceEpochMillis].
     *
     * A query agrupa por `(session_id, model)` porque uma sessão que trocou de
     * modelo no meio precisa ser precificada com a tarifa de cada trecho; as
     * linhas são dobradas por sessão aqui. Sessões sem turno na janela não
     * aparecem — é o recorte pedido, não uma perda de dado.
     */
    private fun readWindowedSessions(
        connection: Connection,
        profileId: String?,
        sinceEpochMillis: Long,
        liveContexts: Map<String, LiveContext>
    ): List<CliSessionSummary> {
        val accumulators = linkedMapOf<String, WindowedSessionAccumulator>()

        connection.prepareStatement(SELECT_SESSIONS_SINCE_SQL).use { statement ->
            statement.setLong(1, sinceEpochMillis)
            statement.setInt(2, if (profileId == null) 1 else 0)
            statement.setString(3, profileId)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val sessionId = rows.getString("session_id")
                    val filePath = rows.getString("file_path")
                    val accumulator = accumulators.getOrPut(sessionId) {
                        val liveContext = liveContexts[sessionId]
                        WindowedSessionAccumulator(
                            sessionId = sessionId,
                            filePath = filePath,
                            profileId = rows.getString("profile_id"),
                            cwd = rows.getString("cwd"),
                            gitBranch = rows.getString("git_branch"),
                            hostName = rows.getString("host_name"),
                            liveContextTokens = liveContext?.cacheReadTokens ?: 0L,
                            liveContextModel = liveContext?.model,
                            stale = !File(filePath).isFile
                        )
                    }
                    accumulator.addModelGroup(
                        model = rows.getString("model"),
                        turnCount = rows.getInt("turn_count"),
                        firstTsMillis = rows.getLong("first_ts"),
                        lastTsMillis = rows.getLong("last_ts"),
                        inputTokens = rows.getLong("input_tokens"),
                        outputTokens = rows.getLong("output_tokens"),
                        cacheReadTokens = rows.getLong("cache_read_tokens"),
                        cacheWrite5mTokens = rows.getLong("cache_write_5m_tokens"),
                        cacheWrite1hTokens = rows.getLong("cache_write_1h_tokens")
                    )
                }
            }
        }

        return accumulators.values
            .map { accumulator -> accumulator.toSummary() }
            .sortedByDescending { summary -> summary.lastTs }
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
        val parsed = parseTranscript(file, startOffset, json)

        connection.autoCommit = false
        try {
            val touchedSessions = insertCliTurns(connection, file.absolutePath, profileId, hostName, parsed.turns)
            for (sessionId in touchedSessions) {
                recomputeSession(
                    connection = connection,
                    sessionId = sessionId,
                    filePath = file.absolutePath,
                    profileId = profileId,
                    metadata = parsed.metadataBySession[sessionId]
                )
            }
            // Fora do laço acima de propósito: um arquivo reprocessado só porque
            // mudou de conta não traz turno novo, `touchedSessions` sai vazio e
            // `recomputeSession` nunca roda. Sem este carimbo a sessão fica com a
            // conta antiga — ou sem conta nenhuma, que foi o que aconteceu quando
            // a coluna nasceu.
            stampSessionProfile(connection, file.absolutePath, profileId)
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
            statement.setString(17, hostName)
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

    private fun readSummary(rows: ResultSet, liveContexts: Map<String, LiveContext>): CliSessionSummary {
        val filePath = rows.getString("file_path")
        val sessionId = rows.getString("session_id")
        val liveContext = liveContexts[sessionId]
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = filePath,
            profileId = rows.getString("profile_id"),
            cwd = rows.getString("cwd"),
            gitBranch = rows.getString("git_branch"),
            hostName = rows.getString("host_name"),
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
            liveContextTokens = liveContext?.cacheReadTokens ?: 0L,
            liveContextModel = liveContext?.model,
            stale = !File(filePath).isFile
        )
    }

    /** Último turno da thread principal de uma sessão, base do status de contexto. */
    private data class LiveContext(
        val cacheReadTokens: Long,
        val model: String?
    )

    /** Ver [readCachedTail]: veredito válido enquanto o par tamanho + data não mudar. */
    private data class CachedTail(
        val sizeBytes: Long,
        val lastModified: Long,
        val tail: CliSessionTail
    )

    companion object {
        /** Raiz do perfil padrão, usada quando nenhum registry de contas é fornecido. */
        fun defaultProjectRoot(): CliProjectRoot {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return CliProjectRoot(
                profileId = DEFAULT_ANTHROPIC_PROFILE_ID,
                directoryPath = File(homeDir, ".claude/projects").absolutePath
            )
        }

        /**
         * Nome da máquina local. As variáveis de ambiente cobrem Windows e Linux
         * sem tocar em DNS; `InetAddress` fica como último recurso.
         */
        private fun resolveHostName(): String? {
            val fromEnvironment = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
                ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
            if (fromEnvironment != null) {
                return fromEnvironment
            }
            return runCatching { java.net.InetAddress.getLocalHost().hostName }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
            return File(homeDir, ".usage-monitor/usage-history.db")
        }
    }
}
