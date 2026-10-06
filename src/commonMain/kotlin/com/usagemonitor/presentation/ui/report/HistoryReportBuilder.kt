package com.usagemonitor.presentation.ui.report

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.presentation.ui.*
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun historyReportRequest(state: HistoryUiState.Success, language: AppLanguage, now: Instant): UsageExportRequest {
    val date = now.toLocalDateTime(TimeZone.of("America/Sao_Paulo")).date.toString()
    val range = when (state.report.range) {
        HistoryRange.LAST_24_HOURS -> "24h"
        HistoryRange.LAST_7_DAYS -> "7d"
        HistoryRange.LAST_30_DAYS -> "30d"
        HistoryRange.TOTAL -> "total"
    }
    return UsageExportRequest("usage-monitor-history-${state.report.source.name.lowercase()}-$range-$date.pdf", UsageExportPayload.Report(reportForHistory(state, language, now), language))
}

/** Documento do snapshot estabilizado; expansão e zoom não alteram a seleção. */
fun reportForHistory(state: HistoryUiState.Success, language: AppLanguage, now: Instant): UsageReportDocument {
    val pt = language == AppLanguage.PT
    val report = state.report
    val selected = selectedHistorySeries(state)
    val sections = mutableListOf<UsageReportSection>()
    if (report.source.isObservedActivitySource()) {
        buildOpenCodeHistoryGroups(report.series, report.range).forEach { model ->
            sections += UsageReportSection.Paragraphs(model.modelName, listOf("${if (pt) "Modelo" else "Model"}: ${model.modelName}") + observedMetricEntries(model, language).map { "${it.label}: ${it.value}" })
        }
        selected.forEach { series -> addPointSection(sections, series, language) }
    } else {
        selected.forEach { series ->
            val entries = if (report.source == ApiSource.DEEPSEEK) deepSeekMetricEntries(series, language, report.lastUpdatedAt)
                else historyMetricEntries(report.source, series, language, report.lastUpdatedAt)
            sections += UsageReportSection.Paragraphs(series.quotaLabel, listOf("${if (pt) "Série" else "Series"}: ${series.quotaLabel}") + entries.map { "${it.label}: ${it.value}" })
            if (report.source == ApiSource.DEEPSEEK) sections += UsageReportSection.Paragraphs(if (pt) "Previsão" else "Forecast", listOf(deepSeekForecastText(series.forecast, language)))
            addWindowSections(sections, series, language)
            addPointSection(sections, series, language)
        }
    }
    val points = selected.flatMap { it.points }
    val first = points.minOfOrNull { it.capturedAt }
    val last = points.maxOfOrNull { it.capturedAt }
    val period = if (report.range == HistoryRange.TOTAL) {
        if (pt) "Todo o histórico disponível" else "All available history"
    } else if (report.rangeStartsAt != null && report.rangeEndsAt != null) {
        "${historyReportTime(report.rangeStartsAt)} — ${historyReportTime(report.rangeEndsAt)}"
    } else {
        if (pt) "Recorte absoluto não registrado" else "Absolute range not recorded"
    }
    val notes = mutableListOf(
        if (pt) "Observações disponíveis: ${historyReportTime(first)} — ${historyReportTime(last)}. Última coleta: ${historyReportTime(report.lastUpdatedAt)}." else "Available observations: ${historyReportTime(first)} — ${historyReportTime(last)}. Last snapshot: ${historyReportTime(report.lastUpdatedAt)}.",
        if (pt) "O início de cada janela é a primeira leitura observada. Dados ausentes não representam zero. As janelas não são limitadas às oito linhas da tela." else "Window start is the first observed reading. Missing data is not zero. Windows are not limited to the eight on-screen rows."
    )
    if (report.range == HistoryRange.TOTAL) notes += if (pt) "Os pontos do gráfico em Total podem estar amostrados em até 720 por série. Este relatório não é uma exportação dos snapshots brutos." else "Total chart points may be sampled to 720 per series. This report is not a raw snapshot export."
    if (report.source.isObservedActivitySource()) notes += if (pt) "Atividade observada localmente; a fonte não informa limite ou reinício de cota." else "Locally observed activity; the source does not report a quota limit or reset."
    val account = report.accountContext?.displayLabel ?: sourceLabel(report.source)
    val quotaLabels = quotaViewLabels(report, language)
    val quota = quotaLabels?.get(state.selectedQuotaView.ordinal)
    sections.add(0, UsageReportSection.Paragraphs(if (pt) "Contexto do relatório" else "Report context", listOf(
        "${if (pt) "Fonte" else "Source"}: ${sourceLabel(report.source)}",
        "${if (pt) "Conta" else "Account"}: $account",
        "${if (pt) "Intervalo" else "Range"}: $period${quota?.let { " · $it" } ?: ""}",
        "${if (pt) "Gerado" else "Generated"}: ${historyReportTime(now)}"
    )))
    return UsageReportDocument(
        title = if (pt) "Histórico de uso" else "Usage history",
        subtitle = "${sourceLabel(report.source)} · $account · ${rangeLabel(report.range, language)}${quota?.let { " · $it" } ?: ""} · ${if (pt) "Gerado" else "Generated"}: ${formatInstant(now)}",
        period = period, sections = sections, footnotes = notes
    ).sanitized()
}

