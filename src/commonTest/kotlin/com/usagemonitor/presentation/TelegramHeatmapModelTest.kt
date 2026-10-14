package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.presentation.ui.TELEGRAM_HEATMAP_MAX_ROWS
import com.usagemonitor.presentation.ui.buildTelegramHeatmap
import com.usagemonitor.presentation.ui.buildTelegramRings
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.time.Instant
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
    fun `rings put the longest window outside, the highest percent in the center and skip balance only accounts`() {
        val now = Instant.parse("2026-10-13T17:00:00Z")
        fun quota(label: String, percent: Int?, resetsAt: String?, risk: UsageRiskLevel? = null) = UsageSnapshotQuota(
            label, PeriodType.INTERVAL, UsageUnit.PERCENTAGE, 0, 100, percent, resetsAt?.let(Instant::parse), "USD", risk
        )
        val snapshot = UsageSnapshot(
            generatedAt = now,
            accounts = listOf(
                UsageSnapshotAccount(
                    ApiSource.ANTHROPIC, "Anthropic · Padrão", false,
                    listOf(
                        quota("5h", 86, "2026-10-13T19:00:00Z", UsageRiskLevel.WILL_EXCEED),
                        quota("Semanal", 61, "2026-10-16T22:44:00Z", UsageRiskLevel.ON_TRACK),
                        quota("Opus", 40, "2026-10-16T22:44:00Z"),
                        quota("Sonnet", 10, "2026-10-14T22:44:00Z")
                    )
                ),
                UsageSnapshotAccount(ApiSource.DEEPSEEK, "DeepSeek", false, listOf(quota("Saldo", null, null)))
            )
        )

        val rings = buildTelegramRings(snapshot, AppLanguage.PT)

        assertEquals(1, rings.rings.size)
        val ring = rings.rings.single()
        assertEquals(86, ring.centerPercent)
        assertEquals("Crítico", ring.riskWord)
        // Reinício mais distante por fora (empate pelo rótulo), teto de três arcos.
        assertEquals(listOf(40, 61, 10), ring.arcs.map { it.percent })
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
