package com.usagemonitor.domain.entity

/**
 * Rótulos das cotas do Antigravity CLI: `Antigravity <grupo> <janela>`.
 *
 * Cada rótulo é metade da chave da série histórica ([QuotaSeriesKey]); por isso
 * sai só de campos estáveis do `/usage` — o grupo de modelos e a janela —, nunca
 * do texto descritivo que o CLI pode reescrever.
 *
 * O Antigravity é a única fonte com **duas cotas do mesmo `periodType`** (um limite
 * semanal por grupo de modelos). O título do card sai do `periodType` e o HUD usa a
 * última palavra do rótulo, então os dois grupos diriam a mesma coisa. [groupOf] é
 * quem devolve o grupo para esses dois pontos, e é o único dono da forma do rótulo:
 * o prefixo existe para que nenhum rótulo de outra fonte seja lido como grupo.
 */
object AntigravityQuotaLabels {
    private const val PREFIX = "Antigravity "

    fun label(group: String, window: String): String = "$PREFIX$group $window"

    /** "Antigravity Claude/GPT 7d" → "Claude/GPT"; rótulo de outra fonte → `null`. */
    fun groupOf(label: String): String? {
        if (!label.startsWith(PREFIX)) return null
        return label.removePrefix(PREFIX).substringBeforeLast(' ', missingDelimiterValue = "")
            .takeIf(String::isNotBlank)
    }

    /** "Antigravity Claude/GPT 7d" → "Claude/GPT 7d": o rótulo sem o nome da fonte. */
    fun withoutSource(label: String): String? =
        label.takeIf { groupOf(it) != null }?.removePrefix(PREFIX)
}
