package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PacePathPoint
import com.usagemonitor.domain.entity.QuotaWindowPacePath
import com.usagemonitor.domain.entity.elapsedFractionOf
import com.usagemonitor.domain.entity.exhaustionTimeOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * O que o gráfico da diagonal do ritmo desenha (#463, direção AH1), já resolvido: no eixo X o
 * tempo da janela que passou, no Y a cota usada, e a diagonal que chega a 100% no reinício.
 * Puro, testado sem tela; a mesma peça serve a "Análise" (a janela aberta e as anteriores atrás)
 * e o "Histórico" (todas as janelas do período, a escolhida em destaque).
 */
internal data class PaceDiagonalModel(
    /** A janela em destaque, em ordem de leitura. */
    val current: List<PacePathPoint>,
    /** As outras janelas, atrás, em cinza; da mais velha à mais nova. */
    val previous: List<List<PacePathPoint>>,
    /** Do agora até onde a cota chega: o reinício, ou o instante em que esgota e depois 100%. */
    val projection: List<PacePathPoint>,
    val projectionCritical: Boolean,
    /** "64% · 97% do tempo" junto do ponto mais novo; `null` sem ponto. */
    val pointLabel: String?,
    /** Início, meio e reinício, na escala de tempo da janela. */
    val axisLabels: List<String>,
    /** A frase que o leitor de tela lê no lugar do desenho. */
    val description: String
)

internal object PaceDiagonalLabels {
    fun above(language: AppLanguage) = localized(language, "acima da linha: esgota antes", "above the line: runs out first")
    fun below(language: AppLanguage) = localized(language, "abaixo: cabe", "below: fits")
    fun timeAxis(language: AppLanguage) = localized(language, "tempo da janela que já passou →", "time of the window that has passed →")
}

internal object PaceDiagonalModels {

    /**
     * A janela aberta da "Análise": o caminho dela, as anteriores atrás e a projeção no ritmo
     * da última hora (sem ele, no médio) — o mesmo ritmo de [AnalysisWindowView.projectedAtReset].
     * `null` sem início e reinício conhecidos: sem eles não há eixo do tempo.
     */
    fun forWindow(window: AnalysisWindowView, now: Instant, language: AppLanguage, description: String): PaceDiagonalModel? {
        val startsAt = window.startsAt ?: return null
        val resetsAt = window.resetsAt ?: return null
        val fraction = elapsedFractionOf(startsAt, resetsAt, now) ?: return null
        val nowPoint = PacePathPoint(fraction, window.percent.toDouble())
        // O caminho termina na leitura de agora: o percentual do card pode ser mais novo que o último ponto lido.
        val path = window.path?.points.orEmpty()
        val last = path.lastOrNull()
        val current = if (last != null && last.elapsedFraction >= nowPoint.elapsedFraction && last.percent == nowPoint.percent) path else path + nowPoint
        val projected = window.projectedAtReset
        // O mesmo critério do veredito do card: só esgota no desenho quando a projeção chega a 100%.
        val eta = if ((projected ?: 0) >= 100) exhaustionTimeOf(window.percent, window.paceNow ?: window.paceAverage, now, resetsAt) else null
        val projection = when {
            eta != null -> listOf(nowPoint, PacePathPoint(elapsedFractionOf(startsAt, resetsAt, eta) ?: 1.0, 100.0), PacePathPoint(1.0, 100.0))
            projected != null -> listOf(nowPoint, PacePathPoint(1.0, projected.toDouble()))
            else -> emptyList()
        }
        return PaceDiagonalModel(
            current = current,
            previous = window.previousPaths.map { path -> path.points },
            projection = projection,
            projectionCritical = eta != null || (projected ?: 0) >= 100,
            pointLabel = "${window.percent}% · ${(fraction * 100).roundToInt()}% " + localized(language, "do tempo", "of time"),
            axisLabels = axisLabels(startsAt, resetsAt),
            description = description
        )
    }

    /**
     * O "Histórico": as janelas do período, a [selected] em destaque e as outras atrás. Sem
     * projeção — o histórico mostra o que aconteceu, não o que vai acontecer.
     */
    fun forHistory(paths: List<QuotaWindowPacePath>, selected: QuotaWindowPacePath, lengthMillis: Long, language: AppLanguage, description: String): PaceDiagonalModel {
        val last = selected.points.lastOrNull()
        return PaceDiagonalModel(
            current = selected.points,
            previous = paths.filter { path -> path != selected }.map { path -> path.points },
            projection = emptyList(),
            projectionCritical = false,
            pointLabel = last?.let { point -> "${point.percent.roundToInt()}%" },
            axisLabels = relativeAxisLabels(lengthMillis, language),
            description = description
        )
    }

    /** Hora do dia numa janela curta; dia e mês numa semanal, que passa de um dia. */
    fun axisLabels(startsAt: Instant, resetsAt: Instant): List<String> {
        val middle = startsAt + ((resetsAt - startsAt).inWholeMilliseconds / 2).milliseconds
        val longWindow = resetsAt - startsAt > 24.hours
        return listOf(startsAt, middle, resetsAt).map { at -> if (longWindow) dayMonth(at) else TelegramBotMessages.clock(at) }
    }

    /** No histórico as janelas começam em horas diferentes: o eixo diz o tempo desde o início. */
    fun relativeAxisLabels(lengthMillis: Long, language: AppLanguage): List<String> =
        listOf(localized(language, "início", "start"), durationLabel(lengthMillis / 2), localized(language, "reinício", "reset"))

    /** "9 min", "1 h 48 min", "3 d 3 h": a maior unidade e a seguinte, sem zeros. */
    fun durationLabel(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(0L)
        val days = minutes / (24 * 60)
        val hours = minutes / 60 % 24
        val rest = minutes % 60
        return when {
            days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
            hours > 0 -> if (rest > 0) "$hours h $rest min" else "$hours h"
            else -> "$rest min"
        }
    }

    /** Pontos acima (positivo) ou abaixo da diagonal agora; `null` sem eixo do tempo. */
    fun gapNow(window: AnalysisWindowView, now: Instant): Int? {
        val fraction = elapsedFractionOf(window.startsAt, window.resetsAt, now) ?: return null
        return (window.percent - fraction * 100).roundToInt()
    }

    private fun dayMonth(at: Instant): String {
        val local = at.toLocalDateTime(TimeZone.of(ACTIVITY_TIME_ZONE_ID))
        return "${local.dayOfMonth.toString().padStart(2, '0')}/${local.monthNumber.toString().padStart(2, '0')}"
    }
}
