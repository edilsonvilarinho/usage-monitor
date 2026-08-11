package com.usagemonitor.domain.entity

/** Um dólar em micros. Custos são armazenados em micros para evitar `Double` em agregação. */
const val MICROS_PER_USD = 1_000_000L

/** Divisor usado ao converter preço por milhão de tokens em custo de N tokens. */
private const val TOKENS_PER_MILLION = 1_000_000L

/**
 * Preço de tabela de um modelo, em **micros de USD por milhão de tokens**.
 *
 * Só as duas tarifas base são declaradas: os três preços de cache derivam do input
 * (cache read 0,1x, cache write 5m 1,25x, cache write 1h 2x). Toda a aritmética é
 * inteira — nenhum valor da tabela sofre truncamento nessas divisões.
 */
data class ModelPricing(
    val inputMicrosPerMillion: Long,
    val outputMicrosPerMillion: Long
) {
    init {
        require(inputMicrosPerMillion >= 0L) { "O preço de input não pode ser negativo." }
        require(outputMicrosPerMillion >= 0L) { "O preço de output não pode ser negativo." }
    }

    /** Cache read: 0,1x a tarifa de input. */
    val cacheReadMicrosPerMillion: Long
        get() = inputMicrosPerMillion / 10L

    /** Cache write com TTL de 5 minutos: 1,25x a tarifa de input. */
    val cacheWrite5mMicrosPerMillion: Long
        get() = inputMicrosPerMillion * 5L / 4L

    /** Cache write com TTL de 1 hora: 2x a tarifa de input. */
    val cacheWrite1hMicrosPerMillion: Long
        get() = inputMicrosPerMillion * 2L

    /**
     * Custo de um turno em micros de USD. Os produtos são somados antes da divisão
     * única para não acumular erro de truncamento por componente.
     */
    fun costMicros(
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): Long {
        val weighted = inputTokens * inputMicrosPerMillion +
            outputTokens * outputMicrosPerMillion +
            cacheReadTokens * cacheReadMicrosPerMillion +
            cacheWrite5mTokens * cacheWrite5mMicrosPerMillion +
            cacheWrite1hTokens * cacheWrite1hMicrosPerMillion
        return weighted / TOKENS_PER_MILLION
    }

    companion object {
        /** Constrói um preço a partir de valores em USD por milhão de tokens. */
        fun ofUsdPerMillion(input: Double, output: Double): ModelPricing {
            return ModelPricing(
                inputMicrosPerMillion = (input * MICROS_PER_USD).toLong(),
                outputMicrosPerMillion = (output * MICROS_PER_USD).toLong()
            )
        }
    }
}
