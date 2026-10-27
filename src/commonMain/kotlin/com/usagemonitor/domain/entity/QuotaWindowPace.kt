package com.usagemonitor.domain.entity

import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Diagonal do ritmo (#463, direção AH1): cada janela de cota vira um caminho de "quanto do
 * tempo nominal da janela já passou" × "quanto da cota já foi usado". A diagonal de (0, 0%)
 * a (1, 100%) é o ritmo que chega a 100% exatamente no reinício — acima dela a cota esgota
 * antes; abaixo, cabe. É a mesma pergunta para a 5h e para a semanal, na mesma escala.
 *
 * Só desenho e frase: os números de cada janela continuam saindo de [QuotaWindowSummary] e
 * de [AnalysisWindowView].
 */
data class PacePathPoint(
    /** 0 = início nominal da janela, 1 = reinício. */
    val elapsedFraction: Double,
    /** Cota usada, de 0 a 100. */
    val percent: Double
) {
    /** Pontos percentuais acima (positivo) ou abaixo (negativo) da diagonal. */
    val gapToPaceLine: Double get() = percent - elapsedFraction * 100.0
}

/** Uma janela no gráfico da diagonal: o caminho e o trecho de tempo que ele cobre. */
data class QuotaWindowPacePath(
    val startsAt: Instant,
    val resetsAt: Instant,
    /** Em ordem de leitura; o último ponto é a leitura mais nova da janela. */
    val points: List<PacePathPoint>
)

/**
 * Duração nominal da janela, que dá o início dela a partir do reinício. `null` quando a fonte
 * não a define — a intervalar do MiniMax não é de 5 horas e a mensal varia com o mês —, e
 * aí não há diagonal: a tela fica com a vista de antes, em vez de desenhar uma linha falsa.
 */
fun quotaWindowLengthMillis(quotaLabel: String, periodType: PeriodType): Long? = when {
    quotaLabel.endsWith(" 5h") -> FIVE_HOURS_MILLIS
    quotaLabel.endsWith(" 7d") || periodType == PeriodType.WEEKLY -> SEVEN_DAYS_MILLIS
    else -> null
}

/**
 * O caminho de uma janela cujo reinício é [resetsAt]. Leitura de antes do início nominal fica
 * no zero do eixo do tempo (a API pode ter adiantado o reinício); vazio sem leitura.
 */
fun pacePathOf(points: List<UsageHistoryPoint>, resetsAt: Instant, lengthMillis: Long): QuotaWindowPacePath? {
    if (points.isEmpty() || lengthMillis <= 0L) return null
    val startsAt = resetsAt - lengthMillis.milliseconds
    val path = points.sortedBy { point -> point.capturedAt }.map { point ->
        val elapsed = (point.capturedAt - startsAt).inWholeMilliseconds.toDouble() / lengthMillis
        // Em Double, não pelo `normalizedUsage` (Float): 61% sairia 61,0000014.
        val percent = if (point.displayTotal > 0L) point.displayUsed.toDouble() / point.displayTotal * 100.0 else 0.0
        PacePathPoint(elapsed.coerceIn(0.0, 1.0), percent.coerceIn(0.0, 100.0))
    }
    return QuotaWindowPacePath(startsAt, resetsAt, path)
}

/**
 * As janelas da série com reinício conhecido, da mais velha à mais nova, cada uma como
 * caminho — o mesmo corte de [splitIntoQuotaWindows] que o histórico usa.
 */
fun quotaWindowPacePathsOf(points: List<UsageHistoryPoint>, unit: UsageUnit, lengthMillis: Long): List<QuotaWindowPacePath> =
    splitIntoQuotaWindows(points.filter { point -> point.hasKnownResetAt && point.displayTotal > 0L }, unit)
        .mapNotNull { window -> pacePathOf(window, window.last().periodEndAt, lengthMillis) }

/** Fração do tempo da janela que já passou em [now], de 0 a 1; `null` sem início e reinício. */
fun elapsedFractionOf(startsAt: Instant?, resetsAt: Instant?, now: Instant): Double? {
    if (startsAt == null || resetsAt == null || resetsAt <= startsAt) return null
    val fraction = (now - startsAt).inWholeMilliseconds.toDouble() / (resetsAt - startsAt).inWholeMilliseconds
    return fraction.coerceIn(0.0, 1.0)
}

/**
 * Quando a cota chega a 100% mantendo [pacePerHour] a partir de [now]; `null` sem ritmo, com
 * ritmo parado, já esgotada ou quando isso só aconteceria depois de [resetsAt] — aí ela cabe.
 */
fun exhaustionTimeOf(percent: Int, pacePerHour: Double?, now: Instant, resetsAt: Instant?): Instant? {
    if (pacePerHour == null || pacePerHour <= 0.0 || percent >= 100) return null
    val hoursLeft = (100 - percent) / pacePerHour
    val at = now + (hoursLeft * MILLIS_PER_HOUR).toLong().milliseconds
    return if (resetsAt != null && at >= resetsAt) null else at
}

private const val MILLIS_PER_HOUR = 3_600_000.0
private val FIVE_HOURS_MILLIS = 5.hours.inWholeMilliseconds
private val SEVEN_DAYS_MILLIS = (7 * 24).hours.inWholeMilliseconds
