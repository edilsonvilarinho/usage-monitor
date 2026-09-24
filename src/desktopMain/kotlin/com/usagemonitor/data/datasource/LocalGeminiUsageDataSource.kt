package com.usagemonitor.data.datasource

import com.usagemonitor.data.mapper.GeminiSessionLogParser
import com.usagemonitor.domain.repository.GeminiUsageException
import com.usagemonitor.domain.repository.GeminiUsageFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Lê só os campos de métrica dos JSONL do Gemini CLI em
 * `~/.gemini/tmp/<projeto>/chats/<sessão>.jsonl`.
 *
 * **Raiz ausente não é erro.** Sem `.gemini/tmp` o Gemini CLI nunca rodou nesta
 * máquina, e a resposta certa é o card vazio — não um banner a cada coleta de 10
 * minutos, que era o que a primeira versão fazia.
 *
 * **Arquivo antigo não é lido.** O JSONL é append-only: nenhum registro é mais
 * novo que o próprio arquivo, então um arquivo modificado antes do início da
 * janela mais longa (7 dias) não contribui. Os que contribuem ficam em cache por
 * caminho + tamanho + data de modificação, e a coleta seguinte só reparseia o que
 * mudou.
 */
class LocalGeminiUsageDataSource(
    private val sessionsDirectory: File = defaultSessionsDirectory(),
    private val nowProvider: () -> Instant = { Clock.System.now() },
    private val lookback: Duration = LOOKBACK
) : GeminiUsageDataSource {

    private data class FileKey(val path: String, val size: Long, val modifiedAt: Long)
    private data class ParsedFile(val recognized: Boolean, val sessionId: String?, val messages: List<GeminiMessageUsage>)

    private val parsedFiles = mutableMapOf<String, Pair<FileKey, ParsedFile>>()

    override suspend fun loadSessions(): List<GeminiSessionUsage> = withContext(Dispatchers.IO) {
        if (!sessionsDirectory.isDirectory) {
            parsedFiles.clear()
            return@withContext emptyList()
        }

        val oldestRelevant = (nowProvider() - lookback).toEpochMilliseconds()
        val sessionFiles = sessionFilesIn(sessionsDirectory)
            .filter { file -> file.lastModified() >= oldestRelevant }
        parsedFiles.keys.retainAll(sessionFiles.map(File::getAbsolutePath).toSet())

        var recognizedFileCount = 0
        val messagesBySession = linkedMapOf<String, LinkedHashMap<String, GeminiMessageUsage>>()

        sessionFiles.forEach { file ->
            val parsed = parseCached(file) ?: return@forEach
            if (!parsed.recognized) return@forEach

            recognizedFileCount += 1
            val sessionId = parsed.sessionId?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
            val sessionMessages = messagesBySession.getOrPut(sessionId) { linkedMapOf() }
            parsed.messages.forEach { message -> sessionMessages[message.messageId] = message }
        }

        // Arquivos recentes que existem e nenhum deles é legível: o formato mudou,
        // e isso é diferente de "não houve uso".
        if (sessionFiles.isNotEmpty() && recognizedFileCount == 0) {
            throw GeminiUsageException(GeminiUsageFailureKind.SESSION_HISTORY_UNREADABLE)
        }

        messagesBySession.map { (sessionId, messages) ->
            GeminiSessionUsage(sessionId = sessionId, messages = messages.values.toList())
        }
    }

    /** Arquivo que some ou fica ilegível entre a listagem e a leitura é pulado. */
    private fun parseCached(file: File): ParsedFile? {
        val key = FileKey(file.absolutePath, file.length(), file.lastModified())
        parsedFiles[key.path]?.let { (cachedKey, cached) -> if (cachedKey == key) return cached }

        val parsed = runCatching {
            file.useLines { lines -> GeminiSessionLogParser.parse(lines.asIterable()) }
        }.getOrNull() ?: return null
        val result = ParsedFile(
            recognized = parsed.recognizedRecords > 0,
            sessionId = parsed.sessionId,
            messages = parsed.messages
        )
        parsedFiles[key.path] = key to result
        return result
    }

    private fun sessionFilesIn(root: File): List<File> =
        root.listFiles().orEmpty()
            .filter(File::isDirectory)
            .flatMap { project -> File(project, CHATS_DIRECTORY).listFiles().orEmpty().toList() }
            .filter { file -> file.isFile && file.extension == "jsonl" }

    private companion object {
        const val CHATS_DIRECTORY = "chats"

        /** A janela mais longa do card; arquivo mais velho que isso não contribui. */
        val LOOKBACK: Duration = 7.days

        fun defaultSessionsDirectory(): File {
            val homeDirectory = System.getProperty("user.home")
                ?: throw IllegalStateException("Gemini CLI home directory is unavailable")
            return File(homeDirectory, ".gemini/tmp")
        }
    }
}
