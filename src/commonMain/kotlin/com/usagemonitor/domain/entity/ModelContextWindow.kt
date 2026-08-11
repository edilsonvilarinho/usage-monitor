package com.usagemonitor.domain.entity

/**
 * Janela de contexto por modelo, em tokens.
 *
 * Só constam modelos cuja janela é conhecida com certeza. Modelo ausente devolve
 * `null` e a saturação da sessão fica indisponível — nunca se assume um valor.
 */
object ModelContextWindowTable {

    private const val ONE_MILLION_TOKENS = 1_000_000L
    private const val TWO_HUNDRED_THOUSAND_TOKENS = 200_000L

    private val entries: List<Pair<String, Long>> = listOf(
        "claude-opus-5" to ONE_MILLION_TOKENS,
        "claude-opus-4-8" to ONE_MILLION_TOKENS,
        "claude-opus-4-7" to ONE_MILLION_TOKENS,
        "claude-opus-4-6" to ONE_MILLION_TOKENS,
        "claude-sonnet-5" to ONE_MILLION_TOKENS,
        "claude-sonnet-4-6" to ONE_MILLION_TOKENS,
        "claude-fable-5" to ONE_MILLION_TOKENS,
        "claude-mythos-5" to ONE_MILLION_TOKENS,
        "claude-haiku-4-5" to TWO_HUNDRED_THOUSAND_TOKENS
    ).sortedByDescending { it.first.length }

    fun forModel(modelId: String?): Long? {
        val normalized = normalizeModelId(modelId) ?: return null

        for ((prefix, window) in entries) {
            if (matchesModelPrefix(normalized, prefix)) {
                return window
            }
        }
        return null
    }
}

/**
 * Limiares do status da sessão, calibrados contra 70 sessões reais.
 *
 * O legado usava `custo da próxima msg > $0,05` **ou** `cacheRead > 150.000`.
 * Os dois problemas: o corte de tokens era fixo, calibrado para janelas de
 * 200K — num modelo de 1M dispara a 15% da janela — e o conjunto disparava em
 * 33 das 70 sessões medidas. Um alerta que aparece em quase metade dos casos
 * deixa de ser informação.
 *
 * A fração da janela substitui o corte fixo porque é relativa ao modelo. O
 * custo entra em paralelo: numa janela pequena a fração pode ser modesta e a
 * mensagem ainda assim sair cara.
 *
 * Distribuição medida: mediana 14% da janela, p90 36%, máximo 69%; custo da
 * próxima mensagem mediana $0,046, p90 $0,124, máximo $0,347.
 */
object CliSessionHealthThresholds {
    /** Saturada: 3 das 70 sessões medidas. */
    const val SATURATED_WINDOW_FRACTION = 0.60
    const val SATURATED_NEXT_COST_MICROS = 250_000L

    /** Atenção: 7 das 70 sessões medidas, saturadas incluídas. */
    const val ATTENTION_WINDOW_FRACTION = 0.40
    const val ATTENTION_NEXT_COST_MICROS = 150_000L
}
