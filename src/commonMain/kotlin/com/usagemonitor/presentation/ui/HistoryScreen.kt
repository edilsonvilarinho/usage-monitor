package com.usagemonitor.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.presentation.ui.components.HistoryChartSelectionController
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    language: AppLanguage,
    onBack: () -> Unit,
    focusedSource: ApiSource? = null,
    showSourceSelector: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val chartSelectionController = remember { HistoryChartSelectionController() }
    val selectedSourceForHeader = when (val current = state) {
        is HistoryUiState.Empty -> current.selectedSource
        is HistoryUiState.Error -> current.selectedSource
        is HistoryUiState.Success -> current.selectedSource
        HistoryUiState.Loading -> focusedSource
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            HistoryHeader(
                language = language,
                selectedSource = selectedSourceForHeader,
                showSourceSelector = showSourceSelector,
                onBack = onBack
            )

            AnimatedContent(
                targetState = state::class,
                transitionSpec = {
                    (fadeIn(tween(AppMotion.normal, easing = AppMotion.enterEasing)) +
                        slideInVertically(tween(AppMotion.slow, easing = AppMotion.enterEasing)) { it / 10 })
                        .togetherWith(fadeOut(tween(AppMotion.fast, easing = AppMotion.exitEasing)))
                        .using(SizeTransform(clip = false))
                },
                label = "historyStateContent"
            ) { _ ->
                when (val current = state) {
                    is HistoryUiState.Loading -> {
                        Text(
                            text = if (language == AppLanguage.PT) "Carregando histórico..." else "Loading history...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is HistoryUiState.Empty -> {
                        Text(
                            text = if (language == AppLanguage.PT) {
                                "Ainda não há snapshots salvos. Faça algumas atualizações bem-sucedidas no dashboard para começar."
                            } else {
                                "There are no saved snapshots yet. Run a few successful dashboard refreshes to get started."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is HistoryUiState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = if (language == AppLanguage.PT) "Erro ao carregar histórico" else "Failed to load history",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = current.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is HistoryUiState.Success -> {
                        LaunchedEffect(current.selectedSource, current.selectedRange) {
                            chartSelectionController.clear()
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            HistoryControls(
                                availableSources = current.availableSources,
                                selectedSource = current.selectedSource,
                                selectedRange = current.selectedRange,
                                showSourceSelector = showSourceSelector,
                                language = language,
                                onSelectSource = viewModel::selectSource,
                                onSelectRange = viewModel::selectRange
                            )

                            if (current.report.series.isEmpty()) {
                                Text(
                                    text = if (language == AppLanguage.PT) {
                                        "Sem dados para o intervalo selecionado."
                                    } else {
                                        "No data for the selected range."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                val accentColor = accentColorForHistorySource(current.report.source)
                                if (current.report.source == ApiSource.DEEPSEEK) {
                                    DeepSeekHistoryContent(
                                        report = current.report,
                                        accentColor = accentColor,
                                        language = language,
                                        selectedRange = current.selectedRange,
                                        selectionController = chartSelectionController
                                    )
                                } else if (current.report.source.isObservedActivitySource()) {
                                    OpenCodeHistoryContent(
                                        report = current.report,
                                        accentColor = accentColor,
                                        language = language,
                                        selectedRange = current.selectedRange,
                                        selectionController = chartSelectionController
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(
                                            text = lastUpdatedLabel(current.report.lastUpdatedAt, language),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        current.report.series.forEachIndexed { index, series ->
                                            key(series.quotaLabel + current.selectedRange.name) {
                                                HistorySeriesCard(
                                                    series = series,
                                                    index = index,
                                                    accentColor = accentColor,
                                                    language = language,
                                                    chartSelectionKey = buildQuotaChartSelectionKey(
                                                        source = current.report.source,
                                                        quotaLabel = series.quotaLabel,
                                                        periodType = series.periodType,
                                                        selectedRange = current.selectedRange
                                                    ),
                                                    selectionController = chartSelectionController
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader(
    language: AppLanguage,
    selectedSource: ApiSource?,
    showSourceSelector: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = historyTitle(
                    selectedSource = selectedSource,
                    showSourceSelector = showSourceSelector,
                    language = language
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = historySubtitle(
                    selectedSource = selectedSource,
                    showSourceSelector = showSourceSelector,
                    language = language
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showSourceSelector) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text(if (language == AppLanguage.PT) "Voltar" else "Back")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryControls(
    availableSources: List<ApiSource>,
    selectedSource: ApiSource,
    selectedRange: HistoryRange,
    showSourceSelector: Boolean,
    language: AppLanguage,
    onSelectSource: (ApiSource) -> Unit,
    onSelectRange: (HistoryRange) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (showSourceSelector) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (language == AppLanguage.PT) "API" else "API",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableSources.forEach { source ->
                        RangeChip(
                            label = sourceLabel(source),
                            selected = source == selectedSource,
                            onClick = { onSelectSource(source) }
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = if (language == AppLanguage.PT) "Intervalo" else "Range",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HistoryRange.entries.forEach { range ->
                    RangeChip(
                        label = rangeLabel(range, language),
                        selected = range == selectedRange,
                        onClick = { onSelectRange(range) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                      else           MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = AppMotion.fast),
        label = "chipColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                      else           MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = AppMotion.fast),
        label = "chipLabelColor"
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor
            )
        },
        shape = AppShapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor            = containerColor,
            selectedContainerColor    = containerColor,
            labelColor                = labelColor,
            selectedLabelColor        = labelColor
        )
    )
}

@Composable
private fun DeepSeekHistoryContent(
    report: ApiUsageHistoryReport,
    accentColor: Color,
    language: AppLanguage,
    selectedRange: HistoryRange,
    selectionController: HistoryChartSelectionController
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
                ),
                selectionController = selectionController
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
                    ),
                    selectionController = selectionController
                )
            }
        }
    }
}

@Composable
private fun OpenCodeHistoryContent(
    report: ApiUsageHistoryReport,
    accentColor: Color,
    language: AppLanguage,
    selectedRange: HistoryRange,
    selectionController: HistoryChartSelectionController
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
                    ),
                    selectionController = selectionController
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
    chartSelectionKey: String,
    selectionController: HistoryChartSelectionController
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "cardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "cardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accentColor.copy(alpha = 0.85f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
                        value = formatCents(series.currentDisplayUsed)
                    )
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Gasto no período" else "Spent in range",
                        value = formatCents(series.deltaDisplayUsed)
                    )
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Ritmo médio" else "Average pace",
                        value = formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"
                    )
                    if (lastUpdatedAt != null) {
                        MetricItem(
                            label = if (language == AppLanguage.PT) "Última coleta" else "Last snapshot",
                            value = formatInstant(lastUpdatedAt)
                        )
                    }
                }

                UsageHistoryLineChart(
                    points = series.points,
                    unit = series.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    selectionController = selectionController,
                    tooltipTitle = title,
                    tooltipSubtitle = subtitle
                )

                deepSeekForecastText(series.forecast, language)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
    chartSelectionKey: String,
    selectionController: HistoryChartSelectionController
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "openCodeHistoryCardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "openCodeHistoryCardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accentColor.copy(alpha = 0.85f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = modelReport.modelName,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = openCodeHistorySubtitle(
                            periodType = modelReport.chartSeries.periodType,
                            language = language
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                UsageHistoryLineChart(
                    points = modelReport.chartSeries.points,
                    unit = modelReport.chartSeries.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    selectionController = selectionController,
                    tooltipTitle = modelReport.modelName,
                    tooltipSubtitle = openCodeHistorySubtitle(
                        periodType = modelReport.chartSeries.periodType,
                        language = language
                    )
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Requisições nas últimas 5h" else "Requests in last 5h",
                        value = localizedRequests(modelReport.requests5h, language)
                    )
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Requisições nos últimos 7 dias" else "Requests in last 7 days",
                        value = localizedRequests(modelReport.requests7d, language)
                    )
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                        value = localizedRequests(modelReport.chartSeries.deltaDisplayUsed, language)
                    )
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                        value = localizedRequests(modelReport.chartSeries.averageDisplayConsumptionPerHour.roundToLong(), language) +
                            if (language == AppLanguage.PT) "/h" else "/h"
                    )
                    MetricItem(
                        label = if (language == AppLanguage.PT) "Previsão" else "Forecast",
                        value = if (language == AppLanguage.PT) "Limite indisponível" else "Limit unavailable"
                    )
                }
            }
        }
    }
}

@Composable
private fun HistorySeriesCard(
    series: UsageHistorySeries,
    index: Int,
    accentColor: Color,
    language: AppLanguage,
    chartSelectionKey: String,
    selectionController: HistoryChartSelectionController
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "seriesCardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "seriesCardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .background(accentColor.copy(alpha = 0.85f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = series.quotaLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (series.periodType.name == "WEEKLY") {
                            if (language == AppLanguage.PT) "Quota semanal" else "Weekly quota"
                        } else {
                            if (language == AppLanguage.PT) "Quota intervalar" else "Interval quota"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                UsageHistoryLineChart(
                    points = series.points,
                    unit = series.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    selectionController = selectionController,
                    tooltipTitle = series.quotaLabel,
                    tooltipSubtitle = if (series.periodType.name == "WEEKLY") {
                        if (language == AppLanguage.PT) "Quota semanal" else "Weekly quota"
                    } else {
                        if (language == AppLanguage.PT) "Quota intervalar" else "Interval quota"
                    }
                )

                HistoryMetrics(series = series, language = language)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryMetrics(
    series: UsageHistorySeries,
    language: AppLanguage
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (series.unit == UsageUnit.CURRENCY_USD) {
            MetricItem(
                label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
                value = formatCents(series.currentDisplayUsed)
            )
            MetricItem(
                label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
                value = formatCents(series.deltaDisplayUsed)
            )
            MetricItem(
                label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                value = formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"
            )
        } else if (series.unit == UsageUnit.REQUESTS) {
            if (series.currentDisplayTotal > 0L) {
                MetricItem(
                    label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
                    value = "${formatQuantity(series.currentDisplayUsed)}/${formatQuantity(series.currentDisplayTotal)} req"
                )
                MetricItem(
                    label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
                    value = "${formatQuantity(series.deltaDisplayUsed)} req"
                )
                MetricItem(
                    label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                    value = "${formatQuantity(series.averageDisplayConsumptionPerHour.roundToLong())} req/h"
                )
            } else {
                MetricItem(
                    label = if (language == AppLanguage.PT) "Requisições na janela" else "Requests in window",
                    value = "${formatQuantity(series.currentDisplayUsed)} req"
                )
                MetricItem(
                    label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                    value = "${formatQuantity(series.deltaDisplayUsed)} req"
                )
                MetricItem(
                    label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                    value = "${formatQuantity(series.averageDisplayConsumptionPerHour.roundToLong())} req/h"
                )
            }
        } else {
            MetricItem(
                label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
                value = "${currentUsagePercent(series.currentDisplayUsed, series.currentDisplayTotal)} / 100 %"
            )
            MetricItem(
                label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
                value = formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
            )
            MetricItem(
                label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                value = formatPercentageOfTotal(series.averageDisplayConsumptionPerHour, series.currentDisplayTotal) + "/h"
            )
        }
        MetricItem(
            label = if (language == AppLanguage.PT) "Previsão" else "Forecast",
            value = if (series.unit == UsageUnit.REQUESTS && series.currentDisplayTotal <= 0L) {
                if (language == AppLanguage.PT) "Limite indisponível" else "Limit unavailable"
            } else {
                forecastLabel(series.forecast, language)
            }
        )
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun accentColorForHistorySource(source: ApiSource): Color {
    return when (source) {
        ApiSource.ANTHROPIC -> Color(0xFF4F8CFF)
        ApiSource.MINIMAX   -> Color(0xFFFF8A3D)
        ApiSource.CODEX     -> Color(0xFF27BFA3)
        ApiSource.DEEPSEEK  -> Color(0xFFC084FC)
        ApiSource.OPENCODE  -> Color(0xFF7BD389)
        ApiSource.KILO      -> Color(0xFFE6D84E)
    }
}

private fun rangeLabel(range: HistoryRange, language: AppLanguage): String {
    return when (range) {
        HistoryRange.LAST_24_HOURS -> if (language == AppLanguage.PT) "24h" else "24h"
        HistoryRange.LAST_7_DAYS -> if (language == AppLanguage.PT) "7 dias" else "7 days"
        HistoryRange.LAST_30_DAYS -> if (language == AppLanguage.PT) "30 dias" else "30 days"
    }
}

private fun sourceLabel(source: ApiSource): String {
    return source.displayName()
}

private fun historyTitle(
    selectedSource: ApiSource?,
    showSourceSelector: Boolean,
    language: AppLanguage
): String {
    if (!showSourceSelector && selectedSource != null) {
        return if (language == AppLanguage.PT) {
            "Histórico do ${sourceLabel(selectedSource)}"
        } else {
            "${sourceLabel(selectedSource)} history"
        }
    }

    return if (language == AppLanguage.PT) "Histórico de uso" else "Usage history"
}

private fun historySubtitle(
    selectedSource: ApiSource?,
    showSourceSelector: Boolean,
    language: AppLanguage
): String {
    if (selectedSource == ApiSource.DEEPSEEK) {
        return if (language == AppLanguage.PT) {
            "Saldo restante, gasto no intervalo e tendência recente."
        } else {
            "Remaining balance, spend in range, and recent trend."
        }
    }

    if (selectedSource?.isObservedActivitySource() == true) {
        return if (language == AppLanguage.PT) {
            "Atividade observada por modelo free nas janelas de 5h e 7d."
        } else {
            "Observed activity per free model across 5h and 7d windows."
        }
    }

    return if (language == AppLanguage.PT) {
        "Tendência, consumo médio e previsão por quota."
    } else {
        "Trend, average consumption, and forecast by quota."
    }
}

private fun lastUpdatedLabel(lastUpdatedAt: Instant?, language: AppLanguage): String {
    if (lastUpdatedAt == null) {
        return if (language == AppLanguage.PT) "Última coleta: —" else "Last snapshot: —"
    }

    return if (language == AppLanguage.PT) {
        "Última coleta: ${formatInstant(lastUpdatedAt)}"
    } else {
        "Last snapshot: ${formatInstant(lastUpdatedAt)}"
    }
}

private fun forecastLabel(forecast: UsageForecast, language: AppLanguage): String {
    return when (forecast) {
        UsageForecast.InsufficientData -> if (language == AppLanguage.PT) "Dados insuficientes" else "Insufficient data"
        UsageForecast.NoGrowth -> if (language == AppLanguage.PT) "Sem crescimento detectado" else "No growth detected"
        UsageForecast.ResetsBeforeExhaustion -> if (language == AppLanguage.PT) "A janela deve reiniciar antes do limite" else "Window should reset before the limit"
        is UsageForecast.EstimatedExhaustionAt -> {
            if (language == AppLanguage.PT) {
                "Esgota por volta de ${formatInstant(forecast.instant)}"
            } else {
                "Expected to exhaust around ${formatInstant(forecast.instant)}"
            }
        }
    }
}

private fun deepSeekSeriesTitle(series: UsageHistorySeries, language: AppLanguage): String {
    if (series.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true)) {
        return if (language == AppLanguage.PT) "Saldo restante" else "Remaining balance"
    }

    if (series.quotaLabel.equals(DeepSeekQuotaLabels.GRANTED, ignoreCase = true)) {
        return if (language == AppLanguage.PT) "Crédito gratuito" else "Free credit"
    }

    return series.quotaLabel
}

private fun deepSeekSeriesSubtitle(series: UsageHistorySeries, language: AppLanguage): String {
    if (series.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true)) {
        return if (language == AppLanguage.PT) {
            "Leitura do saldo restante ao longo do intervalo selecionado."
        } else {
            "Remaining balance observed over the selected range."
        }
    }

    if (series.quotaLabel.equals(DeepSeekQuotaLabels.GRANTED, ignoreCase = true)) {
        return if (language == AppLanguage.PT) {
            "Crédito promocional ainda disponível no período."
        } else {
            "Promotional credit still available in the selected range."
        }
    }

    return if (language == AppLanguage.PT) {
        "Saldo observado neste intervalo."
    } else {
        "Balance observed in this range."
    }
}

private fun deepSeekForecastText(forecast: UsageForecast, language: AppLanguage): String? {
    return when (forecast) {
        UsageForecast.InsufficientData -> null
        UsageForecast.NoGrowth -> null
        UsageForecast.ResetsBeforeExhaustion -> null
        is UsageForecast.EstimatedExhaustionAt -> {
            if (language == AppLanguage.PT) {
                "Mantendo esse ritmo, o saldo pode acabar por volta de ${formatInstant(forecast.instant)}."
            } else {
                "At the current pace, the balance may run out around ${formatInstant(forecast.instant)}."
            }
        }
    }
}

private fun formatInstant(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    return "${local.date.dayOfMonth.toString().padStart(2, '0')}/${local.date.monthNumber.toString().padStart(2, '0')} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')} BRT"
}

private fun currentUsagePercent(used: Long, total: Long): String {
    if (total <= 0L) return "—"
    return (used * 100.0 / total.toDouble()).roundToLong().toString()
}

private fun formatPercentageOfTotal(value: Double, total: Long): String {
    if (total <= 0L) return "— %"
    val pct = value * 100.0 / total.toDouble()
    return "${pct.roundToLong()} %"
}

private fun formatQuantity(value: Long): String {
    return when {
        value >= 1_000_000L -> "${trimDecimal(value / 1_000_000.0)}M"
        value >= 1_000L -> "${trimDecimal(value / 1_000.0)}K"
        else -> value.toString()
    }
}

private fun localizedRequests(value: Long, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        "${formatQuantity(value)} requisições"
    } else {
        "${formatQuantity(value)} requests"
    }
}

private fun formatCents(cents: Long): String {
    val sign = if (cents < 0L) "-" else ""
    val absCents = kotlin.math.abs(cents)
    val dollars = absCents / 100
    val remainder = absCents % 100
    return "${sign}\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

private fun trimDecimal(value: Double): String {
    val text = "%.1f".format(value)
    return text.removeSuffix(".0").removeSuffix(",0")
}

private fun unitSuffix(unit: UsageUnit): String {
    return when (unit) {
        UsageUnit.TOKENS -> "tok"
        UsageUnit.REQUESTS -> "req"
        UsageUnit.PERCENTAGE -> "%"
        UsageUnit.CURRENCY_USD -> "USD"
    }
}

private data class OpenCodeHistoryModelReport(
    val modelName: String,
    val chartSeries: UsageHistorySeries,
    val requests5h: Long,
    val requests7d: Long
)

private fun buildOpenCodeHistoryGroups(
    series: List<UsageHistorySeries>,
    selectedRange: HistoryRange
): List<OpenCodeHistoryModelReport> {
    val grouped = linkedMapOf<String, MutableOpenCodeHistoryGroup>()

    series.forEach { item ->
        val modelName = when {
            item.quotaLabel.endsWith(" 5h") -> item.quotaLabel.removeSuffix(" 5h")
            item.quotaLabel.endsWith(" 7d") -> item.quotaLabel.removeSuffix(" 7d")
            else -> item.quotaLabel
        }

        val group = grouped.getOrPut(modelName) { MutableOpenCodeHistoryGroup(modelName) }
        when {
            item.quotaLabel.endsWith(" 5h") -> group.series5h = item
            item.quotaLabel.endsWith(" 7d") -> group.series7d = item
        }
    }

    return grouped.values.mapNotNull { group ->
        val chartSeries = selectOpenCodeChartSeries(group, selectedRange) ?: return@mapNotNull null
        OpenCodeHistoryModelReport(
            modelName = group.modelName,
            chartSeries = chartSeries,
            requests5h = group.series5h?.currentDisplayUsed ?: 0L,
            requests7d = group.series7d?.currentDisplayUsed ?: group.series5h?.currentDisplayUsed ?: 0L
        )
    }
}

private data class MutableOpenCodeHistoryGroup(
    val modelName: String,
    var series5h: UsageHistorySeries? = null,
    var series7d: UsageHistorySeries? = null
)

private fun selectOpenCodeChartSeries(
    group: MutableOpenCodeHistoryGroup,
    selectedRange: HistoryRange
): UsageHistorySeries? {
    return when (selectedRange) {
        HistoryRange.LAST_24_HOURS -> group.series5h ?: group.series7d
        HistoryRange.LAST_7_DAYS,
        HistoryRange.LAST_30_DAYS -> group.series7d ?: group.series5h
    }
}

private fun openCodeHistorySubtitle(
    periodType: PeriodType,
    language: AppLanguage
): String {
    return when (periodType) {
        PeriodType.INTERVAL -> if (language == AppLanguage.PT) {
            "Atividade observada do modelo free na janela curta de 5h."
        } else {
            "Observed free-model activity in the 5h short window."
        }

        PeriodType.WEEKLY -> if (language == AppLanguage.PT) {
            "Atividade observada do modelo free na janela semanal de 7 dias."
        } else {
            "Observed free-model activity in the 7-day weekly window."
        }
    }
}

private fun buildQuotaChartSelectionKey(
    source: ApiSource,
    quotaLabel: String,
    periodType: PeriodType,
    selectedRange: HistoryRange
): String {
    return "${source.name}:${quotaLabel}:${periodType.name}:${selectedRange.name}"
}
