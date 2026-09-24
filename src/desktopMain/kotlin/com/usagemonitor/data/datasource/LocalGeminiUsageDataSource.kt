package com.usagemonitor.data.datasource

import com.usagemonitor.data.mapper.GeminiSessionLogParser
import com.usagemonitor.domain.repository.GeminiUsageException
import com.usagemonitor.domain.repository.GeminiUsageFailureKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Leitura somente dos campos de métrica dos JSONL locais do Gemini CLI. */
class LocalGeminiUsageDataSource(
    private val sessionsDirectory: File = defaultSessionsDirectory()
) : GeminiUsageDataSource {

    override suspend fun loadSessions(): List<GeminiSessionUsage> = withContext(Dispatchers.IO) {
        if (!sessionsDirectory.isDirectory) {
            throw GeminiUsageException(GeminiUsageFailureKind.SESSION_DIRECTORY_MISSING)
        }

        val sessionFiles = sessionsDirectory.walkTopDown()
            .filter { file -> file.isFile && file.name.startsWith(SESSION_FILE_PREFIX) && file.extension == "jsonl" }
            .toList()
        var recognizedFileCount = 0
        val messagesBySession = linkedMapOf<String, LinkedHashMap<String, GeminiMessageUsage>>()

        sessionFiles.forEach { file ->
            val parsed = file.useLines { lines -> GeminiSessionLogParser.parse(lines.asIterable()) }
            if (parsed.recognizedRecords == 0) return@forEach

            recognizedFileCount += 1
            val sessionId = parsed.sessionId?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
            val sessionMessages = messagesBySession.getOrPut(sessionId) { linkedMapOf() }
            parsed.messages.forEach { message -> sessionMessages[message.messageId] = message }
        }

        if (sessionFiles.isNotEmpty() && recognizedFileCount == 0) {
            throw GeminiUsageException(GeminiUsageFailureKind.SESSION_HISTORY_UNREADABLE)
        }

        messagesBySession.map { (sessionId, messages) ->
            GeminiSessionUsage(sessionId = sessionId, messages = messages.values.toList())
        }
    }

    private companion object {
        const val SESSION_FILE_PREFIX = "session-"

        fun defaultSessionsDirectory(): File {
            val homeDirectory = System.getProperty("user.home")
                ?: throw IllegalStateException("Gemini CLI home directory is unavailable")
            return File(homeDirectory, ".gemini/tmp")
        }
    }
}
