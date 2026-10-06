package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.combinedThroughput

/**
 * Uma linha do Resumo do Codex (#384): sessões agrupadas por um eixo.
 *
 * O índice do Codex agrega por sessão, não por resposta, então o eixo de modelo
 * é o **modelo principal da sessão** — e a tela diz isso. Repartir os tokens de
 * uma sessão entre modelos exigiria ler os turnos de cada uma, e estimar a
 * partilha seria derivar número que a fonte não deu.
 */
internal data class CodexSessionBucket(
    val label: String,
    val sessionCount: Int,
    val responseCount: Int,
    val totalTokens: Long,
    val cachedInputTokens: Long,
    val inputTokens: Long,
    val throughput: OutputThroughput?
) {
    val cacheRate: Double
        get() = if (inputTokens > 0L) cachedInputTokens.toDouble() / inputTokens else 0.0
}

/** Agrupa por [keyOf]; sessão sem chave cai em "—". Ordem total: tokens, depois rótulo. */
internal fun codexBucketsBy(
    sessions: List<CodexCliSessionSummary>,
    keyOf: (CodexCliSessionSummary) -> String?
): List<CodexSessionBucket> {
    return sessions
        .groupBy { session -> keyOf(session) ?: "—" }
        .map { (label, group) ->
            CodexSessionBucket(
                label = label,
                sessionCount = group.size,
                responseCount = group.sumOf { session -> session.responseCount },
                totalTokens = group.sumOf { session -> session.totalTokens },
                cachedInputTokens = group.sumOf { session -> session.cachedInputTokens },
                inputTokens = group.sumOf { session -> session.inputTokens },
                throughput = group.map { session -> session.throughput }.combinedThroughput()
            )
        }
        .sortedWith(compareByDescending<CodexSessionBucket> { bucket -> bucket.totalTokens }.thenBy { bucket -> bucket.label })
}
