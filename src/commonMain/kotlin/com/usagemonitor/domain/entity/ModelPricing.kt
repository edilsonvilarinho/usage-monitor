package com.usagemonitor.domain.entity

/** Um dólar em micros. Custos são armazenados em micros para evitar `Double` em agregação. */
const val MICROS_PER_USD = 1_000_000L

/** Divisor usado ao converter preço por milhão de tokens em custo de N tokens. */
private const val TOKENS_PER_MILLION = 1_000_000L

/** Divisor padrão do cache read (0,1x a tarifa de input). Opus/Sonnet 5.5 e Fable/Mythos 5.1 têm divisor próprio. */
const val DEFAULT_CACHE_READ_DIVISOR = 10L

/** Limite de prompt longo em tokens. Acima dele (`>` estrito) vale a faixa de prompt longo, por requisição. */
const val LONG_PROMPT_THRESHOLD_TOKENS = 100_000L

/** Contagem de tokens de um grupo ou turno, por tipo. */
data class UsageTokenCounts(
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
) {
    /** Tokens de prompt: tudo que entra na requisição. O output fica fora. */
    val promptTokens: Long
        get() = inputTokens + cacheReadTokens + cacheWrite5mTokens + cacheWrite1hTokens
}

/**
 * Parte do turno que cai acima do limite de prompt longo. Inteira quando o prompt do turno
 * passa de [LONG_PROMPT_THRESHOLD_TOKENS]; zero caso contrário. A decisão é por turno, porque
 * a faixa vale por requisição.
 */
fun longPromptPartOfTurn(turn: UsageTokenCounts): UsageTokenCounts {
    return if (turn.promptTokens > LONG_PROMPT_THRESHOLD_TOKENS) turn else UsageTokenCounts()
}

/** Parte curta de um grupo: total menos a parte longa, componente a componente. */
private fun shortPartOf(total: UsageTokenCounts, longPrompt: UsageTokenCounts): UsageTokenCounts {
    return UsageTokenCounts(
        inputTokens = total.inputTokens - longPrompt.inputTokens,
        outputTokens = total.outputTokens - longPrompt.outputTokens,
        cacheReadTokens = total.cacheReadTokens - longPrompt.cacheReadTokens,
        cacheWrite5mTokens = total.cacheWrite5mTokens - longPrompt.cacheWrite5mTokens,
        cacheWrite1hTokens = total.cacheWrite1hTokens - longPrompt.cacheWrite1hTokens
    )
}

/** Faixa de prompt longo: substitui a tarifa base quando o prompt da requisição passa de [thresholdTokens]. */
data class LongPromptPricing(
    val thresholdTokens: Long,
    val pricing: ModelPricing
)

/**
 * Preço de tabela de um modelo, em **micros de USD por milhão de tokens**.
 *
 * Só as duas tarifas base são declaradas: os três preços de cache derivam do input
 * (cache read = input / [cacheReadDivisor], cache write 5m 1,25x, cache write 1h 2x). Toda a
 * aritmética é inteira — nenhum valor da tabela sofre truncamento nessas divisões.
 */
