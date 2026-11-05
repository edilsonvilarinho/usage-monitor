package com.usagemonitor.domain.entity

/**
 * Tabela de preços por modelo do Claude Code.
 *
 * O match é por prefixo com fronteira: o identificador é o prefixo exato ou o prefixo seguido de
 * `-` e de exatamente oito dígitos (sufixo de data, ex.: `claude-haiku-4-5-20251001`). Assim
 * `claude-opus-5-6` não cai em `claude-opus-5`, e `claude-opus-5-5-beta` não casa com nada.
 * Modelo desconhecido devolve `null` — a UI marca o custo como indisponível em vez de aplicar
 * silenciosamente um preço errado.
 */
object ModelPricingTable {

    private val OPUS = ModelPricing.ofUsdPerMillion(input = 5.00, output = 25.00)

    // Opus 5.5: tarifa própria e cache read a 1/20 do input (0,20 por milhão).
    private val OPUS_5_5 = ModelPricing.ofUsdPerMillion(input = 4.00, output = 20.00, cacheReadDivisor = 20L)

    // Sonnet 5 tem tarifa própria: 2/10 por milhão. Os 3/15 são do 4.6 e do 4.5 — uma
    // constante só para os três superestimava toda sessão em Sonnet 5 por um fator
    // exato de 3/2, porque os cinco componentes derivam do input e a razão
    // output/input é 5x nos dois preços.
    private val SONNET_5 = ModelPricing.ofUsdPerMillion(input = 2.00, output = 10.00)
    private val SONNET_5_5 = ModelPricing.ofUsdPerMillion(input = 2.00, output = 10.00, cacheReadDivisor = 20L)
    private val SONNET = ModelPricing.ofUsdPerMillion(input = 3.00, output = 15.00)

    // Haiku 5.5: prompt acima de 100 mil tokens cai na faixa longa (0,50 / 2,50 por milhão).
    private val HAIKU_5_5 = ModelPricing.ofUsdPerMillion(input = 0.10, output = 0.50, longPromptInput = 0.50, longPromptOutput = 2.50)
    private val HAIKU = ModelPricing.ofUsdPerMillion(input = 1.00, output = 5.00)
    private val FABLE = ModelPricing.ofUsdPerMillion(input = 10.00, output = 50.00)

    // Fable/Mythos 5.1: cache read a 1/40 do input (0,25 por milhão).
    private val FABLE_5_1 = ModelPricing.ofUsdPerMillion(input = 10.00, output = 50.00, cacheReadDivisor = 40L)

    /**
     * `<synthetic>` é o marcador que o Claude Code usa em mensagens que ele
     * próprio injeta no transcript (avisos, conteúdo local) — não houve chamada
     * de API e não há cobrança. Preço zero conhecido, não preço desconhecido:
     * tratá-lo como desconhecido marcaria sessões inteiras como custo incompleto.
     */
    private val SYNTHETIC = ModelPricing.ofUsdPerMillion(input = 0.00, output = 0.00)

    // Ordenado por prefixo mais longo primeiro: torna o match determinístico
    // caso um identificador futuro seja prefixo de outro.
    private val entries: List<Pair<String, ModelPricing>> = listOf(
        "claude-opus-5-5" to OPUS_5_5,
        "claude-opus-5" to OPUS,
        "claude-opus-4-8" to OPUS,
        "claude-opus-4-7" to OPUS,
        "claude-opus-4-6" to OPUS,
        "claude-opus-4-5" to OPUS,
        "claude-sonnet-5-5" to SONNET_5_5,
        "claude-sonnet-5" to SONNET_5,
        "claude-sonnet-4-6" to SONNET,
        "claude-sonnet-4-5" to SONNET,
        "claude-haiku-5-5" to HAIKU_5_5,
        "claude-haiku-4-5" to HAIKU,
        "claude-fable-5-1" to FABLE_5_1,
        "claude-mythos-5-1" to FABLE_5_1,
        "claude-fable-5" to FABLE,
        "claude-mythos-5" to FABLE,
        SYNTHETIC_MODEL_ID to SYNTHETIC
    ).sortedByDescending { it.first.length }

    /** Devolve o preço do modelo, ou `null` se o identificador não for reconhecido. */
    fun forModel(modelId: String?): ModelPricing? {
        val normalized = normalizeModelId(modelId) ?: return null

        for ((prefix, pricing) in entries) {
            if (matchesModelPrefix(normalized, prefix)) {
                return pricing
            }
        }
        return null
    }
}

/** Marcador do Claude Code para mensagens injetadas localmente, sem chamada de API. */
const val SYNTHETIC_MODEL_ID = "<synthetic>"

/** Tamanho do sufixo de data (`AAAAMMDD`) aceito depois do prefixo do modelo. */
private const val DATE_SUFFIX_DIGITS = 8

/** Normaliza o identificador de modelo para o match por prefixo. Devolve `null` se vazio. */
internal fun normalizeModelId(modelId: String?): String? {
    return modelId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}

/**
 * Aceita o prefixo exato ou o prefixo seguido de `-` e exatamente oito dígitos (sufixo de data).
 * Qualquer outra continuação — versão menor, texto, data com outro tamanho — não casa.
 */
internal fun matchesModelPrefix(modelId: String, prefix: String): Boolean {
    if (!modelId.startsWith(prefix)) {
        return false
    }
    val rest = modelId.substring(prefix.length)
    if (rest.isEmpty()) {
        return true
    }
    return rest.length == DATE_SUFFIX_DIGITS + 1 &&
        rest[0] == '-' &&
        rest.substring(1).all { it in '0'..'9' }
}
