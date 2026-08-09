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
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.rememberScrollbarAdapter
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
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.domain.entity.requiresUsageAccount
import com.usagemonitor.presentation.ui.components.HistoryChartSelectionController
import com.usagemonitor.presentation.ui.components.HistoryIntervalSummaryModel
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.components.buildHistoryIntervalSummaryModel
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
    val scrollState = rememberScrollState()
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .padding(end = 12.dp),
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
                            LaunchedEffect(current.selectedSource, current.selectedAccount?.key, current.selectedRange) {
                                chartSelectionController.clear()
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                HistoryControls(
                                    availableSources = current.availableSources,
                                    selectedSource = current.selectedSource,
                                    availableAccounts = current.availableAccounts,
                                    selectedAccount = current.selectedAccount,
                                    selectedRange = current.selectedRange,
                                    showSourceSelector = showSourceSelector,
                                    language = language,
                                    onSelectSource = viewModel::selectSource,
                                    onSelectAccount = viewModel::selectAccount,
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
                                    } else if (current.report.source == ApiSource.CODEX) {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text(
                                                text = lastUpdatedLabel(current.report.lastUpdatedAt, language),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            current.report.series.forEachIndexed { index, series ->
                                                key(
                                                    series.quotaLabel +
                                                        current.selectedAccount?.key.toString() +
                                                        current.selectedRange.name
                                                ) {
                                                    HistorySeriesCard(
                                                        source = current.report.source,
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
                                    } else {
                                        val cardModels = remember(current.report.series) {
                                            buildGenericHistoryGroups(current.report.series)
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Text(
                                                text = lastUpdatedLabel(current.report.lastUpdatedAt, language),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            cardModels.forEachIndexed { index, model ->
                                                key(
                                                    model.baseLabel +
                                                        current.selectedAccount?.key.toString() +
                                                        current.selectedRange.name
                                                ) {
                                                    HistorySeriesCard(
                                                        source = current.report.source,
                                                        series = model.chartSeries,
                                                        index = index,
                                                        accentColor = accentColor,
                                                        language = language,
                                                        chartSelectionKey = buildQuotaChartSelectionKey(
                                                            source = current.report.source,
                                                            quotaLabel = model.chartSeries.quotaLabel,
                                                            periodType = model.chartSeries.periodType,
                                                            selectedRange = current.selectedRange
                                                        ),
                                                        selectionController = chartSelectionController,
                                                        titleOverride = model.baseLabel,
                                                        subtitleOverride = genericHistorySubtitle(language),
                                                        weeklySummary = model.weeklySummary
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

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
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
    availableAccounts: List<UsageAccountContext>,
    selectedAccount: UsageAccountContext?,
    selectedRange: HistoryRange,
    showSourceSelector: Boolean,
    language: AppLanguage,
    onSelectSource: (ApiSource) -> Unit,
    onSelectAccount: (UsageAccountContext) -> Unit,
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

        if (selectedSource.requiresUsageAccount) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (language == AppLanguage.PT) "Conta" else "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (availableAccounts.isEmpty()) {
                    Text(
                        text = if (language == AppLanguage.PT) {
                            "Nenhuma conta identificada. Atualize o card após concluir o login."
                        } else {
                            "No account identified. Refresh the card after sign-in completes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (account in availableAccounts) {
                            RangeChip(
                                label = account.displayLabel,
                                selected = account.key == selectedAccount?.key,
                                onClick = { onSelectAccount(account) }
                            )
                        }
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
    source: ApiSource,
    series: UsageHistorySeries,
    index: Int,
    accentColor: Color,
    language: AppLanguage,
    chartSelectionKey: String,
    selectionController: HistoryChartSelectionController,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    weeklySummary: UsageHistorySeries? = null
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

    val title = titleOverride ?: historySeriesDisplayTitle(
        source = source,
        series = series,
        language = language
    )
    val subtitle = subtitleOverride ?: historySeriesDisplaySubtitle(
        source = source,
        series = series,
        language = language
    )

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
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (series.periodType == PeriodType.INTERVAL) {
                    IntervalSummaryPanel(
                        summary = buildHistoryIntervalSummaryModel(
                            points = series.points,
                            unit = series.unit,
                            language = language,
                            selection = selectionController.selectionFor(chartSelectionKey, series.points.size)
                        )
                    )
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

                HistoryMetrics(
                    source = source,
                    series = series,
                    language = language
                )

                if (weeklySummary != null) {
                    WeeklySummaryPanel(
                        source = source,
                        series = weeklySummary,
                        language = language
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeeklySummaryPanel(
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage
) {
    Surface(
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = weeklySummaryLabel(language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            HistoryMetrics(
                source = source,
                series = series,
                language = language
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalSummaryPanel(
    summary: HistoryIntervalSummaryModel?
) {
    if (summary == null) {
        return
    }

    Surface(
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            summary.headline?.let { headline ->
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (summary.metrics.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    summary.metrics.forEach { metric ->
                        MetricItem(label = metric.label, value = metric.value)
                    }
                }
            }

            Text(
                text = summary.supportingText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryMetrics(
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (series.periodType == PeriodType.REPORTED) {
            MetricItem(
                label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
                value = "${currentUsagePercent(series.currentDisplayUsed, series.currentDisplayTotal)} / 100 %"
            )
            MetricItem(
                label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                value = formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
            )
            if (source == ApiSource.CODEX) {
                MetricItem(
                    label = if (language == AppLanguage.PT) "Último reinício reportado" else "Last reported reset",
                    value = formatInstant(series.currentPeriodEndAt)
                )
            }
        } else if (series.unit == UsageUnit.CURRENCY_USD) {
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
        if (series.periodType != PeriodType.REPORTED) {
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

