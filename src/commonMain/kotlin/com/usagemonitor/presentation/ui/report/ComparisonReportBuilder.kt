package com.usagemonitor.presentation.ui.report

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.ComparisonLabels
import com.usagemonitor.presentation.ui.comparisonCell
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * PDF da comparação (#386): a mesma tabela da tela, sem a cor — no papel o
 * número já é a informação, e a escala de cor não sobrevive à impressão.
 */
fun reportForComparison(
    rows: List<ComparisonRow>,
    range: CliSessionRange,
    language: AppLanguage,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageReportDocument {
    val section = UsageReportSection.Table(
        heading = ComparisonLabels.title(language),
        columns = buildList {
            add(UsageReportColumn(ComparisonLabels.modelColumn(language), weight = 2.4f))
            ComparisonMetric.entries.forEach { metric ->
                add(UsageReportColumn(ComparisonLabels.metric(metric, language), weight = 1f, alignEnd = true))
            }
        },
        rows = rows.map { row ->
            listOf("${row.source.name.lowercase()} · ${row.label}") +
                ComparisonMetric.entries.map { metric -> comparisonCell(row, metric, language) }
        }
    )
    return UsageReportDocument(
        title = ComparisonLabels.title(language),
        subtitle = "${CliSessionsLabels.rangeLabel(range, language)} · ${ReportLabels.generatedAt(language)} ${formatTimestamp(now, timeZone)}",
        sections = listOf(section),
        footnotes = ComparisonLabels.notes(language)
    ).sanitized()
}
