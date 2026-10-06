package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.presentation.viewmodel.HistoryQuotaView
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Mesmos pares de dados para a tela e o PDF, sem dependência do desenho. */
internal fun deepSeekMetricEntries(series: UsageHistorySeries, language: AppLanguage, referenceAt: Instant?): List<HistoryMetricEntry> {
    val pt = language == AppLanguage.PT
    return buildList {
        add(HistoryMetricEntry(if (pt) "Saldo atual" else "Current balance", formatCents(series.currentDisplayUsed)))
        add(HistoryMetricEntry(if (pt) "Gasto no período" else "Spent in range", formatCents(series.deltaDisplayUsed)))
        add(HistoryMetricEntry(if (pt) "Ritmo médio" else "Average pace", formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"))
        if (referenceAt != null) add(HistoryMetricEntry(if (pt) "Última coleta" else "Last snapshot", formatInstant(referenceAt)))
    }
}

internal fun observedMetricEntries(model: OpenCodeHistoryModelReport, language: AppLanguage): List<HistoryMetricEntry> {
    val pt = language == AppLanguage.PT
    val tokens = model.chartSeries.unit == UsageUnit.TOKENS
    val noun = if (tokens) "Tokens" else if (pt) "Requisições" else "Requests"
    fun value(count: Long): String = if (tokens) "${formatQuantity(count)} tok" else localizedRequests(count, language)
    return listOf(
        HistoryMetricEntry(if (pt) "$noun nas últimas 5h" else "$noun in last 5h", value(model.requests5h)),
        HistoryMetricEntry(if (pt) "$noun nos últimos 7 dias" else "$noun in last 7 days", value(model.requests7d)),
        HistoryMetricEntry(if (pt) "Variação observada" else "Observed change", value(model.chartSeries.deltaDisplayUsed)),
        HistoryMetricEntry(if (pt) "Média por hora" else "Average per hour", value(model.chartSeries.averageDisplayConsumptionPerHour.roundToLong()) + "/h"),
        HistoryMetricEntry(if (pt) "Limite" else "Limit", if (pt) "Não informado pela fonte" else "Not reported by the source")
    )
}

/** A mesma seleção de cotas do gráfico; séries independentes permanecem no relatório. */
internal fun selectedHistorySeries(state: HistoryUiState.Success): List<UsageHistorySeries> {
    if (state.report.source == ApiSource.DEEPSEEK || state.report.source.isObservedActivitySource()) return state.report.series
    return buildGenericHistoryGroups(state.report.series).flatMap { group ->
        val weekly = group.weeklySummary
        when {
            weekly == null -> listOf(group.chartSeries)
            state.selectedQuotaView == HistoryQuotaView.WEEKLY -> listOf(weekly)
            state.selectedQuotaView == HistoryQuotaView.INTERVAL -> listOf(group.chartSeries)
            else -> listOfNotNull(group.chartSeries, weekly, group.monthlySummary)
        }
    }
}
