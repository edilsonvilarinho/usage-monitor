package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.ComparisonRow

/** Rótulos e células da comparação (#386), os mesmos na tela e no PDF. */
internal object ComparisonLabels {

    fun title(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Comparar modelos e APIs" else "Compare models and APIs"

    fun openAction(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Comparar modelos e APIs" else "Compare models and APIs"

    fun modelColumn(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Modelo / fonte" else "Model / source"

    fun metric(metric: ComparisonMetric, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        return when (metric) {
            ComparisonMetric.TOKENS -> "Tokens"
            ComparisonMetric.COST -> if (pt) "Custo" else "Cost"
            ComparisonMetric.CACHE -> "Cache"
            ComparisonMetric.THROUGHPUT -> if (pt) "Vazão" else "Throughput"
            ComparisonMetric.QUOTA -> if (pt) "Cota da fonte" else "Source quota"
        }
    }

    /**
     * O que cada coluna mede e por que há células vazias — uma vez, abaixo da
     * tabela, e não em cada "—": a cor do mapa é apoio, o número está escrito.
     */
    fun notes(language: AppLanguage): List<String> = if (language == AppLanguage.PT) {
        listOf(
            "Cor: valor sobre o maior da coluna. O número está sempre escrito.",
            "Custo a preço de tabela, não é fatura. Codex sem tarifa conhecida não vira custo zero.",
            "Vazão: tokens de saída do pedido à última linha da resposta, ponta a ponta.",
            "Cota da fonte: maior percentual entre as janelas da conta agora; é da conta, não do modelo.",
            "\"—\": a fonte não mede a métrica (MiniMax não informa tokens; fonte sem CLI não tem vazão)."
        )
    } else {
        listOf(
            "Color: value over the column maximum. The number is always written.",
            "Cost at list price, not an invoice. Codex has no known rate and is never shown as zero cost.",
            "Throughput: output tokens from request to the last response line, end to end.",
            "Source quota: highest percentage among the account's windows now; it belongs to the account, not the model.",
            "\"—\": the source does not measure the metric (MiniMax reports no tokens; sources without a CLI have no throughput)."
        )
    }

    fun empty(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Nenhum uso no recorte." else "No usage in range."
}

/** Texto da célula de [metric] em [row]. */
internal fun comparisonCell(row: ComparisonRow, metric: ComparisonMetric, language: AppLanguage): String {
    return when (metric) {
        ComparisonMetric.TOKENS -> row.totalTokens?.let(::formatQuantity) ?: "—"
        ComparisonMetric.COST -> {
            val cost = row.costMicros
            when {
                cost != null -> if (row.unpricedTurnCount > 0) "${formatMicrosUsd(cost)}+" else formatMicrosUsd(cost)
                row.source == ApiSource.CODEX -> if (language == AppLanguage.PT) "sem tarifa" else "no rate"
                else -> "—"
            }
        }
        ComparisonMetric.CACHE -> row.cacheRate?.let(::formatPercent) ?: "—"
        ComparisonMetric.THROUGHPUT -> formatThroughput(row.throughput)
        ComparisonMetric.QUOTA -> row.quotaPercent?.let { percent -> "$percent%" } ?: "—"
    }
}