data class ModelPricing(
    val inputMicrosPerMillion: Long,
    val outputMicrosPerMillion: Long,
    val cacheReadDivisor: Long = DEFAULT_CACHE_READ_DIVISOR,
    val longPrompt: LongPromptPricing? = null
) {
    init {
        require(inputMicrosPerMillion >= 0L) { "O preço de input não pode ser negativo." }
        require(outputMicrosPerMillion >= 0L) { "O preço de output não pode ser negativo." }
        require(cacheReadDivisor > 0L) { "O divisor do cache read deve ser positivo." }
    }

    /** Cache read: input dividido por [cacheReadDivisor]. */
    val cacheReadMicrosPerMillion: Long
        get() = inputMicrosPerMillion / cacheReadDivisor

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

    /**
     * Economia do cache: o que [cacheReadTokens] teriam custado como input menos o
     * que custaram como cache read.
     */
    fun cacheSavingsMicros(cacheReadTokens: Long): Long {
        return cacheReadTokens * (inputMicrosPerMillion - cacheReadMicrosPerMillion) / TOKENS_PER_MILLION
    }

    /**
     * Tarifa aplicável a uma requisição com [promptTokens] tokens de prompt. A faixa de
     * prompt longo só vale acima do limite; sem faixa, devolve a própria tarifa base.
     */
    fun forPrompt(promptTokens: Long): ModelPricing {
        val tier = longPrompt ?: return this
        return if (promptTokens > tier.thresholdTokens) tier.pricing else this
    }

    /**
     * Custo de um turno com a faixa escolhida pelo prompt do próprio turno (input + cache
     * read + cache write 5m e 1h). O output fica fora do prompt.
     */
    fun turnCostMicros(
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): Long {
        val promptTokens = inputTokens + cacheReadTokens + cacheWrite5mTokens + cacheWrite1hTokens
        return forPrompt(promptTokens).costMicros(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )
    }

    /** Economia do cache do turno com a faixa escolhida pelo prompt do próprio turno. */
    fun turnCacheSavingsMicros(
        inputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): Long {
        val promptTokens = inputTokens + cacheReadTokens + cacheWrite5mTokens + cacheWrite1hTokens
        return forPrompt(promptTokens).cacheSavingsMicros(cacheReadTokens)
    }

    /**
     * Custo de um grupo somado por tipo de turno. Sem faixa de prompt longo no modelo, o total
     * é precificado direto e a parte longa é ignorada. Com faixa, a parte curta usa a tarifa
     * base e a longa, a faixa; `null` quando há faixa e [longPrompt] não foi medido.
     */
    fun groupCostMicros(total: UsageTokenCounts, longPrompt: UsageTokenCounts?): Long? {
        val tier = this.longPrompt ?: return costMicros(
            inputTokens = total.inputTokens,
            outputTokens = total.outputTokens,
            cacheReadTokens = total.cacheReadTokens,
            cacheWrite5mTokens = total.cacheWrite5mTokens,
            cacheWrite1hTokens = total.cacheWrite1hTokens
        )
        if (longPrompt == null) {
            return null
        }
        val short = shortPartOf(total, longPrompt)
        return costMicros(
            inputTokens = short.inputTokens,
            outputTokens = short.outputTokens,
            cacheReadTokens = short.cacheReadTokens,
            cacheWrite5mTokens = short.cacheWrite5mTokens,
            cacheWrite1hTokens = short.cacheWrite1hTokens
        ) + tier.pricing.costMicros(
            inputTokens = longPrompt.inputTokens,
            outputTokens = longPrompt.outputTokens,
            cacheReadTokens = longPrompt.cacheReadTokens,
            cacheWrite5mTokens = longPrompt.cacheWrite5mTokens,
            cacheWrite1hTokens = longPrompt.cacheWrite1hTokens
        )
    }

    /**
     * Economia de cache de um grupo, com a mesma divisão de [groupCostMicros]: a parte curta
     * pela tarifa base e a longa pela faixa. `null` pelo mesmo motivo.
     */
    fun groupCacheSavingsMicros(total: UsageTokenCounts, longPrompt: UsageTokenCounts?): Long? {
        val tier = this.longPrompt ?: return cacheSavingsMicros(total.cacheReadTokens)
        if (longPrompt == null) {
            return null
        }
        val short = shortPartOf(total, longPrompt)
        return cacheSavingsMicros(short.cacheReadTokens) +
            tier.pricing.cacheSavingsMicros(longPrompt.cacheReadTokens)
    }

    companion object {
        /**
         * Constrói um preço a partir de valores em USD por milhão de tokens. A faixa de prompt
         * longo exige `longPromptInput` e `longPromptOutput` juntos, ou nenhum dos dois.
         */
        fun ofUsdPerMillion(
            input: Double,
            output: Double,
            cacheReadDivisor: Long = DEFAULT_CACHE_READ_DIVISOR,
            longPromptInput: Double? = null,
            longPromptOutput: Double? = null
        ): ModelPricing {
            require((longPromptInput == null) == (longPromptOutput == null)) {
                "A faixa de prompt longo exige input e output juntos, ou nenhum dos dois."
            }
            val tier = if (longPromptInput == null || longPromptOutput == null) {
                null
            } else {
                LongPromptPricing(
                    thresholdTokens = LONG_PROMPT_THRESHOLD_TOKENS,
                    pricing = ofUsdPerMillion(input = longPromptInput, output = longPromptOutput)
                )
            }
            return ModelPricing(
                inputMicrosPerMillion = (input * MICROS_PER_USD).toLong(),
                outputMicrosPerMillion = (output * MICROS_PER_USD).toLong(),
                cacheReadDivisor = cacheReadDivisor,
                longPrompt = tier
            )
        }
    }
}
