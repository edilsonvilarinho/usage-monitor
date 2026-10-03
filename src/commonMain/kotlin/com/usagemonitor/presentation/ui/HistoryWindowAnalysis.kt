package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaHourlyDistribution
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Janelas listadas por painel; as mais antigas ficam só no resumo agregado. */
internal const val HISTORY_WINDOW_ROW_LIMIT = 8

private val HOURLY_CHART_HEIGHT = 56.dp

/**
 * A análise por janela de uma série (issue #320): a tabela das janelas do
 * intervalo e o consumo por hora do dia.
 *
 * Nada aparece quando a série não tem janela — saldo, cota reportada, cota sem
 * reinício conhecido. Um painel vazio dizendo "sem janelas" seria cromo.
 */
@Composable
internal fun HistoryWindowAnalysisPanel(
    series: UsageHistorySeries,
    accentColor: Color,
    language: AppLanguage
) {
    val distribution = series.hourlyDistribution
    if (series.windows.isEmpty() && distribution == null) {
        return
    }

    val window = quotaWindowLabel(series, language)
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (language == AppLanguage.PT) "Janelas $window" else "$window windows",
                subtitle = windowCountSubtitle(series.windows.size, language)
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            if (series.windows.isNotEmpty()) {
                HistoryWindowTable(windows = series.windows, language = language)
            }
            if (distribution != null) {
                HistoryHourlyDistribution(
                    distribution = distribution,
                    color = accentColor,
                    language = language
                )
            }
        }
    }
}

@Composable
private fun HistoryWindowTable(windows: List<QuotaWindowSummary>, language: AppLanguage) {
    val pt = language == AppLanguage.PT
    AppColumnHeaderRow(startGutter = 0.dp) {
        AppColumnHeaderLabel(if (pt) "Início observado" else "First reading", Modifier.weight(1.4f))
        AppColumnHeaderLabel(if (pt) "Pico" else "Peak", Modifier.weight(0.7f))
        AppColumnHeaderLabel(if (pt) "Esgotou em" else "Exhausted after", Modifier.weight(1f))
        AppColumnHeaderLabel(if (pt) "Ritmo" else "Pace", Modifier.weight(0.8f))
    }
    val newestFirst = windowRowsNewestFirst(windows)
    newestFirst.forEachIndexed { index, window ->
        AppDataRow(showDivider = index != newestFirst.lastIndex) {
            AppCellValue(windowStartLabel(window, language), Modifier.weight(1.4f))
            AppCellValue("${window.peakPercent} %", Modifier.weight(0.7f))
            AppCellValue(exhaustionLabel(window), Modifier.weight(1f))
            AppCellValue(paceLabel(window.averagePercentPerHour), Modifier.weight(0.8f))
        }
    }
}

@Composable
private fun HistoryHourlyDistribution(
    distribution: QuotaHourlyDistribution,
    color: Color,
    language: AppLanguage
) {
    val summary = hourlyPeakLabel(distribution, language)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(
            text = if (language == AppLanguage.PT) "Consumo por hora do dia (BRT)" else "Usage by hour of day (BRT)",
            style = MaterialTheme.typography.labelSmall,
            color = axisColor
        )
        // As barras não carregam número: a frase abaixo delas é o que diz a hora
        // de pico, e é ela que o leitor de tela recebe.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(HOURLY_CHART_HEIGHT)
                .semantics { contentDescription = summary }
        ) {
            val values = distribution.percentByHour
            val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
            val slot = size.width / values.size
            val barWidth = slot * 0.7f
            values.forEachIndexed { hour, value ->
                val left = hour * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, size.height - 1.dp.toPx()),
                    size = Size(barWidth, 1.dp.toPx())
                )
                val barHeight = (value / max).toFloat() * size.height
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("0h", "6h", "12h", "18h", "23h").forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor,
                    modifier = Modifier.weight(1f),
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    }
                )
            }
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/** As janelas mais recentes primeiro, até [HISTORY_WINDOW_ROW_LIMIT]. */
internal fun windowRowsNewestFirst(windows: List<QuotaWindowSummary>): List<QuotaWindowSummary> {
    return windows.asReversed().take(HISTORY_WINDOW_ROW_LIMIT)
}

internal fun windowCountSubtitle(count: Int, language: AppLanguage): String {
    val shown = minOf(count, HISTORY_WINDOW_ROW_LIMIT)
    return when {
        count == 0 -> if (language == AppLanguage.PT) "Onde o consumo acontece no dia" else "Where usage happens in the day"
        count > shown -> if (language == AppLanguage.PT) {
            "$shown mais recentes de $count no intervalo"
        } else {
            "$shown most recent of $count in range"
        }
        count == 1 -> if (language == AppLanguage.PT) "1 janela no intervalo" else "1 window in range"
        else -> if (language == AppLanguage.PT) "$count janelas no intervalo" else "$count windows in range"
    }
}

internal fun windowStartLabel(window: QuotaWindowSummary, language: AppLanguage): String {
    val start = formatInstant(window.firstObservedAt)
    if (!window.isOpen) {
        return start
    }
    return if (language == AppLanguage.PT) "$start · atual" else "$start · current"
}

/**
 * Quanto a janela durou até esgotar, contado da primeira leitura dela — o início
 * nominal a API não informa, e inventá-lo seria derivar número que ela não dá.
 */
internal fun exhaustionLabel(window: QuotaWindowSummary): String {
    val exhaustedAt = window.exhaustedAt ?: return "—"
    return formatElapsed(window.firstObservedAt, exhaustedAt)
}

internal fun paceLabel(percentPerHour: Double?): String {
    if (percentPerHour == null) {
        return "—"
    }
    return "${percentPerHour.roundToLong()} %/h"
}

internal fun hourlyPeakLabel(distribution: QuotaHourlyDistribution, language: AppLanguage): String {
    val hour = distribution.peakHour
        ?: return if (language == AppLanguage.PT) "Sem consumo medido" else "No usage measured"
    val total = distribution.percentByHour.sum()
    val share = if (total > 0.0) (distribution.percentByHour[hour] / total * 100.0).roundToLong() else 0L
    return if (language == AppLanguage.PT) {
        "Pico às ${hour}h BRT · $share% do consumo"
    } else {
        "Peak at ${hour}h BRT · $share% of usage"
    }
}

private fun formatElapsed(from: Instant, to: Instant): String {
    val minutes = (to - from).inWholeMinutes.coerceAtLeast(0L)
    val hours = minutes / 60L
    val rest = minutes % 60L
    return when {
        hours == 0L -> "${rest}min"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}min"
    }
}
