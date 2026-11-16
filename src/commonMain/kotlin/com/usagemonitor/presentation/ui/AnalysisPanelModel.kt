package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AnalysisPeriodSummary
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
    /** Frase curta do veredito (#610, D5): a projeção, ou quando esgota; o `verdict` curto é a pílula. */
    val verdictSentence: String,
    val resetLabel: String?,
    /** O que a diagonal mostra, em frase: onde a janela está em relação à linha e como termina. */
    val sentence: String,
    /** Sem início e reinício conhecidos a diagonal não tem linha de agora nem projeção: só as anteriores e o eixo de duração. */
    val diagonal: PaceDiagonalModel,
    /** Fatos da janela (#610, D4): uso, tempo, reinício, esgota só quando esgota, e janelas anteriores. Os ritmos ficam no comparador. */
    val facts: List<AnalysisFact> = emptyList()
)

/** Um fato do card: rótulo e valor já em texto; `null` medido vira "—". */
internal data class AnalysisFact(val label: String, val value: String)

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
        val verdictSentence = when {
            projected == null -> verdict
            projected >= 100 -> overLimitVerdict(window, now, language)
            else -> fitsVerdict(projected, language)
        }
        val sentence = windowSentence(window, now, language)
        return AnalysisWindowCardModel(
            title = if (window.periodType == PeriodType.WEEKLY) localized(language, "Semanal", "Weekly") else "5h",
            verdict = verdict,
            verdictTone = tone,
            verdictSentence = verdictSentence,
            resetLabel = window.resetsAt?.let { at -> localized(language, "reinicia ", "resets ") + resetClock(at, now, language) },
            sentence = sentence,
            diagonal = PaceDiagonalModels.forWindow(window, now, language, sentence),
            facts = windowFacts(window, now, language)
        )
    }

    /** Frase de quem cabe (#610, D5): a projeção no reinício, curta; o ritmo usado fica no comparador. */
    private fun fitsVerdict(projected: Int, language: AppLanguage): String =
        localized(language, "Projeção de $projected % no reinício, abaixo dos 100 %.", "Projection of $projected% at the reset, under 100%.")

    /** Frase de quem esgota (#610, D5): quando e quanto antes do reinício; sem ritmo ou reinício, só o veredito. */
    private fun overLimitVerdict(window: AnalysisWindowView, now: Instant, language: AppLanguage): String {
        val runsOut = localized(language, "Esgota antes do reinício.", "Runs out before the reset.")
        val resetsAt = window.resetsAt ?: return runsOut
        val eta = exhaustionTimeOf(window.percent, window.paceNow ?: window.paceAverage, now, resetsAt) ?: return runsOut
        val before = PaceDiagonalModels.durationLabel((resetsAt - eta).inWholeMilliseconds)
        val at = PaceDiagonalModels.weekdayClock(eta, language)
        return localized(language, "Esgota $at, $before antes do reinício.", "Runs out $at, $before before the reset.")
    }

    /**
     * Fatos do card (#610, D4): uso com o quanto do tempo já passou, tempo decorrido, reinício, "Esgota"
     * só quando a projeção chega a 100%, e a contagem de janelas anteriores. Medido ausente vira "—".
     */
    private fun windowFacts(window: AnalysisWindowView, now: Instant, language: AppLanguage): List<AnalysisFact> {
        val startsAt = window.startsAt
        val resetsAt = window.resetsAt
        val gap = PaceDiagonalModels.gapNow(window, now)
        val used = if (gap == null) "${window.percent}%" else {
            val elapsed = window.percent - gap
            localized(language, "${window.percent}% · $elapsed% do tempo", "${window.percent}% · $elapsed% of the time")
        }
        val fixed = listOf(
            AnalysisFact(localized(language, "Uso", "Used"), used),
            AnalysisFact(localized(language, "Decorrido", "Elapsed"), if (startsAt == null) "—" else PaceDiagonalModels.durationLabel((now - startsAt).inWholeMilliseconds)),
            AnalysisFact(localized(language, "Reinício", "Reset"), if (resetsAt == null) "—" else resetClock(resetsAt, now, language))
        )
        val previous = AnalysisFact(localized(language, "Janelas anteriores", "Previous windows"), window.previousPaths.size.toString())
        return fixed + listOfNotNull(runsOutFact(window, now, language), previous)
    }

    /** "Esgota" só quando a projeção chega a 100%: o instante e quanto falta para o reinício. */
    private fun runsOutFact(window: AnalysisWindowView, now: Instant, language: AppLanguage): AnalysisFact? {
        val resetsAt = window.resetsAt ?: return null
        if ((window.projectedAtReset ?: 0) < 100) return null
        val eta = exhaustionTimeOf(window.percent, window.paceNow ?: window.paceAverage, now, resetsAt) ?: return null
        val before = PaceDiagonalModels.durationLabel((resetsAt - eta).inWholeMilliseconds)
        return AnalysisFact(
            localized(language, "Esgota", "Runs out"),
            "${PaceDiagonalModels.weekdayClock(eta, language)} · " + localized(language, "$before antes", "$before before")
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
            ?: return used + localized(language, "; sem janela aberta, o gráfico mostra só as anteriores.", "; no open window, the chart shows only the previous ones.")
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

    /**
     * Resumo do período: a 5h só nas 24 h; a semanal em 7 dias, 30 dias e tudo. `null` no "agora" e sem
     * janela fechada no período.
     */
    fun periodSummary(analysis: SessionAnalysis, language: AppLanguage): String? = when (analysis.period) {
        AnalysisPeriod.NOW -> null
        AnalysisPeriod.HOURS_24 -> analysis.fiveHourSummary?.let { summary ->
            summaryText(summary, localized(language, "janelas de 5 h nas últimas 24 h", "windows of 5 h in the last 24 h"), language)
        }
        else -> analysis.weeklySummary?.let { summary -> summaryText(summary, localized(language, "semanas anteriores", "previous weeks"), language) }
    }

    /** Título das barras do período; `null` no "agora", que não tem barras. */
    fun barsTitle(period: AnalysisPeriod, language: AppLanguage): String? = when (period) {
        AnalysisPeriod.NOW -> null
        AnalysisPeriod.HOURS_24 -> localized(language, "Últimas 24 h", "Last 24 h")
        AnalysisPeriod.DAYS_7 -> localized(language, "Pico por dia · 7 d", "Peak per day · 7 d")
        AnalysisPeriod.DAYS_30 -> localized(language, "Pico por semana · 30 d", "Peak per week · 30 d")
        AnalysisPeriod.ALL -> localized(language, "Pico por semana · tudo", "Peak per week · all")
    }

    fun intervalsTitle(period: AnalysisPeriod, language: AppLanguage): String {
        val label = periodLabel(period, language)
        return localized(language, "Ritmo por intervalo · $label", "Pace by interval · $label")
    }

    /**
     * Uma linha por janela de 5 h, com a razão do ritmo contra o de 7 dias — a mesma conta do
     * `/analise intervalos`. Em 30 dias e tudo, a linha do período entra no fim.
     */
    fun intervals(analysis: SessionAnalysis, language: AppLanguage): List<AnalysisIntervalRowModel> {
        val pt = language == AppLanguage.PT
        val week = analysis.intervals.firstOrNull { row -> row.span == AnalysisSpan.DAYS_7 }?.pacePerHour
        val rows = analysis.intervals.map { row ->
            val (ratio, tone) = if (row.span == AnalysisSpan.DAYS_7) {
                (if (pt) "referência" else "reference") to AppTone.NEUTRAL
            } else {
                ratioVsWeek(row.pacePerHour, week, language, pt)
            }
            AnalysisIntervalRowModel(TelegramAnalysisTexts.spanLabel(row.span), pace(row.pacePerHour, pt), speed(row.tokensPerSecond), ratio, tone)
        }
        val rate = analysis.periodRate ?: return rows
        val (ratio, tone) = ratioVsWeek(rate.pacePerHour, week, language, pt)
        return rows + AnalysisIntervalRowModel(periodLabel(rate.period, language), pace(rate.pacePerHour, pt), speed(rate.tokensPerSecond), ratio, tone)
    }

    private fun ratioVsWeek(value: Double?, week: Double?, language: AppLanguage, pt: Boolean): Pair<String, AppTone> = when {
        value == null || week == null || week <= 0.0 -> "—" to AppTone.NEUTRAL
        value >= week -> "↑ ${pace(value / week, pt)}× " + localized(language, "mais rápido", "faster") to AppTone.WARNING
        else -> "↓ ${pace(value / week, pt)}× " + localized(language, "mais lento", "slower") to AppTone.OK
    }

    private fun summaryText(summary: AnalysisPeriodSummary, noun: String, language: AppLanguage): String {
        val exhausted = localized(language, "${summary.exhaustedCount} de ${summary.windowCount} esgotaram", "${summary.exhaustedCount} of ${summary.windowCount} ran out")
        val peak = summary.meanPeakPercent?.let { value -> localized(language, "$value %", "$value%") } ?: "—"
        return "${summary.windowCount} $noun · $exhausted · " + localized(language, "pico médio $peak", "mean peak $peak")
    }

    private fun periodLabel(period: AnalysisPeriod, language: AppLanguage): String = when (period) {
        AnalysisPeriod.NOW -> localized(language, "agora", "now")
        AnalysisPeriod.HOURS_24 -> "24 h"
        AnalysisPeriod.DAYS_7 -> "7 d"
        AnalysisPeriod.DAYS_30 -> "30 d"
        AnalysisPeriod.ALL -> localized(language, "tudo", "all")
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
