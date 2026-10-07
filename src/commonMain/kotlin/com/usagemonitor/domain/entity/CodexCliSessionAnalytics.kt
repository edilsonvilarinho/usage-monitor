package com.usagemonitor.domain.entity

/**
 * Séries por resposta de uma sessão do Codex CLI (#393), para os gráficos do
 * detalhe. Uma posição por resposta, na ordem da sessão.
 *
 * O que o rollout permite e nada além (medido em 2026-10-06 no rollout real):
 * `usage` de `token_usage_record` é **por resposta**; `input_tokens` é o prompt
 * inteiro que o modelo leu, com o cache dentro (cresce de resposta em resposta
 * e cai na compactação), e `total_tokens = input_tokens + output_tokens` — o
 * raciocínio está **dentro** da saída. Custo não entra: o app não tem tarifa
 * de modelo do Codex, e custo desconhecido não é zero.
 */
data class CodexCliSessionAnalytics(
    /** Entrada de cada resposta: o tamanho do contexto que o modelo leu. */
    val contextPerTurn: List<Long> = emptyList(),
    /** Parte da entrada servida do cache, 0–1; `null` com entrada zero. */
    val cacheHitPerTurn: List<Double?> = emptyList(),
    /** Saída de cada resposta, raciocínio incluído. */
    val outputPerTurn: List<Long> = emptyList(),
    /** Raciocínio de cada resposta (parte da saída). */
    val reasoningPerTurn: List<Long> = emptyList(),
    /** Tokens de saída por segundo; `null` quando a resposta não foi medida. */
    val throughputPerTurn: List<Double?> = emptyList()
)

/** Ordem total: `seq`, depois carimbo e id — duas leituras iguais dão séries iguais. */
fun codexCliSessionAnalyticsOf(turns: List<CodexCliSessionTurn>): CodexCliSessionAnalytics {
    val ordered = turns.sortedWith(compareBy({ turn -> turn.seq }, { turn -> turn.ts }, { turn -> turn.responseId }))
    return CodexCliSessionAnalytics(
        contextPerTurn = ordered.map { turn -> turn.usage.inputTokens },
        cacheHitPerTurn = ordered.map { turn ->
            val input = turn.usage.inputTokens
            if (input > 0L) turn.usage.cachedInputTokens.coerceIn(0L, input).toDouble() / input else null
        },
        outputPerTurn = ordered.map { turn -> turn.usage.outputTokens },
        reasoningPerTurn = ordered.map { turn -> turn.usage.reasoningOutputTokens },
        throughputPerTurn = ordered.map { turn -> turn.throughput?.tokensPerSecond }
    )
}
