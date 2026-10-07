package com.usagemonitor.domain.entity

import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Uma janela de cota (5h, 7d…) observada no intervalo do histórico (issue #320).
 *
 * "Consumido no período" somava os deltas de todas as janelas e dividia pelo total
 * de **uma** — com trinta janelas de 5h numa semana, dava 173% e não respondia a
 * pergunta nenhuma. A pergunta que o usuário faz é por janela: quanto chegou a
 * usar, se esgotou, em quanto tempo.
 *
 * Percentuais sobre o total da própria janela (0–100), não sobre o total da série.
 */
data class QuotaWindowSummary(
    /** Primeiro ponto observado da janela — não o início nominal, que a API não informa. */
    val firstObservedAt: Instant,
    /** Último ponto observado da janela. */
    val lastObservedAt: Instant,
    /** Reinício previsto da janela (`periodEndAt` do último ponto dela). */
    val resetsAt: Instant,
    val peakPercent: Int,
    /** Primeiro ponto em 100%; `null` é "não esgotou", e nunca zero. */
    val exhaustedAt: Instant?,
    /** Quanto subiu dentro da janela, em pontos percentuais. */
    val consumedPercent: Double,
    /** [consumedPercent] por hora observada; `null` sem duas leituras separadas no tempo. */
    val averagePercentPerHour: Double?,
    /** A janela corrente, ainda não reiniciada no último ponto lido. */
    val isOpen: Boolean,
    /**
     * Faixa ativa da janela (#382): da última leitura parada antes da primeira
     * subida até a leitura da última subida. A janela nem sempre esgota, e é esta
     * faixa que diz quanto dela foi de fato usada. A precisão é a do polling — 60 s
     * com sessão CLI, 5 min sem. `null` nos dois quando o uso não subiu na janela.
     */
    val activeFrom: Instant? = null,
    val activeUntil: Instant? = null,
    /**
     * Consumo por hora do dia **só desta janela** (#392), dos pontos crus — no
     * intervalo "Total" os pontos que o gráfico recebe são reamostrados e a soma
     * por hora sairia errada se viesse deles. `null` quando o uso não subiu.
     */
    val hourlyDistribution: QuotaHourlyDistribution? = null
)

/**
 * O resumo das janelas do intervalo.
 *
 * Médias só sobre janelas **fechadas**: a aberta ainda está subindo, e entrar na
 * média puxaria o pico médio para baixo a cada reinício recente. `null` quando não
 * há janela fechada — a média de nada não é zero.
 */
data class QuotaWindowStats(
    val windowCount: Int,
    val exhaustedCount: Int,
    val averagePeakPercent: Double?,
    val averageConsumedPercent: Double?
)

/**
 * Consumo por hora do dia, em pontos percentuais somados (24 posições, 0h a 23h),
 * no fuso de [ACTIVITY_TIME_ZONE_ID]. Ordem fixa por hora: duas leituras iguais
 * dão listas iguais.
 */
data class QuotaHourlyDistribution(val percentByHour: List<Double>) {
    val peakHour: Int?
        get() {
            val max = percentByHour.maxOrNull() ?: return null
            if (max <= 0.0) {
                return null
            }
            return percentByHour.indexOf(max)
        }
}

/**
 * Corta a série nas janelas de cota: um ponto começa janela nova quando o
 * reinício previsto muda (fora da tolerância de [isSamePeriod]) ou, fora do
 * saldo, quando o acumulado cai.
 *
 * É o mesmo critério com que a previsão escolhe o trecho corrente — a última
 * janela daqui é esse trecho —, e os dois moram juntos para não divergirem no
 * tratamento do reinício.
 */
fun splitIntoQuotaWindows(points: List<UsageHistoryPoint>, unit: UsageUnit): List<List<UsageHistoryPoint>> {
    if (points.isEmpty()) {
        return emptyList()
    }

    val windows = mutableListOf<List<UsageHistoryPoint>>()
    var startIndex = 0
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val periodChanged = !isSamePeriod(current.periodEndAt, previous.periodEndAt)
        val resetDetected = if (unit == UsageUnit.CURRENCY_USD) {
            periodChanged
        } else {
            current.displayUsed < previous.displayUsed || periodChanged
        }
        if (resetDetected) {
            windows += points.subList(startIndex, index)
            startIndex = index
        }
    }
    windows += points.subList(startIndex, points.size)
    return windows
}

/**
 * As janelas da série, em ordem cronológica. Vazio para o que não tem janela:
 * saldo, cota reportada sem período e série sem nenhum reinício conhecido.
 *
 * Ponto sem reinício conhecido é leitura ociosa — a 5h da Anthropic sem sessão
 * vem sem `resets_at`, o Antigravity intacto também — e não forma janela. Antes
 * era o **último** ponto que decidia, e uma leitura ociosa apagava todas as
 * janelas anteriores do intervalo.
 */
fun quotaWindowsOf(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    periodType: PeriodType,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): List<QuotaWindowSummary> {
    if (!hasQuotaWindows(points, unit, periodType)) {
        return emptyList()
    }

    // Da série inteira: com a leitura mais nova ociosa, a última janela fecha.
    val lastCapturedAt = points.last().capturedAt
    val windows = splitIntoQuotaWindows(windowedPoints(points), unit)
    return windows.mapIndexed { index, window ->
        val first = window.first()
        val last = window.last()
        val consumed = positiveDeltaOf(window, unit).toDouble() / percentBase(last)
        val hours = (last.capturedAt - first.capturedAt).inWholeMilliseconds / MILLIS_PER_HOUR
        val activeSpan = activeSpanIn(window)
        QuotaWindowSummary(
            firstObservedAt = first.capturedAt,
            lastObservedAt = last.capturedAt,
            resetsAt = last.periodEndAt,
            peakPercent = window.maxOf { point -> percentOf(point) },
            exhaustedAt = window.firstOrNull { point -> point.displayTotal > 0L && point.displayUsed >= point.displayTotal }
                ?.capturedAt,
            consumedPercent = consumed,
            averagePercentPerHour = if (hours > 0.0) consumed / hours else null,
            isOpen = index == windows.lastIndex && last.periodEndAt > lastCapturedAt,
            activeFrom = activeSpan?.let { span -> window[span.first].capturedAt },
            activeUntil = activeSpan?.let { span -> window[span.last].capturedAt },
            hourlyDistribution = hourlyConsumptionOf(window, timeZone).toDistributionOrNull()
        )
    }
}

