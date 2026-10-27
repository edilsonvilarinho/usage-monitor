package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.presentation.ui.ComparisonLabels
import com.usagemonitor.presentation.ui.comparisonCell
import com.usagemonitor.presentation.ui.report.UsageReportSection
import com.usagemonitor.presentation.ui.report.reportForComparison
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class ComparisonReportBuilderTest {

    private val now = Instant.parse("2026-10-27T15:04:00Z")

    private val rows = listOf(
        ComparisonRow(
            source = ApiSource.ANTHROPIC,
            label = "claude-sonnet-5",
            turnCount = 12,
            totalTokens = 50_000L,
            costMicros = 2_500_000L,
            unpricedTurnCount = 1,
            cacheRate = 0.8,
            throughput = null,
            quotaPercent = 40
        ),
        ComparisonRow(
            source = ApiSource.CODEX,
            label = "gpt-6.1-sol",
            turnCount = null,
            totalTokens = null,
            costMicros = null,
            cacheRate = null,
            throughput = null,
            quotaPercent = null
        )
    )

    @Test
    fun `pdf table is the same comparison as the screen, one column per metric`() {
        val document = reportForComparison(rows, CliSessionRange.LAST_7D, AppLanguage.EN, now)

        assertEquals(ComparisonLabels.title(AppLanguage.EN), document.title)
        assertEquals("7 days · generated at 2026-10-27 12:04 BRT", document.subtitle)
        assertTrue(document.footnotes.isNotEmpty())

        val table = assertIs<UsageReportSection.Table>(document.sections.single())
        assertEquals(1 + ComparisonMetric.entries.size, table.columns.size)
        assertEquals(ComparisonLabels.modelColumn(AppLanguage.EN), table.columns.first().title)
        assertTrue(table.columns.drop(1).all { column -> column.alignEnd })

        assertEquals("anthropic · claude-sonnet-5", table.rows[0].first())
        assertEquals("codex · gpt-6.1-sol", table.rows[1].first())
        // Mesma célula da tela: custo incompleto com "+", Codex "sem tarifa", não medido "—".
        assertEquals(
            ComparisonMetric.entries.map { metric -> comparisonCell(rows[0], metric, AppLanguage.EN) },
            table.rows[0].drop(1)
        )
        assertTrue(table.rows[0].contains(comparisonCell(rows[0], ComparisonMetric.COST, AppLanguage.EN)))
        assertTrue(comparisonCell(rows[0], ComparisonMetric.COST, AppLanguage.EN).endsWith("+"))
        assertTrue(table.rows[1].contains("no rate"))
    }

    @Test
    fun `portuguese report is sanitized for the pdf font`() {
        val document = reportForComparison(rows, CliSessionRange.ALL, AppLanguage.PT, now)

        assertTrue(document.subtitle.startsWith("Total · gerado em"))
        val table = assertIs<UsageReportSection.Table>(document.sections.single())
        assertTrue(table.rows[1].contains("sem tarifa"))
    }
}
