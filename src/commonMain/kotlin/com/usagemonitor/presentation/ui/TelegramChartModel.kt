package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isSamePeriod
import kotlin.time.Instant

/** Uma linha do gráfico do `/grafico` (#398, Y6): uso de 0 a 100 no tempo. */
data class TelegramChartLine(
    val label: String,
    /** (instante em ms, percentual 0–100), em ordem de tempo e só dentro do intervalo. */
    val points: List<Pair<Long, Float>>,
    /** Instantes em que a janela da cota virou — linhas verticais no gráfico. */
    val resets: List<Long>,
    /** O último percentual, escrito na legenda: cor nunca informa sozinha. */
    val lastPercent: Int
)

data class TelegramChart(
    val startMillis: Long,
    val endMillis: Long,
    val lines: List<TelegramChartLine>
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** Teto de linhas: além disso a legenda não cabe e as cores deixam de se distinguir. */
const val TELEGRAM_CHART_MAX_LINES = 8

/**
 * Do histórico ao gráfico do bot (#398, Y6). Função pura: entra cada relatório com
 * o nome da conta, sai uma linha por cota com janela conhecida.
 *
 * Saldo em dinheiro fica de fora (não é "uso de 0 a 100"), e série sem ponto no
 * intervalo também — linha vazia na legenda diria um número que não foi medido.
 */
fun buildTelegramChart(reports: List<Pair<String, ApiUsageHistoryReport>>, range: HistoryRange, now: Instant): TelegramChart {
    val start = range.windowStart(now).toEpochMilliseconds()
    val end = now.toEpochMilliseconds()
    val lines = reports.flatMap { (accountName, report) ->
        report.series
            .filter { series -> series.unit != UsageUnit.CURRENCY_USD }
            .mapNotNull { series ->
                val inside = series.points
                    .filter { point -> point.capturedAt.toEpochMilliseconds() in start..end && point.hasKnownResetAt && point.displayTotal > 0L }
                    .sortedBy { point -> point.capturedAt }
                if (inside.isEmpty()) return@mapNotNull null
                val resets = inside.zipWithNext()
                    .filter { (before, after) -> !isSamePeriod(before.periodEndAt, after.periodEndAt) }
                    .map { (_, after) -> after.capturedAt.toEpochMilliseconds() }
                TelegramChartLine(
                    label = "$accountName · ${series.quotaLabel}",
                    points = inside.map { point -> point.capturedAt.toEpochMilliseconds() to point.normalizedUsage * 100f },
                    resets = resets,
                    lastPercent = (inside.last().normalizedUsage * 100f).toInt()
                )
            }
    }
    return TelegramChart(start, end, lines.take(TELEGRAM_CHART_MAX_LINES))
}

/** Legenda da foto e a resposta sem dado, no idioma do app. */
internal object TelegramChartMessages {

    fun caption(range: HistoryRange, chart: TelegramChart, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val span = when (range) {
            HistoryRange.LAST_7_DAYS -> if (pt) "7 dias" else "7 days"
            else -> "24 h"
        }
        val end = TelegramBotMessages.clock(Instant.fromEpochMilliseconds(chart.endMillis))
        val head = if (pt) "<b>📈 Uso nas últimas $span</b>" else "<b>📈 Usage over the last $span</b>"
        val tail = if (pt) "<i>até $end BRT · linhas tracejadas: reinício da cota</i>" else "<i>until $end BRT · dashed lines: quota reset</i>"
        return "$head\n$tail"
    }

    fun empty(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Sem histórico no intervalo. O gráfico aparece depois de algumas coletas."
    } else {
        "No history in this range. The chart shows up after a few collections."
    }
}
