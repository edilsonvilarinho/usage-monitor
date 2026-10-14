package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AnalysisSpan
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.components.AppTone
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Instant

/**
 * O que o painel único da janela "Análise" mostra (rodada AD, direção AD1), já em texto e
 * tom: funções puras, testadas sem tela. `null` medido vira "—", nunca zero; a cor de cada
 * tom vem sempre com a palavra ou a seta escrita.
 */
internal data class AnalysisWindowCardModel(
    val title: String,
    val percent: Int,
    val projectedPercent: Int?,
    val verdict: String,
    val verdictTone: AppTone,
    val resetLabel: String?,
    val usedLabel: String,
    val projectionLabel: String?,
    val paceNow: String,
    val paceAverage: String,
    val safePace: String,
    /** "folga" ou "acima do seguro"; `null` sem ritmo ou sem ritmo seguro. */
    val safeVerdict: String?,
    val safeTone: AppTone
)

internal data class AnalysisIntervalRowModel(val span: String, val pace: String, val tokensPerSecond: String, val ratio: String, val ratioTone: AppTone)

internal data class AnalysisVersusRowModel(val label: String, val now: String, val before: String, val change: String, val changeTone: AppTone)

/** Uma hora das últimas 24: rótulo "07", pontos da 5h e vazão; `null` = não medido. */
internal data class AnalysisHourBar(val label: String, val points: Double?, val tokensPerSecond: Double?)

internal object AnalysisPanelModel {

    fun windowCard(window: AnalysisWindowView, now: Instant, language: AppLanguage): AnalysisWindowCardModel {
        val pt = language == AppLanguage.PT
        val projected = window.projectedAtReset
        val (verdict, tone) = when {
            projected == null -> (if (pt) "sem projeção" else "no projection") to AppTone.NEUTRAL
            projected >= 100 -> (if (pt) "estoura antes do reinício" else "runs out before the reset") to AppTone.CRITICAL
            else -> (if (pt) "cabe até o reinício" else "fits until the reset") to AppTone.OK
        }
        val current = window.paceNow ?: window.paceAverage
        val safe = window.safePace
        val (safeVerdict, safeTone) = when {
            current == null || safe == null -> null to AppTone.NEUTRAL
            current <= safe -> (if (pt) "folga" else "room left") to AppTone.OK
            else -> (if (pt) "acima do seguro" else "above the safe pace") to AppTone.CRITICAL
        }
        return AnalysisWindowCardModel(
            title = if (window.periodType == PeriodType.WEEKLY) (if (pt) "Semanal" else "Weekly") else "5h",
            percent = window.percent,
            projectedPercent = projected,
            verdict = verdict,
            verdictTone = tone,
            resetLabel = window.resetsAt?.let { at -> (if (pt) "reinicia " else "resets ") + TelegramBotMessages.resetLabel(at, now, pt) },
            usedLabel = if (pt) "${window.percent}% usado" else "${window.percent}% used",
            projectionLabel = projected?.let { value -> if (pt) "≈$value% no reinício" else "≈$value% at the reset" },
            paceNow = pace(window.paceNow, pt),
            paceAverage = pace(window.paceAverage, pt),
            safePace = pace(window.safePace, pt),
            safeVerdict = safeVerdict,
            safeTone = safeTone
        )
    }

    /** 1 h a 7 d, com a razão do ritmo contra o de 7 dias — a mesma conta do `/analise intervalos`. */
    fun intervals(analysis: SessionAnalysis, language: AppLanguage): List<AnalysisIntervalRowModel> {
        val pt = language == AppLanguage.PT
        val week = analysis.intervals.firstOrNull { row -> row.span == AnalysisSpan.DAYS_7 }?.pacePerHour
        return analysis.intervals.map { row ->
            val value = row.pacePerHour
            val (ratio, tone) = when {
                row.span == AnalysisSpan.DAYS_7 -> (if (pt) "referência" else "reference") to AppTone.NEUTRAL
                value == null || week == null || week <= 0.0 -> "—" to AppTone.NEUTRAL
                value >= week -> "↑ ${pace(value / week, pt)}×" to AppTone.WARNING
                else -> "↓ ${pace(value / week, pt)}×" to AppTone.OK
            }
            AnalysisIntervalRowModel(TelegramAnalysisTexts.spanLabel(row.span), pace(value, pt), speed(row.tokensPerSecond), ratio, tone)
        }
    }

