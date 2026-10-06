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
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppSpacing

@Composable
internal fun DeepSeekHistoryContent(report: ApiUsageHistoryReport, accentColor: Color, language: AppLanguage, selectedRange: HistoryRange) {
    val primary = report.series.firstOrNull { it.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true) } ?: report.series.first()
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        (listOf(primary) + report.series.filterNot { it == primary }).forEach { series ->
            key(series.seriesKey, selectedRange) {
                val title = deepSeekSeriesTitle(series, language)
                val subtitle = deepSeekSeriesSubtitle(series, language)
                val referenceAt = report.lastUpdatedAt.takeIf { series == primary }
                val entries = deepSeekMetricEntries(series, language, referenceAt)
                val selection = buildQuotaChartSelectionKey(report.source, series.quotaLabel, series.periodType, selectedRange)
                var expanded by remember(selection) { mutableStateOf(true) }
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    HistorySummary(entries, selection)
                    HistoryProviderChart(title, subtitle, series, accentColor, language, selection)
                    HistoryDetailsSection(if (language == AppLanguage.PT) "Resumo do saldo" else "Balance summary", "historyMetrics:$selection", expanded, { expanded = !expanded }, language) {
                        AppDataSurfaceFlush(header = { AppSectionHeader(title = if (language == AppLanguage.PT) "Detalhes do saldo" else "Balance details") }) {
                            Column(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                HistoryMetricTable(entries)
                                Text(deepSeekForecastText(series.forecast, language), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OpenCodeHistoryContent(report: ApiUsageHistoryReport, accentColor: Color, language: AppLanguage, selectedRange: HistoryRange) {
    val models = remember(report.series, selectedRange) { buildOpenCodeHistoryGroups(report.series, selectedRange) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        models.forEach { model ->
            key(model.modelName, selectedRange) {
                val entries = observedMetricEntries(model, language)
                val selection = buildQuotaChartSelectionKey(report.source, model.modelName, model.chartSeries.periodType, selectedRange)
                var expanded by remember(selection) { mutableStateOf(true) }
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    HistorySummary(entries, selection)
                    HistoryProviderChart(model.modelName, openCodeHistorySubtitle(model.chartSeries.periodType, language), model.chartSeries, accentColor, language, selection)
                    HistoryDetailsSection(if (language == AppLanguage.PT) "Resumo da atividade" else "Activity summary", "historyMetrics:$selection", expanded, { expanded = !expanded }, language) {
                        AppDataSurfaceFlush(header = { AppSectionHeader(title = if (language == AppLanguage.PT) "Atividade local" else "Local activity") }) {
                            Column(Modifier.fillMaxWidth().padding(AppSpacing.md)) { HistoryMetricTable(entries) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryProviderChart(title: String, subtitle: String, series: UsageHistorySeries, color: Color, language: AppLanguage, selection: String) {
    AppDataSurfaceFlush(header = { AppSectionHeader(title = title, subtitle = subtitle, markerColor = color) }) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.sm)) {
            UsageHistoryLineChart(points = series.points, unit = series.unit, language = language, chartSelectionKey = selection, tooltipTitle = title, tooltipSubtitle = subtitle, accentColor = color, previousPoints = series.previousWindowPoints)
        }
    }
}
