package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.pacePathOf
import com.usagemonitor.domain.entity.quotaWindowLengthMillis
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val HISTORY_PACE_PANEL_TAG_PREFIX = "historyPacePanel:"

/** A partir daqui as janelas 5h e 7d ficam lado a lado (#463, D5); abaixo, uma embaixo da outra. */
private val SIDE_BY_SIDE_MIN_WIDTH = 720.dp

/** A série tem duração nominal conhecida e janelas no intervalo: entra na diagonal do ritmo. */
internal fun hasPaceDiagonal(series: UsageHistorySeries): Boolean =
    series.windows.isNotEmpty() && quotaWindowLengthMillis(series.quotaLabel, series.periodType) != null

/**
 * As janelas das séries na diagonal do ritmo (#463, direção AH1), lado a lado a partir de
 * [SIDE_BY_SIDE_MIN_WIDTH]: antes 5h e 7d vinham empilhadas e pediam duas rolagens.
 */
@Composable
internal fun HistoryPaceWindowsRow(series: List<UsageHistorySeries>, colorOf: (UsageHistorySeries) -> Color, language: AppLanguage) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (series.size >= 2 && maxWidth >= SIDE_BY_SIDE_MIN_WIDTH) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                series.forEach { item -> HistoryPaceWindowsPanel(item, colorOf(item), language, Modifier.weight(1f)) }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                series.forEach { item -> HistoryPaceWindowsPanel(item, colorOf(item), language, Modifier.fillMaxWidth()) }
            }
        }
    }
}

/**
 * Uma série na diagonal: todas as janelas listadas (até [HISTORY_WINDOW_ROW_LIMIT]) no mesmo
 * gráfico, a escolhida em destaque e as outras atrás; o menu de janelas para escolher; os fatos
 * e as horas só da escolhida. Abre na janela atual, senão na mais nova — a mesma regra da lista (#392).
 */
@Composable
internal fun HistoryPaceWindowsPanel(series: UsageHistorySeries, accentColor: Color, language: AppLanguage, modifier: Modifier = Modifier) {
    val lengthMillis = quotaWindowLengthMillis(series.quotaLabel, series.periodType) ?: return
    val listed = windowRowsNewestFirst(series.windows)
    val paths = remember(series.points, listed, lengthMillis) {
        listed.mapNotNull { window -> pacePathOf(pointsOfWindow(series.points, window), window.resetsAt, lengthMillis)?.let { path -> window to path } }
    }
    var selectedStart by remember(series.quotaLabel, series.periodType, series.windows) {
        mutableStateOf(defaultSelectedWindow(series.windows)?.firstObservedAt)
    }
    val selected = listed.firstOrNull { window -> window.firstObservedAt == selectedStart } ?: listed.first()
    val selectedPath = paths.firstOrNull { (window, _) -> window == selected }?.second
    val window = quotaWindowLabel(series, language)
    AppDataSurfaceFlush(
        modifier = modifier.testTag(HISTORY_PACE_PANEL_TAG_PREFIX + series.quotaLabel),
        header = {
            AppSectionHeader(
                title = localized(language, "Janelas $window", "$window windows"),
                subtitle = windowCountSubtitle(series.windows.size, language)
            )
        }
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            // Escolher uma entre várias janelas é o menu do histórico (#392): chip é para ligar e
            // desligar uma restrição, não para escolher um item (design system).
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                HistoryWindowMenu(listed, selected, { picked -> selectedStart = picked.firstObservedAt }, language, maxWidth)
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                if (selectedPath != null) {
                    PaceDiagonalChart(
                        model = PaceDiagonalModels.forHistory(paths.map { (_, path) -> path }, selectedPath, lengthMillis, language, paceWindowDescription(selected, language)),
                        lineColor = if (selected.exhaustedAt != null) MaterialTheme.colorScheme.error else accentColor,
                        language = language
                    )
                }
                Text(paceWindowFacts(selected, language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                selected.hourlyDistribution?.let { hours -> HistoryHourlyDistribution(distribution = hours, color = accentColor, language = language) }
            }
        }
    }
}

/** Ativa, pico, se esgotou e o ritmo da janela escolhida, numa linha. */
internal fun paceWindowFacts(window: QuotaWindowSummary, language: AppLanguage): String = listOf(
    localized(language, "ativa ", "active ") + activeSpanLabel(window, language),
    localized(language, "pico ", "peak ") + "${window.peakPercent}%",
    if (window.exhaustedAt != null) localized(language, "esgotou em ", "ran out after ") + exhaustionLabel(window)
    else localized(language, "não esgotou", "did not run out"),
    localized(language, "ritmo ", "pace ") + paceLabel(window.averagePercentPerHour, language)
).joinToString(" · ")

/** O que o leitor de tela lê no lugar da diagonal do histórico. */
internal fun paceWindowDescription(window: QuotaWindowSummary, language: AppLanguage): String =
    localized(language, "Janela de ", "Window from ") + windowStartLabel(window, language) + ": " + paceWindowFacts(window, language)
