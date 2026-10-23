package com.usagemonitor.domain.entity

/**
 * Base da porcentagem de cada modelo numa sessão (#445).
 *
 * Custo a preço de tabela é a base preferida: o mesmo volume de tokens pesa
 * muito mais num modelo caro, e é esse peso que aparece na cota. Tokens é a
 * base de quem não tem custo nenhum calculado — o Codex, sem tarifa no app, ou
 * uma sessão só com modelos fora da tabela.
 */
enum class ModelShareBasis {
    COST,
    TOKENS
}

/** Um modelo da sessão e a fatia dele; `null` = sem tarifa numa base de custo. */
data class ModelShare(
    val bucket: CliUsageBucket,
    val share: Double?
)

/**
 * Os modelos que uma sessão usou, cada um com a sua fatia.
 *
 * A soma das fatias medidas dá 1 (salvo arredondamento). Modelo sem tarifa numa
 * base de custo fica com fatia `null`, e não zero: custo não medido não é custo
 * zero, a mesma regra do `+` no valor.
 */
data class SessionModelMix(
    val basis: ModelShareBasis = ModelShareBasis.COST,
    val shares: List<ModelShare> = emptyList()
) {
    val isEmpty: Boolean
        get() = shares.isEmpty()

    /** O modelo que mais pesou, ou `null` sem nenhum modelo conhecido. */
    val leadingModel: String?
        get() = shares.firstNotNullOfOrNull { share -> share.bucket.label }
}

/**
 * Fatias de cada modelo, já em ordem total: a da maior fatia primeiro, o rótulo
 * desempata — duas leituras iguais dão a mesma lista, ou a tela recompõe a cada
 * tique do laço ao vivo.
 */
fun sessionModelMixOf(buckets: List<CliUsageBucket>): SessionModelMix {
    if (buckets.isEmpty()) {
        return SessionModelMix()
    }
    val totalCost = buckets.sumOf { bucket -> bucket.costMicros }
    if (totalCost > 0L) {
        val shares = buckets.rankedByCost().map { bucket ->
            // Um balde é um modelo só: ou tem tarifa inteira, ou não tem nenhuma.
            val share = if (bucket.isCostComplete) {
                bucket.costMicros.toDouble() / totalCost.toDouble()
            } else {
                null
            }
            ModelShare(bucket = bucket, share = share)
        }
        return SessionModelMix(basis = ModelShareBasis.COST, shares = shares)
    }
    val totalTokens = buckets.sumOf { bucket -> bucket.totalTokens }
    val shares = buckets
        .sortedWith(
            compareByDescending<CliUsageBucket> { bucket -> bucket.totalTokens }
                .thenBy { bucket -> bucket.label ?: "" }
        )
        .map { bucket ->
            val share = if (totalTokens > 0L) bucket.totalTokens.toDouble() / totalTokens.toDouble() else 0.0
            ModelShare(bucket = bucket, share = share)
        }
    return SessionModelMix(basis = ModelShareBasis.TOKENS, shares = shares)
}

/**
 * Corte por modelo de cada sessão, a partir das linhas `(sessão, modelo)` do
 * índice. Passa pelo mesmo [toUsageBreakdown] da aba Resumo: o custo de cada
 * modelo é recalculado dos tokens com a tarifa dele, e os dois nunca divergem.
 */
fun Iterable<CliUsageGroupRow>.modelBreakdownBySession(): Map<String, List<CliUsageBucket>> {
    return filter { row -> row.sessionId != null }
        .groupBy { row -> row.sessionId.orEmpty() }
        .mapValues { entry -> entry.value.toUsageBreakdown().byModel }
}

/** Corte por modelo de uma sessão a partir dos turnos dela (detalhe). Subagente entra: o gasto é real. */
fun Iterable<CliSessionTurn>.toModelBreakdown(): List<CliUsageBucket> {
    return map { turn ->
        CliUsageGroupRow(
            sessionId = turn.sessionId,
            model = turn.model,
            turnCount = 1,
            inputTokens = turn.inputTokens,
            outputTokens = turn.outputTokens,
            cacheReadTokens = turn.cacheReadTokens,
            cacheWrite5mTokens = turn.cacheWrite5mTokens,
            cacheWrite1hTokens = turn.cacheWrite1hTokens
        )
    }.toUsageBreakdown().byModel
}

/**
 * Corte por modelo de uma sessão do Codex, nas mesmas séries que a TUI já usa:
 * entrada cobrável, saída, `cached` como cache lido e `cache_write` como cache
 * gravado. Turno é o `turn_id` distinto, a mesma contagem do resumo da sessão.
 * Sem tarifa no app, o custo sai zero com os turnos em `unpricedTurnCount`, e a
 * fatia cai na base de tokens.
 */
fun Iterable<CodexCliSessionTurn>.toCodexModelBreakdown(): List<CliUsageBucket> {
    return groupBy { turn -> turn.model }
        .map { (model, turns) ->
            CliUsageGroupRow(
                sessionId = turns.first().sessionId,
                model = model,
                turnCount = turns.map { turn -> turn.turnId }.distinct().size,
                inputTokens = turns.sumOf { turn -> turn.usage.billableInputTokens },
                outputTokens = turns.sumOf { turn -> turn.usage.outputTokens },
                cacheReadTokens = turns.sumOf { turn -> turn.usage.cachedInputTokens },
                cacheWrite5mTokens = turns.sumOf { turn -> turn.usage.cacheWriteInputTokens }
            )
        }
        .toUsageBreakdown()
        .byModel
}
