package com.usagemonitor.domain.entity

/**
 * Vazão de saída medida: tokens de saída e o tempo que levaram, do pedido à
 * última linha da resposta (#381).
 *
 * É vazão **ponta a ponta** — inclui a espera pelo primeiro token —, não a
 * velocidade de decodificação do modelo. Guardar a soma dos dois termos, e não
 * a razão, deixa juntar sessões e modelos sem média de médias: a vazão de um
 * conjunto é o total de tokens sobre o total de tempo.
 *
 * Ausência é `null` em quem carrega o valor: turno sem pedido conhecido não
 * foi medido, e zero diria que foi medido e não produziu nada.
 */
data class OutputThroughput(
    val outputTokens: Long,
    val generationMillis: Long
) {
    /** Tokens por segundo; `null` quando não há tempo medido. */
    val tokensPerSecond: Double?
        get() = if (generationMillis > 0L) outputTokens * MILLIS_PER_SECOND / generationMillis else null

    operator fun plus(other: OutputThroughput): OutputThroughput {
        return OutputThroughput(
            outputTokens = outputTokens + other.outputTokens,
            generationMillis = generationMillis + other.generationMillis
        )
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
    }
}

/** Vazão do conjunto (total sobre total); `null` quando nenhum item foi medido. */
fun Iterable<OutputThroughput?>.combinedThroughput(): OutputThroughput? {
    var total: OutputThroughput? = null
    for (item in this) {
        if (item == null) {
            continue
        }
        val current = total
        total = if (current == null) item else current + item
    }
    return total
}
