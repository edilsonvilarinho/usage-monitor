package com.usagemonitor.data.export

import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.UsageExportFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Exporta somente o resumo exibido; custo USD não é inferido para o Codex local. */
object CodexCliUsageExporter {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun exportSessions(sessions: List<CodexCliSessionSummary>, format: UsageExportFormat): String {
        return when (format) {
            UsageExportFormat.CSV -> csv(sessions)
            UsageExportFormat.JSON -> json.encodeToString(sessions.map { session -> session.toDto() })
        }
    }

    private fun csv(sessions: List<CodexCliSessionSummary>): String {
        val header = listOf(
            "session_id", "project", "cwd", "git_branch", "first_ts", "last_ts", "model",
            "originator", "source", "raw_source", "thread_source", "cli_version", "turn_count", "response_count",
            "input_tokens", "cached_input_tokens", "cache_write_input_tokens", "output_tokens",
            "reasoning_output_tokens", "total_tokens"
        )
        val rows = sessions.map { session ->
            listOf(
                session.sessionId, session.projectName.orEmpty(), session.cwd.orEmpty(), session.gitBranch.orEmpty(),
                session.firstTs.toString(), session.lastTs.toString(), session.primaryModel.orEmpty(),
                session.originator.orEmpty(), session.source.name, session.rawSource.orEmpty(), session.threadSource.orEmpty(), session.cliVersion.orEmpty(),
                session.turnCount.toString(), session.responseCount.toString(), session.inputTokens.toString(),
                session.cachedInputTokens.toString(), session.cacheWriteInputTokens.toString(), session.outputTokens.toString(),
                session.reasoningOutputTokens.toString(), session.totalTokens.toString()
            )
        }
        return buildString {
            appendLine(header.joinToString(",") { cell -> csvCell(cell) })
            rows.forEach { row -> appendLine(row.joinToString(",") { cell -> csvCell(cell) }) }
        }
    }

    private fun csvCell(value: String): String {
        return if (value.any { char -> char == ',' || char == '"' || char == '\n' || char == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    @Serializable
    private data class SessionDto(
        @SerialName("sessionId") val sessionId: String,
        @SerialName("filePath") val filePath: String,
        @SerialName("cwd") val cwd: String? = null,
        @SerialName("gitBranch") val gitBranch: String? = null,
        @SerialName("firstTs") val firstTs: String,
        @SerialName("lastTs") val lastTs: String,
        @SerialName("primaryModel") val primaryModel: String? = null,
        @SerialName("originator") val originator: String? = null,
        @SerialName("source") val source: String,
        @SerialName("rawSource") val rawSource: String? = null,
        @SerialName("threadSource") val threadSource: String? = null,
        @SerialName("cliVersion") val cliVersion: String? = null,
        @SerialName("turnCount") val turnCount: Int,
        @SerialName("responseCount") val responseCount: Int,
        @SerialName("inputTokens") val inputTokens: Long,
        @SerialName("cachedInputTokens") val cachedInputTokens: Long,
        @SerialName("cacheWriteInputTokens") val cacheWriteInputTokens: Long,
        @SerialName("outputTokens") val outputTokens: Long,
        @SerialName("reasoningOutputTokens") val reasoningOutputTokens: Long,
        @SerialName("totalTokens") val totalTokens: Long
    )

    private fun CodexCliSessionSummary.toDto(): SessionDto {
        return SessionDto(
            sessionId, filePath, cwd, gitBranch, firstTs.toString(), lastTs.toString(), primaryModel,
            originator, source.name, rawSource, threadSource, cliVersion, turnCount, responseCount, inputTokens,
            cachedInputTokens, cacheWriteInputTokens, outputTokens, reasoningOutputTokens, totalTokens
        )
    }
}
