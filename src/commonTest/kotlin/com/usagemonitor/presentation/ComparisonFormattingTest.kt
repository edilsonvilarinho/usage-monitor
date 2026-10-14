package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.presentation.ui.ComparisonLabels
import com.usagemonitor.presentation.ui.comparisonCell
import com.usagemonitor.presentation.ui.formatMicrosUsd
import kotlin.test.Test
import kotlin.test.assertEquals

class ComparisonFormattingTest {

    private fun row(
        source: ApiSource = ApiSource.ANTHROPIC,
        tokens: Long? = null,
        cost: Long? = null,
        unpriced: Int = 0,
        quota: Int? = null
    ) = ComparisonRow(
        source = source,
        label = "modelo",
        turnCount = null,
        totalTokens = tokens,
        costMicros = cost,
        unpricedTurnCount = unpriced,
        cacheRate = null,
        throughput = null,
        quotaPercent = quota
    )

    @Test
    fun `metrica que a fonte nao mede vira travessao`() {
        val empty = row(source = ApiSource.MINIMAX)

        assertEquals("—", comparisonCell(empty, ComparisonMetric.TOKENS, AppLanguage.PT))
        assertEquals("—", comparisonCell(empty, ComparisonMetric.COST, AppLanguage.PT))
        assertEquals("—", comparisonCell(empty, ComparisonMetric.CACHE, AppLanguage.PT))
        assertEquals("—", comparisonCell(empty, ComparisonMetric.QUOTA, AppLanguage.PT))
    }

    @Test
    fun `custo com turno sem tarifa e piso e leva mais`() {
        assertEquals(formatMicrosUsd(1_500_000L), comparisonCell(row(cost = 1_500_000L), ComparisonMetric.COST, AppLanguage.PT))
        assertEquals(
            formatMicrosUsd(1_500_000L) + "+",
            comparisonCell(row(cost = 1_500_000L, unpriced = 2), ComparisonMetric.COST, AppLanguage.PT)
        )
    }

    /** Codex sem tarifa conhecida nunca vira custo zero nem travessão: diz que não há tarifa. */
    @Test
    fun `codex sem custo diz que nao ha tarifa`() {
        val codex = row(source = ApiSource.CODEX)

        assertEquals("sem tarifa", comparisonCell(codex, ComparisonMetric.COST, AppLanguage.PT))
        assertEquals("no rate", comparisonCell(codex, ComparisonMetric.COST, AppLanguage.EN))
    }

    @Test
    fun `cota da fonte em percentual e vazao com o rotulo das sessoes`() {
        assertEquals("62%", comparisonCell(row(quota = 62), ComparisonMetric.QUOTA, AppLanguage.PT))
        assertEquals("Vazão", ComparisonLabels.metric(ComparisonMetric.THROUGHPUT, AppLanguage.PT))
        assertEquals("Throughput", ComparisonLabels.metric(ComparisonMetric.THROUGHPUT, AppLanguage.EN))
    }
}
