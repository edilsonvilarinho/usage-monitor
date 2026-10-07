package com.usagemonitor.presentation.ui

import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaHourlyDistribution
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import kotlin.math.roundToLong
import kotlin.time.Instant

/** Janelas listadas por painel; as mais antigas ficam só no resumo agregado. */
internal const val HISTORY_WINDOW_ROW_LIMIT = 8

/**
 * A janela que o detalhe abre (#392, direção S9): a aberta, e sem ela a mais
 * nova. `null` sem janela no intervalo.
 */
internal fun defaultSelectedWindow(windows: List<QuotaWindowSummary>): QuotaWindowSummary? {
    return windows.lastOrNull { window -> window.isOpen } ?: windows.lastOrNull()
}

/**
 * Os pontos da série que caem dentro de [window], para a curva do detalhe. Só
 * desenho: no intervalo "Total" eles herdam a reamostragem do gráfico principal,
 * e por isso nenhum número do detalhe sai daqui — sai de [QuotaWindowSummary].
 */
internal fun pointsOfWindow(points: List<UsageHistoryPoint>, window: QuotaWindowSummary): List<UsageHistoryPoint> {
    return points.filter { point -> point.capturedAt >= window.firstObservedAt && point.capturedAt <= window.lastObservedAt }
}

/** Segunda linha do item da lista: pico e, quando esgotou, em quanto tempo. */
internal fun windowListDetail(window: QuotaWindowSummary, language: AppLanguage): String {
    val peak = if (language == AppLanguage.PT) "pico ${window.peakPercent} %" else "peak ${window.peakPercent} %"
    if (window.exhaustedAt == null) {
        return peak
    }
    val exhausted = exhaustionLabel(window)
    return if (language == AppLanguage.PT) "$peak · esgotou em $exhausted" else "$peak · exhausted after $exhausted"
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

/**
 * Faixa ativa da janela (#382): "08:12 → 11:40 · 3h 28min". Os horários são do
 * dia da própria janela; a data já está na coluna de início. "—" sem subida.
 */
internal fun activeSpanLabel(window: QuotaWindowSummary, language: AppLanguage): String {
    val from = window.activeFrom ?: return "—"
    val until = window.activeUntil ?: return "—"
    // Faixa de um ponto só: a janela chegou usada e não subiu depois (#392). Um
    // "21:02 → 21:02 · 0min" se lia como janela ativa por zero minutos.
    if (from == until) {
        return if (language == AppLanguage.PT) "usada antes da 1ª leitura" else "used before the first reading"
    }
    return "${formatClock(from)} → ${formatClock(until)} · ${formatElapsed(from, until)}"
}

private fun formatClock(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
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

/**
 * Legenda sob o gráfico (#382, direção N6): a faixa ativa da janela corrente e
 * o que a faixa clara significa. `null` sem janela aberta com uso.
 */
internal fun currentActiveSpanCaption(series: UsageHistorySeries, language: AppLanguage): String? {
    val window = series.windows.lastOrNull { candidate -> candidate.isOpen } ?: return null
    if (window.activeFrom == null) {
        return null
    }
    val span = activeSpanLabel(window, language)
    return if (language == AppLanguage.PT) {
        "Janela atual ativa $span. A faixa clara marca o trecho em que o uso subiu; o resto ficou ocioso."
    } else {
        "Current window active $span. The light band marks where usage rose; the rest was idle."
    }
}
