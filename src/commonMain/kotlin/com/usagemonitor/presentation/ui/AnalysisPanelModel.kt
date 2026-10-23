package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AnalysisSpan
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.exhaustionTimeOf
import com.usagemonitor.presentation.ui.components.AppTone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * O que o painel único da janela "Análise" mostra (rodada AD; janelas na diagonal do ritmo
 * desde a #463, direção AH1), já em texto e tom: funções puras, testadas sem tela. `null` medido vira "—", nunca zero; a cor de cada
 * tom vem sempre com a palavra ou a seta escrita.
 */
internal data class AnalysisWindowCardModel(
    val title: String,
    val verdict: String,
    val verdictTone: AppTone,
    val resetLabel: String?,
    /** O que a diagonal mostra, em frase: onde a janela está em relação à linha e como termina. */
    val sentence: String,
    /** `null` sem início e reinício conhecidos: sem eixo do tempo, só a frase. */
    val diagonal: PaceDiagonalModel?
)

internal data class AnalysisIntervalRowModel(val span: String, val pace: String, val tokensPerSecond: String, val ratio: String, val ratioTone: AppTone)

/** Uma hora das últimas 24: rótulo "07", pontos da 5h e vazão; `null` = não medido. */
internal data class AnalysisHourBar(val label: String, val points: Double?, val tokensPerSecond: Double?)

internal object AnalysisPanelModel {

    fun windowCard(window: AnalysisWindowView, now: Instant, language: AppLanguage): AnalysisWindowCardModel {
        val projected = window.projectedAtReset
        val (verdict, tone) = when {
            projected == null -> localized(language, "sem projeção", "no projection") to AppTone.NEUTRAL
            projected >= 100 -> localized(language, "esgota antes do reinício", "runs out before the reset") to AppTone.CRITICAL
            else -> localized(language, "cabe até o reinício", "fits until the reset") to AppTone.OK
        }
        val sentence = windowSentence(window, now, language)
        return AnalysisWindowCardModel(
            title = if (window.periodType == PeriodType.WEEKLY) localized(language, "Semanal", "Weekly") else "5h",
            verdict = verdict,
            verdictTone = tone,
            resetLabel = window.resetsAt?.let { at -> localized(language, "reinicia ", "resets ") + resetClock(at, now, language) },
            sentence = sentence,
            diagonal = PaceDiagonalModels.forWindow(window, now, language, sentence)
        )
    }

    /**
     * A diagonal em frase (#463, AH1): quanto da cota e do tempo já foi, quantos pontos acima ou
     * abaixo da linha que chega a 100% no reinício, e como a janela termina — quando esgota, no
     * ritmo da projeção, ou onde chega. O ritmo seguro só entra assim: "safe 249.9" com 9 min
     * para o reinício não decidia nada.
     */
    fun windowSentence(window: AnalysisWindowView, now: Instant, language: AppLanguage): String {
        val used = localized(language, "${window.percent}% usada", "${window.percent}% used")
        val gap = PaceDiagonalModels.gapNow(window, now)
            ?: return used + localized(language, "; sem reinício conhecido, não há linha de ritmo.", "; with no known reset there is no pace line.")
        val elapsed = window.percent - gap
        val span = if (window.periodType == PeriodType.WEEKLY) localized(language, "da semana", "of the week") else localized(language, "da janela", "of the window")
        val points = if (abs(gap) == 1) localized(language, "1 ponto", "1 point") else localized(language, "${abs(gap)} pontos", "${abs(gap)} points")
        val position = when {
            gap > 0 -> localized(language, "$points acima da linha", "$points over the line")
            gap < 0 -> localized(language, "$points abaixo da linha", "$points under the line")
            else -> localized(language, "em cima da linha", "right on the line")
        }
        val first = localized(language, "$used com $elapsed% $span já passada: $position.", "$used with $elapsed% $span gone: $position.")
        return listOfNotNull(first, endingSentence(window, now, language)).joinToString(" ")
    }

    private fun endingSentence(window: AnalysisWindowView, now: Instant, language: AppLanguage): String? {
        val resetsAt = window.resetsAt ?: return null
        val pace = window.paceNow ?: window.paceAverage
        val paceName = if (window.paceNow != null) localized(language, "No ritmo da última hora", "At the last hour's pace")
        else localized(language, "No ritmo médio da janela", "At the window's average pace")
        // O veredito do card decide (projeção ≥ 100%); o instante sai do mesmo ritmo.
        val eta = if ((window.projectedAtReset ?: 0) >= 100) exhaustionTimeOf(window.percent, pace, now, resetsAt) else null
        if (eta != null && pace != null) {
            val before = PaceDiagonalModels.durationLabel((resetsAt - eta).inWholeMilliseconds)
            val at = resetClock(eta, now, language)
            return localized(
                language,
                "$paceName (${paceLabel(pace, language)}), esgota por volta de $at, $before antes do reinício.",
                "$paceName (${paceLabel(pace, language)}) it runs out around $at, $before before the reset."
            )
        }
        val projected = window.projectedAtReset ?: return null
        val at = resetClock(resetsAt, now, language)
        return localized(language, "Chega a ~$projected% no reinício ($at).", "Reaches ~$projected% at the reset ($at).")
    }

    private fun resetClock(at: Instant, now: Instant, language: AppLanguage): String =
        TelegramBotMessages.resetLabel(at, now, language == AppLanguage.PT)

    /** 1 h a 7 d, com a razão do ritmo contra o de 7 dias — a mesma conta do `/analise intervalos`. */
    fun intervals(analysis: SessionAnalysis, language: AppLanguage): List<AnalysisIntervalRowModel> {
        val pt = language == AppLanguage.PT
        val week = analysis.intervals.firstOrNull { row -> row.span == AnalysisSpan.DAYS_7 }?.pacePerHour
        return analysis.intervals.map { row ->
            val value = row.pacePerHour
            val (ratio, tone) = when {
                row.span == AnalysisSpan.DAYS_7 -> (if (pt) "referência" else "reference") to AppTone.NEUTRAL
                value == null || week == null || week <= 0.0 -> "—" to AppTone.NEUTRAL
                value >= week -> "↑ ${pace(value / week, pt)}× " + localized(language, "mais rápido", "faster") to AppTone.WARNING
                else -> "↓ ${pace(value / week, pt)}× " + localized(language, "mais lento", "slower") to AppTone.OK
            }
            AnalysisIntervalRowModel(TelegramAnalysisTexts.spanLabel(row.span), pace(value, pt), speed(row.tokensPerSecond), ratio, tone)
        }
    }

    fun hours(analysis: SessionAnalysis): List<AnalysisHourBar> =
        analysis.hours.map { bucket -> AnalysisHourBar(TelegramBotMessages.clock(bucket.start).substring(0, 2), bucket.percentPoints, bucket.tokensPerSecond) }

    /** A hora de maior consumo; `null` sem consumo medido acima de zero. Empate fica com a mais antiga. */
    fun peakIndex(bars: List<AnalysisHourBar>): Int? {
        val peak = bars.withIndex().filter { (_, bar) -> (bar.points ?: 0.0) > 0.0 }.maxByOrNull { (_, bar) -> bar.points ?: 0.0 } ?: return null
        return peak.index
    }

    fun peakLabel(bars: List<AnalysisHourBar>, language: AppLanguage): String {
        val index = peakIndex(bars) ?: return if (language == AppLanguage.PT) "sem consumo nas últimas 24 h" else "no usage in the last 24 h"
        val bar = bars[index]
        val points = (bar.points ?: 0.0).roundToInt()
        return localized(language, "pico ${bar.label}h · $points%", "peak ${bar.label}h · $points%")
    }

    /** "23h–24h · 57,0% · 121 tok/s" (% da cota 5h); hora sem leitura diz que não foi medida. */
    fun hourTooltip(bar: AnalysisHourBar, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val next = ((bar.label.toIntOrNull() ?: 0) + 1).toString().padStart(2, '0')
        val range = "${bar.label}h–${next}h"
        val points = bar.points ?: return "$range · " + (if (pt) "sem leitura" else "no reading")
        val speed = bar.tokensPerSecond?.let { value -> " · ${value.roundToInt()} tok/s" }.orEmpty()
        return "$range · ${pace(points, pt)}%$speed"
    }

    /** Vazão da última hora por modelo, e de quando é o índice quando ele não acompanha o ao vivo (Codex). */
    fun speedLine(analysis: SessionAnalysis, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val speeds = analysis.modelSpeeds.joinToString(" · ") { speed -> "${speed.model} ${speed.tokensPerSecond.roundToInt()}" }
            .ifEmpty { if (pt) "sem medição" else "not measured" }
        val index = analysis.throughputIndexedAt?.let { at -> (if (pt) " · índice do Codex às " else " · Codex index at ") + TelegramBotMessages.clock(at) }.orEmpty()
        return (if (pt) "tok/s na última hora: " else "tok/s in the last hour: ") + speeds + index
    }

    /** "Ao vivo · 06:20:31" (hora da leitura na tela); com a última releitura falha, diz qual leitura está na tela. */
    fun liveLabel(generatedAt: Instant, failedAt: Instant?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (failedAt != null) {
            return if (pt) "Falhou às ${clockSeconds(failedAt)} · leitura das ${clockSeconds(generatedAt)}" else "Failed at ${clockSeconds(failedAt)} · reading from ${clockSeconds(generatedAt)}"
        }
        return if (pt) "Ao vivo · ${clockSeconds(generatedAt)}" else "Live · ${clockSeconds(generatedAt)}"
    }

    /** Uma casa, vírgula em português; `null` vira "—". */
    private fun pace(value: Double?, pt: Boolean): String = TelegramAnalysisMessages.pace(value, pt).trim()

    private fun speed(value: Double?): String = value?.roundToInt()?.toString() ?: "—"

    private fun clockSeconds(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.of(ACTIVITY_TIME_ZONE_ID))
        return listOf(local.hour, local.minute, local.second).joinToString(":") { part -> part.toString().padStart(2, '0') }
    }
}
