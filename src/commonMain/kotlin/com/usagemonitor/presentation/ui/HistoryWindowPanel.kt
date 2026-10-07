package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppMenu
import com.usagemonitor.presentation.ui.components.AppMenuOption
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val HISTORY_WINDOW_ITEM_TAG_PREFIX = "historyWindowItem:"
const val HISTORY_WINDOW_MENU_TAG = "historyWindowMenu"
const val HISTORY_WINDOW_DETAIL_TAG = "historyWindowDetail"

fun historyWindowItemTag(window: QuotaWindowSummary): String =
    "$HISTORY_WINDOW_ITEM_TAG_PREFIX${window.firstObservedAt.toEpochMilliseconds()}"

/** Largura da lista: cabe "06/10 16:06 · atual" e "pico 100 % · esgotou em 3h 23min" em duas linhas. */
private val WINDOW_LIST_WIDTH = 200.dp

/** Abaixo disso a lista vira menu acima do detalhe — duas colunas não cabem. */
private val WINDOW_LIST_MIN_WIDTH = 600.dp

/**
 * As janelas de uma série (#392, direção S9): lista à esquerda, mais nova
 * primeiro, e o detalhe da escolhida à direita — métricas, curva com a faixa
 * ativa e as horas de consumo **só dela**. Antes era uma tabela de todas as
 * janelas com as barras do intervalo inteiro embaixo, e as barras não diziam de
 * que janela eram.
 *
 * Nada aparece quando a série não tem janela nem distribuição. Sem janela mas
 * com distribuição, ficam só as barras do intervalo, como antes.
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
        if (series.windows.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm)) {
                HistoryHourlyDistribution(distribution = distribution!!, color = accentColor, language = language)
            }
            return@AppDataSurfaceFlush
        }
        val listed = windowRowsNewestFirst(series.windows)
        var selectedStart by remember(series.quotaLabel, series.periodType, series.windows) {
            mutableStateOf(defaultSelectedWindow(series.windows)?.firstObservedAt)
        }
        val selected = listed.firstOrNull { candidate -> candidate.firstObservedAt == selectedStart } ?: listed.first()
        val onSelect: (QuotaWindowSummary) -> Unit = { picked -> selectedStart = picked.firstObservedAt }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val panelWidth = maxWidth
            if (panelWidth < WINDOW_LIST_MIN_WIDTH) {
                Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    HistoryWindowMenu(listed, selected, onSelect, language, panelWidth)
                    HistoryWindowDetail(series, selected, accentColor, language)
                }
            } else {
                // Sem divisória de altura cheia: `IntrinsicSize.Min` pediria medida
                // intrínseca ao `BoxWithConstraints` do detalhe, que não a suporta.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Column(Modifier.width(WINDOW_LIST_WIDTH)) {
                        listed.forEachIndexed { index, item ->
                            HistoryWindowListItem(item, item == selected, index != listed.lastIndex, language) { onSelect(item) }
                        }
                    }
                    Column(Modifier.weight(1f).padding(vertical = AppSpacing.sm)) {
                        HistoryWindowDetail(series, selected, accentColor, language)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryWindowListItem(
    window: QuotaWindowSummary,
    selected: Boolean,
    showDivider: Boolean,
    language: AppLanguage,
    onClick: () -> Unit
) {
    AppDataRow(
        modifier = Modifier.testTag(historyWindowItemTag(window)).semantics { this.selected = selected },
        onClick = onClick,
        showDivider = showDivider,
        highlighted = selected
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = windowStartLabel(window, language),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = windowListDetail(window, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryWindowMenu(
    listed: List<QuotaWindowSummary>,
    selected: QuotaWindowSummary,
    onSelect: (QuotaWindowSummary) -> Unit,
    language: AppLanguage,
    menuWidth: Dp
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (language == AppLanguage.PT) "Janela" else "Window"
    val value = windowStartLabel(selected, language)
    AppMenu(
        expanded = expanded,
        options = listed.map { item -> AppMenuOption("${windowStartLabel(item, language)} · ${windowListDetail(item, language)}", historyWindowItemTag(item)) },
        selectedIndex = listed.indexOf(selected),
        onSelect = { index -> expanded = false; onSelect(listed[index]) },
        onDismissRequest = { expanded = false },
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        maxWidth = menuWidth,
        wrapLabels = true
    ) {
        AppButton(
            label = "$label: $value ▾",
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().testTag(HISTORY_WINDOW_MENU_TAG).semantics {
                contentDescription = "$label: $value"
                stateDescription = if (expanded) "Expanded" else "Collapsed"
            }
        )
    }
}

/** Métricas, curva com faixa ativa e horas de consumo de uma janela. */
@Composable
private fun HistoryWindowDetail(
    series: UsageHistorySeries,
    window: QuotaWindowSummary,
    accentColor: Color,
    language: AppLanguage
) {
    val pt = language == AppLanguage.PT
    val points = remember(series.points, window) { pointsOfWindow(series.points, window) }
    Column(
        modifier = Modifier.fillMaxWidth().testTag(HISTORY_WINDOW_DETAIL_TAG),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.md)) {
            HistoryMetricTable(listOf(
                HistoryMetricEntry(if (pt) "Início observado" else "First reading", windowStartLabel(window, language)),
                HistoryMetricEntry(if (pt) "Ativa" else "Active", activeSpanLabel(window, language)),
                HistoryMetricEntry(if (pt) "Pico" else "Peak", "${window.peakPercent} %"),
                HistoryMetricEntry(if (pt) "Esgotou em" else "Exhausted after", exhaustionLabel(window)),
                HistoryMetricEntry(if (pt) "Ritmo" else "Pace", paceLabel(window.averagePercentPerHour))
            ))
        }
        // Uma leitura só não forma curva: o gráfico seria um ponto solto.
        if (points.size >= 2) {
            Column(Modifier.padding(horizontal = AppSpacing.sm)) {
                UsageHistoryLineChart(
                    points = points, unit = series.unit, language = language,
                    chartSelectionKey = "historyWindow:${series.quotaLabel}:${window.firstObservedAt.toEpochMilliseconds()}",
                    tooltipTitle = windowStartLabel(window, language), accentColor = accentColor,
                    showActiveSpans = true
                )
            }
        }
        window.hourlyDistribution?.let { hours ->
            HistoryHourlyDistribution(distribution = hours, color = accentColor, language = language)
        }
    }
}