private fun addWindowSections(sections: MutableList<UsageReportSection>, series: UsageHistorySeries, language: AppLanguage) {
    val pt = language == AppLanguage.PT
    if (series.windows.isNotEmpty()) sections += UsageReportSection.Table(
        heading = "${if (pt) "Janelas" else "Windows"} · ${series.quotaLabel}",
        columns = listOf(UsageReportColumn(if (pt) "Início observado" else "First observed", 2f), UsageReportColumn(if (pt) "Pico" else "Peak", alignEnd = true), UsageReportColumn(if (pt) "Esgotou em" else "Exhausted after", alignEnd = true), UsageReportColumn(if (pt) "Ritmo" else "Pace", alignEnd = true)),
        rows = series.windows.asReversed().map { listOf(windowStartLabel(it, language), "${it.peakPercent} %", exhaustionLabel(it), paceLabel(it.averagePercentPerHour)) }
    )
    val distribution = series.hourlyDistribution ?: return
    sections += UsageReportSection.Table(
        heading = if (pt) "Consumo por hora do dia (BRT)" else "Usage by hour of day (BRT)",
        columns = listOf(UsageReportColumn(if (pt) "Hora" else "Hour"), UsageReportColumn(if (pt) "Pontos percentuais" else "Percentage points", alignEnd = true)),
        rows = distribution.percentByHour.mapIndexed { hour, value -> listOf("${hour}h", trimDecimal(value)) },
        note = hourlyPeakLabel(distribution, language)
    )
}

private fun historyReportTime(value: Instant?): String {
    if (value == null) return "—"
    val year = value.toLocalDateTime(TimeZone.of("America/Sao_Paulo")).year
    return "$year · ${formatInstant(value)}"
}

private fun addPointSection(sections: MutableList<UsageReportSection>, series: UsageHistorySeries, language: AppLanguage) {
    if (series.points.isEmpty()) return
    val pt = language == AppLanguage.PT
    sections += UsageReportSection.Table(
        heading = "${if (pt) "Leituras" else "Readings"} · ${series.quotaLabel}",
        columns = listOf(UsageReportColumn(if (pt) "Coleta (BRT)" else "Collected (BRT)", 2f), UsageReportColumn(if (pt) "Valor observado" else "Observed value", alignEnd = true), UsageReportColumn(if (pt) "Unidade" else "Unit")),
        rows = series.points.map { point -> listOf(historyReportTime(point.capturedAt), when (series.unit) { UsageUnit.CURRENCY_USD -> formatCents(point.displayUsed); UsageUnit.PERCENTAGE -> currentUsagePercent(point.displayUsed, point.displayTotal); else -> formatQuantity(point.displayUsed) }, unitSuffix(series.unit)) }
    )
}
