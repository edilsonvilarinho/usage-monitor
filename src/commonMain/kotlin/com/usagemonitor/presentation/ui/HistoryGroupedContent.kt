package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.presentation.viewmodel.HistoryQuotaView
import com.usagemonitor.presentation.viewmodel.HistoryUiState

const val HISTORY_QUOTA_VIEW_CHIP_TAG_PREFIX = "history-quota-view-"

fun historyQuotaViewChipTag(view: HistoryQuotaView): String = "$HISTORY_QUOTA_VIEW_CHIP_TAG_PREFIX${view.name}"

/**
 * Os cards das fontes com cota por janela: um por família, com a intervalar e a
 * semanal fundidas quando existem as duas.
 *
 * O Codex passou a vir por aqui (issue #320): antes cada série dele tinha card
 * próprio, e o seletor `5h | 7d | Ambas` só faz sentido com as duas no mesmo
 * card. As séries dele que não se fundem — a reportada e a mensal — continuam
 * com título e subtítulo da própria série, como eram.
 */
@Composable
internal fun GroupedHistoryContent(
    state: HistoryUiState.Success,
    accentColor: Color,
    language: AppLanguage
) {
    val report = state.report
    val cardModels = remember(report.series) {
        buildGenericHistoryGroups(report.series)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        cardModels.forEachIndexed { index, model ->
            key(model.baseLabel + state.selectedAccount?.key.toString() + state.selectedRange.name) {
                val keepSeriesTitle = report.source == ApiSource.CODEX && model.weeklySummary == null
                HistorySeriesCard(
                    source = report.source,
                    series = model.chartSeries,
                    index = index,
                    accentColor = accentColor,
                    language = language,
                    chartSelectionKey = buildQuotaChartSelectionKey(
                        source = report.source,
                        quotaLabel = model.chartSeries.quotaLabel,
                        periodType = model.chartSeries.periodType,
                        selectedRange = state.selectedRange
                    ),
                    titleOverride = if (keepSeriesTitle) null else model.baseLabel,
                    subtitleOverride = if (keepSeriesTitle) null else genericHistorySubtitle(language),
                    weeklySummary = model.weeklySummary,
                    monthlySummary = model.monthlySummary,
                    referenceAt = report.lastUpdatedAt,
                    quotaView = state.selectedQuotaView
                )
            }
        }
    }
}

/**
 * Os rótulos do seletor de janela, na ordem de [HistoryQuotaView], ou `null`
 * quando não há o que escolher: nenhuma família com a intervalar e a semanal
 * juntas. DeepSeek e as fontes de atividade observada montam os próprios cards.
 *
 * Os nomes saem das séries (`5h`/`7d` do rótulo da cota, o tipo de período
 * quando o rótulo não diz): a janela intervalar do MiniMax não é de 5 horas, e
 * um seletor escrito `5h` ali descreveria outra cota.
 */
internal fun quotaViewLabels(report: ApiUsageHistoryReport, language: AppLanguage): List<String>? {
    if (report.source == ApiSource.DEEPSEEK || report.source.isObservedActivitySource()) {
        return null
    }
    val merged = buildGenericHistoryGroups(report.series).firstOrNull { model -> model.weeklySummary != null }
        ?: return null
    val weekly = merged.weeklySummary ?: return null
    return HistoryQuotaView.entries.map { view ->
        when (view) {
            HistoryQuotaView.INTERVAL -> quotaWindowLabel(merged.chartSeries, language)
            HistoryQuotaView.WEEKLY -> quotaWindowLabel(weekly, language)
            // Com a cota mensal no mesmo card, "Ambas" mentiria: são três janelas.
            HistoryQuotaView.BOTH -> when {
                merged.monthlySummary != null -> if (language == AppLanguage.PT) "Todas" else "All"
                language == AppLanguage.PT -> "Ambas"
                else -> "Both"
            }
        }
    }
}
