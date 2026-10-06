package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.dailyBaseline
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.roundToLong
import kotlin.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HistoryMetricsPanel(
    title: String,
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage,
    referenceAt: Instant?
) {
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = title) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.sm)) {
            HistoryMetrics(
                source = source,
                series = series,
                language = language,
                referenceAt = referenceAt
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/** Métricas compartilhadas com o relatório, adaptadas à largura disponível. */
@Composable
internal fun HistoryMetrics(
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage,
    referenceAt: Instant?
) {
    HistoryMetricTable(
        entries = historyMetricEntries(
            source = source,
            series = series,
            language = language,
            referenceAt = referenceAt
        )
    )
}

/** Uma coluna abaixo de 600dp; duas colunas largas com referência válida para weight. */
@Composable
internal fun HistoryMetricTable(entries: List<HistoryMetricEntry>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            Column { entries.forEach { MetricItem(it.label, it.value) } }
        } else {
            val half = (entries.size + 1) / 2
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
                Column(Modifier.weight(1f)) { entries.take(half).forEach { MetricItem(it.label, it.value) } }
                Column(Modifier.weight(1f)) { entries.drop(half).forEach { MetricItem(it.label, it.value) } }
            }
        }
    }
}

/** Um par de métrica. Nome e valor já formatados e já traduzidos. */
internal data class HistoryMetricEntry(val label: String, val value: String)

/**
 * As métricas que a série publica, na ordem em que a tela as mostra.
 *
 * Separada do desenho porque a escolha de quais métricas existem depende do
 * tipo de período, da unidade e da fonte — regra de apresentação que não tem
 * nada a ver com o layout de duas colunas.
 */
internal fun historyMetricEntries(
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage,
    referenceAt: Instant?
): List<HistoryMetricEntry> {
    val entries = mutableListOf<HistoryMetricEntry>()

    if (series.periodType == PeriodType.REPORTED) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
            value = "${currentUsagePercent(series.currentDisplayUsed, series.currentDisplayTotal)} / 100 %"
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
            value = formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
        )
        if (source == ApiSource.CODEX) {
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Último reinício reportado" else "Last reported reset",
                value = formatInstant(series.currentPeriodEndAt)
            )
        }
    } else if (series.unit == UsageUnit.CURRENCY_USD) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
            value = formatCents(series.currentDisplayUsed)
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
            value = formatCents(series.deltaDisplayUsed)
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
            value = formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"
        )
    } else if (series.unit == UsageUnit.REQUESTS) {
        if (series.currentDisplayTotal > 0L) {
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
                value = "${formatQuantity(series.currentDisplayUsed)}/${formatQuantity(series.currentDisplayTotal)} req"
            )
            entries += windowEntriesOrConsumed(series, language) {
                "${formatQuantity(series.deltaDisplayUsed)} req"
            }
        } else {
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Requisições na janela" else "Requests in window",
                value = "${formatQuantity(series.currentDisplayUsed)} req"
            )
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                value = "${formatQuantity(series.deltaDisplayUsed)} req"
            )
        }
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
            value = "${formatQuantity(series.averageDisplayConsumptionPerHour.roundToLong())} req/h"
        )
    } else {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
            value = "${currentUsagePercent(series.currentDisplayUsed, series.currentDisplayTotal)} / 100 %"
        )
        entries += windowEntriesOrConsumed(series, language) {
            formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
        }
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
            value = formatPercentageOfTotal(series.averageDisplayConsumptionPerHour, series.currentDisplayTotal) + "/h"
        )
    }

    if (series.periodType != PeriodType.REPORTED) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Previsão" else "Forecast",
            value = if (series.unit == UsageUnit.REQUESTS && series.currentDisplayTotal <= 0L) {
                if (language == AppLanguage.PT) "Limite indisponível" else "Limit unavailable"
            } else {
                forecastLabel(series.forecast, language)
            }
        )
    }

    val comparison = series.comparison
    if (comparison != null) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "vs. período anterior" else "vs. previous period",
            value = periodComparisonLabel(comparison, language)
        )
    }

    // O consumo de hoje contra o hábito, ao lado da comparação de janelas — as
    // duas respondem "está mais ou menos que antes", e é a linha acima que
    // responde "quanto falta para o teto". A referência de tempo é o carimbo do
    // último ponto coletado, e **nunca** `Clock.System.now()`: num composable ele
    // mudaria a cada recomposição e o teste não teria valor previsível para
    // afirmar.
    val baseline = referenceAt?.let { at -> series.dailyBaseline(at, HISTORY_TIME_ZONE) }
    val baselineFactor = baseline?.factor
    if (baselineFactor != null) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Hoje vs. mediana diária" else "Today vs. daily median",
            value = dailyBaselineLabel(baselineFactor, baseline.completeDays, language)
        )
    }

    return entries
}

/**
 * O resumo das janelas no lugar de "Consumido no período" (issue #320).
 *
 * Aquela linha somava as subidas de todas as janelas do intervalo e dividia pelo
 * total de uma: numa semana de janelas de 5h dava 173%, número que não responde
 * pergunta nenhuma. Com janelas conhecidas a tabela diz quantas houve, quantas
 * esgotaram e quanto cada uma costuma usar. Sem elas — cota sem reinício
 * conhecido — a linha antiga fica, porque ali não há janela para contar.
 */
private fun windowEntriesOrConsumed(
    series: UsageHistorySeries,
    language: AppLanguage,
    consumedValue: () -> String
): List<HistoryMetricEntry> {
    val stats = series.windowStats
        ?: return listOf(
            HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
                value = consumedValue()
            )
        )

    val entries = mutableListOf(
        HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Janelas no intervalo" else "Windows in range",
            value = windowCountLabel(stats.windowCount, stats.exhaustedCount, language)
        )
    )
    val averagePeak = stats.averagePeakPercent
    if (averagePeak != null) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Pico médio por janela" else "Average peak per window",
            value = "${averagePeak.roundToLong()} %"
        )
    }
    val averageConsumed = stats.averageConsumedPercent
    if (averageConsumed != null) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Consumo médio por janela" else "Average use per window",
            value = "${averageConsumed.roundToLong()} %"
        )
    }
    return entries
}

internal fun windowCountLabel(windowCount: Int, exhaustedCount: Int, language: AppLanguage): String {
    if (exhaustedCount == 0) {
        return windowCount.toString()
    }
    val exhausted = if (language == AppLanguage.PT) {
        if (exhaustedCount == 1) "1 esgotou" else "$exhaustedCount esgotaram"
    } else {
        "$exhaustedCount exhausted"
    }
    return "$windowCount · $exhausted"
}

/**
 * Uma métrica: rótulo à esquerda, valor à direita, largura fixa.
 *
 * Era rótulo em cima e valor embaixo, num `FlowRow` cujas colunas mudavam de
 * largura conforme o texto — sete métricas viravam sete larguras diferentes e
 * nenhum valor alinhava com o de baixo. Com a largura fixa os pares formam
 * colunas de verdade, e o `FlowRow` decide quantas cabem.
 */
@Composable
private fun MetricItem(
    label: String,
    value: String
) {
    Row(
        // Largura cheia: "A janela deve reiniciar antes do limite" é um valor de
        // métrica, e numa coluna estreita o rótulo ao lado quebrava letra a letra.
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            // A previsão completa pode ocupar várias linhas, sem truncar o conteúdo.
            modifier = Modifier.weight(1f)
        )
    }
}
