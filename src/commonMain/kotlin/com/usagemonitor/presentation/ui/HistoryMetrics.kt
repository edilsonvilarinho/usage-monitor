package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.datetime.Instant

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
/**
 * As métricas da série, em duas colunas de pares rótulo→valor.
 *
 * Duas colunas e não uma: com uma, a tela do OpenCode — que tem duas séries por
 * modelo — passava de 2.400px de altura. Não é `FlowRow`: ali a linha mede pelo
 * conteúdo, o `weight` do valor fica sem referência e o Compose deixa o texto
 * **sem posicionar** — `isPlaced` falso, nó na árvore e nada na tela. Duas
 * `Column` com `weight(1f)` dentro de uma `Row` de largura cheia dão ao peso a
 * referência que ele precisa.
 */
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

/**
 * A tabela de métricas: duas colunas de pares rótulo→valor.
 *
 * Duas colunas e não uma: com uma, a tela do OpenCode — que tem duas séries por
 * modelo — passava de 2.400px de altura. E **não** é `FlowRow`: ali a linha mede
 * pelo conteúdo, o `weight` do valor fica sem referência e o Compose deixa o
 * texto sem posicionar — `isPlaced` falso, nó presente na árvore e nada na tela,
 * que é como este layout falhou da primeira vez. Duas `Column` com `weight(1f)`
 * dentro de uma `Row` de largura cheia dão ao peso a referência que falta.
 */
@Composable
internal fun HistoryMetricTable(entries: List<HistoryMetricEntry>) {
    // Ímpar sobra para a esquerda: um buraco no fim da segunda coluna lê melhor
    // que um no meio da primeira.
    val half = (entries.size + 1) / 2

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            entries.take(half).forEach { entry ->
                MetricItem(label = entry.label, value = entry.value)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            entries.drop(half).forEach { entry ->
                MetricItem(label = entry.label, value = entry.value)
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
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
                value = "${formatQuantity(series.deltaDisplayUsed)} req"
            )
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
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
            value = formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
        )
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
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}
