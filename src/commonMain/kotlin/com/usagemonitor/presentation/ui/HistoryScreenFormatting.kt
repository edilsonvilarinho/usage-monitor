package com.usagemonitor.presentation.ui

import androidx.compose.ui.graphics.Color
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.isObservedActivitySource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToLong

internal fun accentColorForHistorySource(source: ApiSource): Color {
    return when (source) {
        ApiSource.ANTHROPIC -> Color(0xFF4F8CFF)
        ApiSource.MINIMAX -> Color(0xFFFF8A3D)
        ApiSource.CODEX -> Color(0xFF27BFA3)
        ApiSource.DEEPSEEK -> Color(0xFFC084FC)
        ApiSource.OPENCODE -> Color(0xFF7BD389)
        ApiSource.KILO -> Color(0xFFE6D84E)
    }
}

internal fun rangeLabel(range: HistoryRange, language: AppLanguage): String {
    return when (range) {
        HistoryRange.LAST_24_HOURS -> if (language == AppLanguage.PT) "24h" else "24h"
        HistoryRange.LAST_7_DAYS -> if (language == AppLanguage.PT) "7 dias" else "7 days"
        HistoryRange.LAST_30_DAYS -> if (language == AppLanguage.PT) "30 dias" else "30 days"
        HistoryRange.TOTAL -> if (language == AppLanguage.PT) "Total" else "All"
    }
}

internal fun sourceLabel(source: ApiSource): String {
    return source.displayName()
}

internal fun historyTitle(
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

internal fun historySubtitle(
    selectedSource: ApiSource?,
    showSourceSelector: Boolean,
    language: AppLanguage
): String {
    if (selectedSource == ApiSource.DEEPSEEK) {
        return if (language == AppLanguage.PT) {
            "Saldo restante, gasto no intervalo e tendência recente."
        } else {
            "Remaining balance, spend in range, and recent trend."
        }
    }

    if (selectedSource?.isObservedActivitySource() == true) {
        return if (language == AppLanguage.PT) {
            "Atividade observada por modelo free nas janelas de 5h e 7d."
        } else {
            "Observed activity per free model across 5h and 7d windows."
        }
    }

    return if (language == AppLanguage.PT) {
        "Tendência, consumo médio e previsão por quota."
    } else {
        "Trend, average consumption, and forecast by quota."
    }
}

internal fun lastUpdatedLabel(lastUpdatedAt: Instant?, language: AppLanguage): String {
    if (lastUpdatedAt == null) {
        return if (language == AppLanguage.PT) "Última coleta: —" else "Last snapshot: —"
    }

    return if (language == AppLanguage.PT) {
        "Última coleta: ${formatInstant(lastUpdatedAt)}"
    } else {
        "Last snapshot: ${formatInstant(lastUpdatedAt)}"
    }
}

internal fun forecastLabel(forecast: UsageForecast, language: AppLanguage): String {
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

internal fun deepSeekSeriesTitle(series: UsageHistorySeries, language: AppLanguage): String {
    if (series.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true)) {
        return if (language == AppLanguage.PT) "Saldo restante" else "Remaining balance"
    }

    if (series.quotaLabel.equals(DeepSeekQuotaLabels.GRANTED, ignoreCase = true)) {
        return if (language == AppLanguage.PT) "Crédito gratuito" else "Free credit"
    }

    return series.quotaLabel
}

internal fun deepSeekSeriesSubtitle(series: UsageHistorySeries, language: AppLanguage): String {
    if (series.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true)) {
        return if (language == AppLanguage.PT) {
            "Leitura do saldo restante ao longo do intervalo selecionado."
        } else {
            "Remaining balance observed over the selected range."
        }
    }

    if (series.quotaLabel.equals(DeepSeekQuotaLabels.GRANTED, ignoreCase = true)) {
        return if (language == AppLanguage.PT) {
            "Crédito promocional ainda disponível no período."
        } else {
            "Promotional credit still available in the selected range."
        }
    }

    return if (language == AppLanguage.PT) {
        "Saldo observado neste intervalo."
    } else {
        "Balance observed in this range."
    }
}

internal fun deepSeekForecastText(forecast: UsageForecast, language: AppLanguage): String? {
    return when (forecast) {
        UsageForecast.InsufficientData -> null
        UsageForecast.NoGrowth -> null
        UsageForecast.ResetsBeforeExhaustion -> null
        is UsageForecast.EstimatedExhaustionAt -> {
            if (language == AppLanguage.PT) {
                "Mantendo esse ritmo, o saldo pode acabar por volta de ${formatInstant(forecast.instant)}."
            } else {
                "At the current pace, the balance may run out around ${formatInstant(forecast.instant)}."
            }
        }
    }
}

