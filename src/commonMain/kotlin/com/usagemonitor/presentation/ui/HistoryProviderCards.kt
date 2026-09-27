package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.animateFloatAsState
import com.usagemonitor.presentation.ui.theme.appTween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant

@Composable
internal fun DeepSeekHistoryContent(
    report: ApiUsageHistoryReport,
    accentColor: Color,
    language: AppLanguage,
    selectedRange: HistoryRange
) {
    val primarySeries = report.series
        .firstOrNull { series -> series.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true) }
        ?: report.series.first()
    val extraSeries = report.series.filterNot { series -> series == primarySeries }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        key(primarySeries.quotaLabel + selectedRange.name) {
            DeepSeekHistoryCard(
                title = deepSeekSeriesTitle(primarySeries, language),
                subtitle = deepSeekSeriesSubtitle(primarySeries, language),
                series = primarySeries,
                lastUpdatedAt = report.lastUpdatedAt,
                accentColor = accentColor,
                index = 0,
                language = language,
                chartSelectionKey = buildQuotaChartSelectionKey(
                    source = report.source,
                    quotaLabel = primarySeries.quotaLabel,
                    periodType = primarySeries.periodType,
                    selectedRange = selectedRange
                )
            )
        }

        extraSeries.forEachIndexed { i, series ->
            key(series.quotaLabel + selectedRange.name) {
                DeepSeekHistoryCard(
                    title = deepSeekSeriesTitle(series, language),
                    subtitle = deepSeekSeriesSubtitle(series, language),
                    series = series,
                    lastUpdatedAt = null,
                    accentColor = accentColor,
                    index = i + 1,
                    language = language,
                    chartSelectionKey = buildQuotaChartSelectionKey(
                        source = report.source,
                        quotaLabel = series.quotaLabel,
                        periodType = series.periodType,
                        selectedRange = selectedRange
                    )
                )
            }
        }
    }
}

@Composable
internal fun OpenCodeHistoryContent(
    report: ApiUsageHistoryReport,
    accentColor: Color,
    language: AppLanguage,
    selectedRange: HistoryRange
) {
    val modelReports = remember(report.series, selectedRange) {
        buildOpenCodeHistoryGroups(report.series, selectedRange)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = lastUpdatedLabel(report.lastUpdatedAt, language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        modelReports.forEachIndexed { index, modelReport ->
            key(modelReport.modelName + selectedRange.name) {
                OpenCodeHistoryCard(
                    modelReport = modelReport,
                    accentColor = accentColor,
                    index = index,
                    language = language,
                    chartSelectionKey = buildQuotaChartSelectionKey(
                        source = report.source,
                        quotaLabel = modelReport.modelName,
                        periodType = modelReport.chartSeries.periodType,
                        selectedRange = selectedRange
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepSeekHistoryCard(
    title: String,
    subtitle: String,
    series: UsageHistorySeries,
    lastUpdatedAt: Instant?,
    accentColor: Color,
    index: Int,
    language: AppLanguage,
    chartSelectionKey: String
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "cardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = appTween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "cardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    // Mesma anatomia de `HistorySeriesCard`: painel neutro, cabeçalho com o
    // marcador de 2dp e nenhuma sombra. A faixa de 3dp que atravessava a altura
    // toda, a superfície com alpha e a elevação de 6 eram os três restos do card
    // anterior que sobreviveram à passada da Fase E — esta tela só é composta com
    // a DeepSeek selecionada, e nenhuma captura passava por aqui.
    AppDataSurfaceFlush(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        header = {
            AppSectionHeader(
                title = title,
                subtitle = subtitle,
                markerColor = accentColor
            )
        }
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                HistoryMetricTable(
                    entries = buildList {
                        add(
                            HistoryMetricEntry(
                                label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
                                value = formatCents(series.currentDisplayUsed)
                            )
                        )
                        add(
                            HistoryMetricEntry(
                                label = if (language == AppLanguage.PT) "Gasto no período" else "Spent in range",
                                value = formatCents(series.deltaDisplayUsed)
                            )
                        )
                        add(
                            HistoryMetricEntry(
                                label = if (language == AppLanguage.PT) "Ritmo médio" else "Average pace",
                                value = formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"
                            )
                        )
                        if (lastUpdatedAt != null) {
                            add(
                                HistoryMetricEntry(
                                    label = if (language == AppLanguage.PT) "Última coleta" else "Last snapshot",
                                    value = formatInstant(lastUpdatedAt)
                                )
                            )
                        }
                    }
                )

                UsageHistoryLineChart(
                    points = series.points,
                    unit = series.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    tooltipTitle = title,
                    tooltipSubtitle = subtitle,
                    accentColor = accentColor,
                    previousPoints = series.previousWindowPoints
                )

                Text(
                    text = deepSeekForecastText(series.forecast, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenCodeHistoryCard(
    modelReport: OpenCodeHistoryModelReport,
    accentColor: Color,
    index: Int,
    language: AppLanguage,
    chartSelectionKey: String
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "openCodeHistoryCardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = appTween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "openCodeHistoryCardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    AppDataSurfaceFlush(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        header = {
            AppSectionHeader(
                title = modelReport.modelName,
                subtitle = openCodeHistorySubtitle(
                    periodType = modelReport.chartSeries.periodType,
                    language = language
                ),
                markerColor = accentColor
            )
        }
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                UsageHistoryLineChart(
                    points = modelReport.chartSeries.points,
                    unit = modelReport.chartSeries.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    tooltipTitle = modelReport.modelName,
                    tooltipSubtitle = openCodeHistorySubtitle(
                        periodType = modelReport.chartSeries.periodType,
                        language = language
                    ),
                    accentColor = accentColor,
                    previousPoints = modelReport.chartSeries.previousWindowPoints
                )

                HistoryMetricTable(
                    entries = listOf(
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Requisições nas últimas 5h" else "Requests in last 5h",
                            value = localizedRequests(modelReport.requests5h, language)
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Requisições nos últimos 7 dias" else "Requests in last 7 days",
                            value = localizedRequests(modelReport.requests7d, language)
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                            value = localizedRequests(modelReport.chartSeries.deltaDisplayUsed, language)
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                            value = localizedRequests(
                                modelReport.chartSeries.averageDisplayConsumptionPerHour.roundToLong(),
                                language
                            ) + "/h"
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Previsão" else "Forecast",
                            value = if (language == AppLanguage.PT) "Limite indisponível" else "Limit unavailable"
                        )
                    )
                )
            }
    }
}
