package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.presentation.ui.TELEGRAM_HEATMAP_MAX_ROWS
import com.usagemonitor.presentation.ui.buildTelegramHeatmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** O mapa de calor do `/comparar` (#408, AA6) sai da mesma tabela da janela de comparação. */
class TelegramHeatmapModelTest {

    private fun row(source: ApiSource, label: String, tokens: Long?, cost: Long?, quota: Int?) = ComparisonRow(
        source = source, label = label, turnCount = null, totalTokens = tokens, costMicros = cost, cacheRate = null,
        throughput = null, quotaPercent = quota
    )

    @Test
    fun `cells reuse the comparison text and intensity is over the column maximum`() {
        val rows = listOf(
            row(ApiSource.ANTHROPIC, "claude-opus-5-5", 2_000_000, 96_100_000, 86),
            row(ApiSource.CODEX, "gpt-5-codex", 1_000_000, null, 22),
            row(ApiSource.MINIMAX, "MiniMax", null, null, 9)
        )

        val heatmap = buildTelegramHeatmap(rows, AppLanguage.PT)

        assertEquals(listOf("Tokens", "Custo", "Cache", "Vazão", "Cota da fonte"), heatmap.columns)
        assertEquals(listOf("Anthropic · claude-opus-5-5", "Codex · gpt-5-codex", "MiniMax"), heatmap.rows.map { it.label })
        assertEquals(1.0, heatmap.rows[0].cells[0].intensity)
        assertEquals(0.5, heatmap.rows[1].cells[0].intensity)
        assertEquals("sem tarifa", heatmap.rows[1].cells[1].text)
        assertEquals("—", heatmap.rows[2].cells[0].text)
        assertNull(heatmap.rows[2].cells[0].intensity)
    }

    @Test
    fun `rows stop at the ceiling without changing the scale`() {
        val rows = (1..20).map { index -> row(ApiSource.ANTHROPIC, "m$index", index * 1_000L, null, null) }

        val heatmap = buildTelegramHeatmap(rows, AppLanguage.EN)

        assertEquals(TELEGRAM_HEATMAP_MAX_ROWS, heatmap.rows.size)
        // A primeira linha mostrada (1 000) continua medida contra o maior de todas (20 000).
        assertEquals(0.05, heatmap.rows[0].cells[0].intensity)
    }
}