internal fun formatInstant(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    return "${local.date.dayOfMonth.toString().padStart(2, '0')}/${local.date.monthNumber.toString().padStart(2, '0')} ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')} BRT"
}

internal fun currentUsagePercent(used: Long, total: Long): String {
    if (total <= 0L) return "—"
    return (used * 100.0 / total.toDouble()).roundToLong().toString()
}

internal fun formatPercentageOfTotal(value: Double, total: Long): String {
    if (total <= 0L) return "— %"
    val pct = value * 100.0 / total.toDouble()
    return "${pct.roundToLong()} %"
}

internal fun formatQuantity(value: Long): String {
    return when {
        value >= 1_000_000L -> "${trimDecimal(value / 1_000_000.0)}M"
        value >= 1_000L -> "${trimDecimal(value / 1_000.0)}K"
        else -> value.toString()
    }
}

internal fun localizedRequests(value: Long, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        "${formatQuantity(value)} requisições"
    } else {
        "${formatQuantity(value)} requests"
    }
}

internal fun formatCents(cents: Long): String {
    val sign = if (cents < 0L) "-" else ""
    val absCents = kotlin.math.abs(cents)
    val dollars = absCents / 100
    val remainder = absCents % 100
    return "${sign}\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

internal fun trimDecimal(value: Double): String {
    val text = "%.1f".format(value)
    return text.removeSuffix(".0").removeSuffix(",0")
}

internal fun unitSuffix(unit: UsageUnit): String {
    return when (unit) {
        UsageUnit.TOKENS -> "tok"
        UsageUnit.REQUESTS -> "req"
        UsageUnit.PERCENTAGE -> "%"
        UsageUnit.CURRENCY_USD -> "USD"
    }
}

internal data class OpenCodeHistoryModelReport(
    val modelName: String,
    val chartSeries: UsageHistorySeries,
    val requests5h: Long,
    val requests7d: Long
)

internal fun buildOpenCodeHistoryGroups(
    series: List<UsageHistorySeries>,
    selectedRange: HistoryRange
): List<OpenCodeHistoryModelReport> {
    val grouped = linkedMapOf<String, MutableOpenCodeHistoryGroup>()

    series.forEach { item ->
        val modelName = when {
            item.quotaLabel.endsWith(" 5h") -> item.quotaLabel.removeSuffix(" 5h")
            item.quotaLabel.endsWith(" 7d") -> item.quotaLabel.removeSuffix(" 7d")
            else -> item.quotaLabel
        }

        val group = grouped.getOrPut(modelName) { MutableOpenCodeHistoryGroup(modelName) }
        when {
            item.quotaLabel.endsWith(" 5h") -> group.series5h = item
            item.quotaLabel.endsWith(" 7d") -> group.series7d = item
        }
    }

    return grouped.values.mapNotNull { group ->
        val chartSeries = selectOpenCodeChartSeries(group, selectedRange) ?: return@mapNotNull null
        OpenCodeHistoryModelReport(
            modelName = group.modelName,
            chartSeries = chartSeries,
            requests5h = group.series5h?.currentDisplayUsed ?: 0L,
            requests7d = group.series7d?.currentDisplayUsed ?: group.series5h?.currentDisplayUsed ?: 0L
        )
    }
}

internal data class MutableOpenCodeHistoryGroup(
    val modelName: String,
    var series5h: UsageHistorySeries? = null,
    var series7d: UsageHistorySeries? = null
)

internal fun selectOpenCodeChartSeries(
    group: MutableOpenCodeHistoryGroup,
    selectedRange: HistoryRange
): UsageHistorySeries? {
    return when (selectedRange) {
        HistoryRange.LAST_24_HOURS -> group.series5h ?: group.series7d
        HistoryRange.LAST_7_DAYS,
        HistoryRange.LAST_30_DAYS,
        HistoryRange.TOTAL -> group.series7d ?: group.series5h
    }
}

internal fun openCodeHistorySubtitle(
    periodType: PeriodType,
    language: AppLanguage
): String {
    return when (periodType) {
        PeriodType.INTERVAL -> if (language == AppLanguage.PT) {
            "Atividade observada do modelo free na janela curta de 5h."
        } else {
            "Observed free-model activity in the 5h short window."
        }

        PeriodType.WEEKLY -> if (language == AppLanguage.PT) {
            "Atividade observada do modelo free na janela semanal de 7 dias."
        } else {
            "Observed free-model activity in the 7-day weekly window."
        }
    }
}

internal fun buildQuotaChartSelectionKey(
    source: ApiSource,
    quotaLabel: String,
    periodType: PeriodType,
    selectedRange: HistoryRange
): String {
    return "${source.name}:${quotaLabel}:${periodType.name}:${selectedRange.name}"
}
