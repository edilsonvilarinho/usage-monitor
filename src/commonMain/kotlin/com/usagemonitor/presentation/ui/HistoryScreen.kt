package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
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

                is HistoryUiState.Success -> {
                    HistoryControls(
                        availableSources = current.availableSources,
                        selectedSource = current.selectedSource,
                        selectedRange = current.selectedRange,
                        showSourceSelector = showSourceSelector,
                        language = language,
                        onSelectSource = viewModel::selectSource,
                        onSelectRange = viewModel::selectRange
                    )

                    Text(
                        text = lastUpdatedLabel(current.report.lastUpdatedAt, language),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        current.report.series.forEach { series ->
                            HistorySeriesCard(
                                series = series,
                                language = language
                            )
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
                text = if (language == AppLanguage.PT) {
                    "Tendência, consumo médio e previsão por quota."
                } else {
                    "Trend, average consumption, and forecast by quota."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        TextButton(onClick = onBack) {
            Text(
                if (showSourceSelector) {
                    if (language == AppLanguage.PT) "Voltar" else "Back"
                } else {
                    if (language == AppLanguage.PT) "Fechar" else "Close"
                }
            )
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
private fun HistorySeriesCard(
    series: UsageHistorySeries,
    language: AppLanguage
) {
    Card(
        shape = AppShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppElevation.card)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = series.quotaLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
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

            UsageHistoryLineChart(points = series.points, unit = series.unit)

            HistoryMetrics(series = series, language = language)
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
            value = forecastLabel(series.forecast, language)
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

private fun rangeLabel(range: HistoryRange, language: AppLanguage): String {
    return when (range) {
        HistoryRange.LAST_24_HOURS -> if (language == AppLanguage.PT) "24h" else "24h"
        HistoryRange.LAST_7_DAYS -> if (language == AppLanguage.PT) "7 dias" else "7 days"
        HistoryRange.LAST_30_DAYS -> if (language == AppLanguage.PT) "30 dias" else "30 days"
    }
}

private fun sourceLabel(source: ApiSource): String {
    return when (source) {
        ApiSource.ANTHROPIC -> "Anthropic"
        ApiSource.MINIMAX -> "MiniMax"
        ApiSource.CODEX -> "Codex"
        ApiSource.DEEPSEEK -> "DeepSeek"
    }
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
