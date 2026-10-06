package com.usagemonitor.domain.entity

import kotlin.time.Instant

/** Classificação observada no campo `session_meta.source` do rollout. */
enum class CodexCliRolloutSource {
    CLI,
    EXEC,
    VSCODE,
    SUBAGENT,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): CodexCliRolloutSource {
            return when (raw?.trim()?.lowercase()) {
                "cli" -> CLI
                "exec" -> EXEC
                "vscode" -> VSCODE
                "subagent" -> SUBAGENT
                else -> UNKNOWN
            }
        }
    }
}

/** Delta de uma resposta, extraído somente de `token_usage_record.usage`. */
data class CodexCliUsageDelta(
    val inputTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val cacheWriteInputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val reasoningOutputTokens: Long = 0L,
    val totalTokens: Long = 0L
) {
    val billableInputTokens: Long
        get() = (inputTokens - cachedInputTokens).coerceAtLeast(0L)
}

/** Uma resposta observável de um turno do Codex CLI. */
data class CodexCliSessionTurn(
    val sessionId: String,
    val turnId: String,
    val responseId: String,
    val seq: Int,
    val ts: Instant,
    val model: String? = null,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val source: CodexCliRolloutSource = CodexCliRolloutSource.UNKNOWN,
    val rawSource: String? = null,
    val threadSource: String? = null,
    val usage: CodexCliUsageDelta = CodexCliUsageDelta(),
    /** Entrada que originou esta resposta (contexto do turno, saída de ferramenta ou mensagem); `null` = não lida (#381). */
    val requestTs: Instant? = null
) {
    /** Vazão desta resposta, de [requestTs] até o registro de uso; `null` quando não medida. */
    val throughput: OutputThroughput?
        get() = measuredThroughput(
            outputTokens = usage.outputTokens,
            requestMillis = requestTs?.toEpochMilliseconds(),
            endMillis = ts.toEpochMilliseconds()
        )
}

/** Agregado de uma sessão local, sem prompt, resposta ou conteúdo de ferramenta. */
data class CodexCliSessionSummary(
    val sessionId: String,
    val filePath: String,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val firstTs: Instant,
    val lastTs: Instant,
    val primaryModel: String? = null,
    val originator: String? = null,
    val source: CodexCliRolloutSource = CodexCliRolloutSource.UNKNOWN,
    val rawSource: String? = null,
    val threadSource: String? = null,
    val cliVersion: String? = null,
    val turnCount: Int = 0,
    val responseCount: Int = 0,
    val inputTokens: Long = 0L,
    val cachedInputTokens: Long = 0L,
    val cacheWriteInputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val reasoningOutputTokens: Long = 0L,
    val totalTokens: Long = 0L,
    val skippedLines: Int = 0,
    /** Vazão de saída das respostas medidas; `null` = nenhuma medida (#381). */
    val throughput: OutputThroughput? = null
) {
    val projectName: String?
        get() = cwd
            ?.trimEnd('/', '\\')
            ?.split('/', '\\')
            ?.lastOrNull()
            ?.takeIf { value -> value.isNotBlank() }
}

data class CodexCliSessionDetail(
    val summary: CodexCliSessionSummary,
    val turns: List<CodexCliSessionTurn>
)

data class CodexCliSessionIndexReport(
    val scannedFiles: Int = 0,
    val updatedFiles: Int = 0,
    val skippedLines: Int = 0,
    val unknownLines: Int = 0
)