/**
 * Trecho ativo de uma janela, em índices de [window] (#382).
 *
 * Começa na leitura **anterior** à primeira subida — o uso aconteceu depois
 * dela — e termina na leitura da última subida. Uma janela cuja primeira leitura
 * já tem uso foi usada antes de ser lida: o trecho começa nela. `null` quando o
 * uso não subiu e a janela começou zerada.
 */
fun activeSpanIn(window: List<UsageHistoryPoint>): IntRange? {
    if (window.isEmpty()) {
        return null
    }
    val rises = (1 until window.size).filter { index -> window[index].displayUsed > window[index - 1].displayUsed }
    val usedBeforeFirstReading = window.first().displayUsed > 0L
    if (rises.isEmpty()) {
        return if (usedBeforeFirstReading) 0..0 else null
    }
    val start = if (usedBeforeFirstReading) 0 else rises.first() - 1
    return start..rises.last()
}

/**
 * Trechos ativos de cada janela da série, em índices de [points], para o
 * gráfico (#382, direção N6). Saldo não tem janela nem faixa ativa.
 */
fun activeSpansOf(points: List<UsageHistoryPoint>, unit: UsageUnit): List<IntRange> {
    if (unit == UsageUnit.CURRENCY_USD) {
        return emptyList()
    }
    var offset = 0
    return buildList {
        for (window in splitIntoQuotaWindows(points, unit)) {
            val span = activeSpanIn(window)
            if (span != null) {
                add((span.first + offset)..(span.last + offset))
            }
            offset += window.size
        }
    }
}

fun quotaWindowStatsOf(windows: List<QuotaWindowSummary>): QuotaWindowStats? {
    if (windows.isEmpty()) {
        return null
    }

    val closed = windows.filterNot { window -> window.isOpen }
    return QuotaWindowStats(
        windowCount = windows.size,
        exhaustedCount = windows.count { window -> window.exhaustedAt != null },
        averagePeakPercent = closed.takeIf { it.isNotEmpty() }?.map { it.peakPercent.toDouble() }?.average(),
        averageConsumedPercent = closed.takeIf { it.isNotEmpty() }?.map { it.consumedPercent }?.average()
    )
}

/**
 * Onde, no relógio, o consumo acontece: cada subida entre duas leituras da mesma
 * janela entra na hora local da leitura mais nova. O reinício não entra — ele
 * derruba o acumulado, e contá-lo como consumo negativo apagaria a hora.
 */
fun quotaHourlyDistributionOf(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    periodType: PeriodType,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): QuotaHourlyDistribution? {
    if (!hasQuotaWindows(points, unit, periodType)) {
        return null
    }

    val byHour = DoubleArray(HOURS_PER_DAY)
    splitIntoQuotaWindows(windowedPoints(points), unit).forEach { window ->
        val windowHours = hourlyConsumptionOf(window, timeZone)
        for (hour in 0 until HOURS_PER_DAY) {
            byHour[hour] += windowHours[hour]
        }
    }
    return byHour.toDistributionOrNull()
}

/** Subidas de uma janela somadas na hora local da leitura mais nova; o reinício não entra. */
private fun hourlyConsumptionOf(window: List<UsageHistoryPoint>, timeZone: TimeZone): DoubleArray {
    val byHour = DoubleArray(HOURS_PER_DAY)
    for (index in 1 until window.size) {
        val diff = window[index].displayUsed - window[index - 1].displayUsed
        if (diff > 0L) {
            val hour = window[index].capturedAt.toLocalDateTime(timeZone).hour
            byHour[hour] += diff.toDouble() / percentBase(window[index])
        }
    }
    return byHour
}

private fun DoubleArray.toDistributionOrNull(): QuotaHourlyDistribution? {
    if (all { value -> value <= 0.0 }) {
        return null
    }
    return QuotaHourlyDistribution(toList())
}

private fun hasQuotaWindows(points: List<UsageHistoryPoint>, unit: UsageUnit, periodType: PeriodType): Boolean {
    if (points.isEmpty() || unit == UsageUnit.CURRENCY_USD) {
        return false
    }
    if (periodType == PeriodType.REPORTED) {
        return false
    }
    return points.any(::isWindowedPoint)
}

/** Os pontos que pertencem a alguma janela; os ociosos, sem reinício conhecido, ficam fora. */
private fun windowedPoints(points: List<UsageHistoryPoint>): List<UsageHistoryPoint> {
    return points.filter(::isWindowedPoint)
}

private fun isWindowedPoint(point: UsageHistoryPoint): Boolean {
    return point.hasKnownResetAt && point.displayTotal > 0L
}

/** Divisor que leva unidade crua a pontos percentuais do total da janela. */
private fun percentBase(point: UsageHistoryPoint): Double {
    return point.displayTotal.coerceAtLeast(1L).toDouble() / 100.0
}

private fun percentOf(point: UsageHistoryPoint): Int {
    return kotlin.math.round(point.normalizedUsage * 100f).toInt()
}

private const val MILLIS_PER_HOUR = 3_600_000.0
private const val HOURS_PER_DAY = 24