    /**
     * A janela 5h corrente contra a anterior no mesmo ponto; `null` sem janela anterior no
     * histórico. Nas linhas de consumo, descer é folga; a vazão não tem lado bom.
     */
    fun versus(analysis: SessionAnalysis, language: AppLanguage): List<AnalysisVersusRowModel>? {
        val pt = language == AppLanguage.PT
        val previous = analysis.previous ?: return null
        val today = analysis.days.lastOrNull()?.percentPoints
        val yesterday = analysis.days.getOrNull(analysis.days.size - 2)?.percentPoints
        val elapsed = elapsedLabel(previous.elapsedMillis)
        return listOf(
            versusRow((if (pt) "5h aos " else "5h at ") + elapsed, previous.currentPercent.toDouble(), previous.previousPercent?.toDouble(), true) { value -> "${value.roundToInt()}%" },
            versusRow(if (pt) "ritmo médio" else "avg pace", previous.currentPace, previous.previousPace, true) { value -> pace(value, pt) },
            versusRow(if (pt) "hora pesada" else "peak hour", previous.currentPeakHour, previous.previousPeakHour, true) { value -> "${value.roundToInt()} pp" },
            versusRow("tok/s", previous.currentTokensPerSecond, previous.previousTokensPerSecond, false) { value -> value.roundToInt().toString() },
            versusRow(if (pt) "semanal/dia" else "weekly/day", today, yesterday, true) { value -> pace(value, pt) }
        )
    }

    fun versusFootnote(analysis: SessionAnalysis, language: AppLanguage): String? {
        val previous = analysis.previous ?: return null
        val elapsed = elapsedLabel(previous.elapsedMillis)
        return if (language == AppLanguage.PT) "mesmo ponto da janela: $elapsed desde o início · semanal: hoje × ontem" else "same point of the window: $elapsed since the start · weekly: today × yesterday"
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
        return if (language == AppLanguage.PT) "pico ${bar.label}h · $points pp" else "peak ${bar.label}h · $points pp"
    }

    /** "23h–24h · 57 pp · 121 tok/s"; hora sem leitura diz que não foi medida. */
    fun hourTooltip(bar: AnalysisHourBar, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val next = ((bar.label.toIntOrNull() ?: 0) + 1).toString().padStart(2, '0')
        val range = "${bar.label}h–${next}h"
        val points = bar.points ?: return "$range · " + (if (pt) "sem leitura" else "no reading")
        val speed = bar.tokensPerSecond?.let { value -> " · ${value.roundToInt()} tok/s" }.orEmpty()
        return "$range · ${pace(points, pt)} pp$speed"
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

    private fun versusRow(label: String, now: Double?, before: Double?, lowerIsBetter: Boolean, format: (Double) -> String): AnalysisVersusRowModel {
        val (change, tone) = if (now == null || before == null || before <= 0.0) {
            "—" to AppTone.NEUTRAL
        } else {
            val percent = ((now - before) / before * 100).roundToInt()
            val mark = if (percent > 0) "↑" else if (percent < 0) "↓" else "="
            val tone = when {
                !lowerIsBetter || percent == 0 -> AppTone.NEUTRAL
                percent < 0 -> AppTone.OK
                else -> AppTone.WARNING
            }
            "$mark ${abs(percent)}%" to tone
        }
        return AnalysisVersusRowModel(label, now?.let(format) ?: "—", before?.let(format) ?: "—", change, tone)
    }

    /** Uma casa, vírgula em português; `null` vira "—". */
    private fun pace(value: Double?, pt: Boolean): String = TelegramAnalysisMessages.pace(value, pt).trim()

    private fun speed(value: Double?): String = value?.roundToInt()?.toString() ?: "—"

    private fun elapsedLabel(millis: Long): String {
        val minutes = millis / 60_000L
        return if (minutes < 60) "${minutes}min" else "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
    }

    private fun clockSeconds(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.of(ACTIVITY_TIME_ZONE_ID))
        return listOf(local.hour, local.minute, local.second).joinToString(":") { part -> part.toString().padStart(2, '0') }
    }
}
