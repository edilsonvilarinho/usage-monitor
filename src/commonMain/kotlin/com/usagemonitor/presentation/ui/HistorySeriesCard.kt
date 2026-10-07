package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.HistoryQuotaView
import kotlin.time.Instant

@Composable
internal fun HistorySeriesCard(
    source: ApiSource, series: UsageHistorySeries, index: Int, accentColor: Color,
    language: AppLanguage, chartSelectionKey: String, titleOverride: String? = null,
    subtitleOverride: String? = null, weeklySummary: UsageHistorySeries? = null,
    referenceAt: Instant? = null, quotaView: HistoryQuotaView = HistoryQuotaView.BOTH,
    monthlySummary: UsageHistorySeries? = null
) {
    var metricsExpanded by remember(chartSelectionKey) { mutableStateOf(true) }
    // Nasce aberta (#392): a lista de janelas e o detalhe são a resposta da issue.
    var analysisExpanded by remember(chartSelectionKey) { mutableStateOf(true) }
    val title = titleOverride ?: historySeriesDisplayTitle(source, series, language)
    val subtitle = subtitleOverride ?: historySeriesDisplaySubtitle(source, series, language)
    val chartSeries = if (quotaView == HistoryQuotaView.WEEKLY && weeklySummary != null) weeklySummary else series
    val visibleSeries = when {
        weeklySummary == null -> listOf(series)
        quotaView == HistoryQuotaView.WEEKLY -> listOf(weeklySummary)
        quotaView == HistoryQuotaView.INTERVAL -> listOf(series)
        else -> listOfNotNull(series, weeklySummary, monthlySummary)
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        HistorySummary(historyMetricEntries(source, chartSeries, language, referenceAt), chartSelectionKey)
        AppDataSurfaceFlush(header = { AppSectionHeader(title = title, subtitle = subtitle, markerColor = accentColor) }) {
            Column(Modifier.fillMaxWidth().padding(AppSpacing.sm)) {
                UsageHistoryLineChart(
                    points = chartSeries.points, unit = chartSeries.unit, language = language,
                    chartSelectionKey = "$chartSelectionKey:${quotaView.name}", tooltipTitle = title, tooltipSubtitle = subtitle,
                    accentColor = accentColor, previousPoints = chartSeries.previousWindowPoints,
                    seriesLabel = quotaWindowLabel(chartSeries, language),
                    overlays = historyChartOverlays(
                        weeklySummary.takeIf { quotaView == HistoryQuotaView.BOTH }, series, AppAccents.current.output, language,
                        monthlySummary = monthlySummary.takeIf { quotaView == HistoryQuotaView.BOTH && weeklySummary != null },
                        monthlyColor = AppAccents.current.savings
                    ),
                    showActiveSpans = chartSeries.windows.isNotEmpty()
                )
                currentActiveSpanCaption(chartSeries, language)?.let { caption ->
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
            }
        }
        HistoryDetailsSection(
            if (language == AppLanguage.PT) "Resumo das cotas" else "Quota summary",
            "historyMetrics:$chartSelectionKey", metricsExpanded, { metricsExpanded = !metricsExpanded }, language
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                visibleSeries.forEach { item ->
                    HistoryMetricsPanel(
                        title = when {
                            item == monthlySummary -> monthlySummaryLabel(language)
                            weeklySummary != null -> if (item == weeklySummary) weeklySummaryLabel(language) else intervalSummaryLabel(language)
                            else -> if (language == AppLanguage.PT) "Resumo da série" else "Series summary"
                        },
                        source = source, series = item, language = language, referenceAt = referenceAt
                    )
                }
            }
        }
        if (visibleSeries.any { it.windows.isNotEmpty() || it.hourlyDistribution != null }) {
            HistoryDetailsSection(
                if (language == AppLanguage.PT) "Janelas e distribuição horária" else "Windows and hourly distribution",
                "historyAnalysis:$chartSelectionKey", analysisExpanded, { analysisExpanded = !analysisExpanded }, language
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    visibleSeries.forEach { item ->
                        val itemColor = when (item) {
                            weeklySummary -> AppAccents.current.output
                            monthlySummary -> AppAccents.current.savings
                            else -> accentColor
                        }
                        HistoryWindowAnalysisPanel(item, itemColor, language)
                    }
                }
            }
        }
    }
}
