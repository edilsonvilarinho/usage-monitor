package com.usagemonitor.domain.entity

/**
 * Estado do contexto vivo de uma sessão: quanto da janela do modelo já está
 * ocupado e quanto custa mandar a próxima mensagem.
 *
 * Vive separado de [CliSessionAnalytics] porque a lista precisa do mesmo
 * veredito sem carregar todos os turnos da sessão. Um status que discorda de si
 * mesmo entre a lista e o detalhe é pior que status nenhum, então os dois
 * caminhos passam por aqui.
 */
data class CliSessionContextStatus(
    /** `null` quando o turno da thread principal não foi medido — nunca se assume zero. */
    val liveContextTokens: Long? = null,
    /** `null` quando a janela do modelo não é conhecida ou o contexto não foi medido. */
    val contextSaturation: Double? = null,
    val nextInteractionCostMicros: Long = 0L
) {
    /**
     * Quanto da janela já está ocupado e quanto custa continuar, avaliados em
     * paralelo: numa janela pequena a fração pode ser modesta e a mensagem ainda
     * assim sair cara.
     */
    val health: CliSessionHealth
        get() {
            val saturation = contextSaturation ?: 0.0
            return when {
                saturation >= CliSessionHealthThresholds.SATURATED_WINDOW_FRACTION ||
                    nextInteractionCostMicros >= CliSessionHealthThresholds.SATURATED_NEXT_COST_MICROS ->
                    CliSessionHealth.SATURATED

                saturation >= CliSessionHealthThresholds.ATTENTION_WINDOW_FRACTION ||
                    nextInteractionCostMicros >= CliSessionHealthThresholds.ATTENTION_NEXT_COST_MICROS ->
                    CliSessionHealth.ATTENTION

                else -> CliSessionHealth.HEALTHY
            }
        }
}

/**
 * Deriva o status a partir do contexto vivo — o `cache_read` do último turno da
 * thread principal, que é o volume reenviado na próxima mensagem.
 *
 * [liveContextTokens] `null` é "não medido": sem saturação e sem custo estimado.
 * [windowModel] é o modelo predominante da sessão e define a janela de contexto.
 * [lastTurnModel] é o modelo do último turno e define a tarifa de cache read:
 * é ele que vai reenviar o contexto. Numa sessão que não trocou de modelo os
 * dois coincidem.
 */
fun computeContextStatus(
    liveContextTokens: Long?,
    windowModel: String?,
    lastTurnModel: String?
): CliSessionContextStatus {
    val contextWindow = ModelContextWindowTable.forModel(windowModel)?.takeIf { window -> window > 0L }
    val pricing = ModelPricingTable.forModel(lastTurnModel)
    val saturation = if (liveContextTokens == null || contextWindow == null) {
        null
    } else {
        liveContextTokens.toDouble() / contextWindow.toDouble()
    }
    val nextCostMicros = if (liveContextTokens == null || pricing == null) {
        0L
    } else {
        liveContextTokens * pricing.forPrompt(liveContextTokens).cacheReadMicrosPerMillion / MICROS_PER_USD
    }

    return CliSessionContextStatus(
        liveContextTokens = liveContextTokens,
        contextSaturation = saturation,
        nextInteractionCostMicros = nextCostMicros
    )
}
