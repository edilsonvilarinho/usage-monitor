package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AnalysisSpan
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/**
 * As vistas de texto da análise além do painel (rodada AB). Monoespaçadas dentro de `<pre>`,
 * escapadas por inteiro; `null` medido vira "—" e espaço na linha de tendência.
 */
internal object TelegramAnalysisTexts {

    /**
     * AB2 · tendência: uma linha por métrica nas últimas horas, com o valor de agora e a seta
     * contra o trecho anterior. O percentual da 5h sai das fatias da janela corrente, que
     * começa em zero — fora dela não há como saber o acumulado.
     */
    fun trend(analysis: SessionAnalysis, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        var accumulated = 0.0
        val windowPercent = analysis.slices.map { slice -> slice.percentPoints?.let { value -> accumulated += value; accumulated } }.takeLast(TREND_POINTS)
        val hours = analysis.hours.takeLast(TREND_POINTS)
        val lines = listOf(
            trendLine("5h %", windowPercent, analysis.fiveHour?.percent?.let { value -> "$value%" }, analysis.slices.lastOrNull()?.percentPoints?.let { value -> "+${TelegramAnalysisMessages.pace(value, pt).trim()} pp" }),
            trendLine("pp/h", hours.map { bucket -> bucket.percentPoints }, analysis.fiveHour?.paceNow?.let { value -> TelegramAnalysisMessages.pace(value, pt).trim() }, arrow(hours.map { bucket -> bucket.percentPoints }, pt)),
            trendLine("tok/s", hours.map { bucket -> bucket.tokensPerSecond }, hours.lastOrNull()?.tokensPerSecond?.roundToInt()?.toString(), arrow(hours.map { bucket -> bucket.tokensPerSecond }, pt)),
            trendLine(if (pt) "semanal" else "weekly", analysis.days.takeLast(TREND_POINTS).map { day -> day.percentPoints }, analysis.weekly?.percent?.let { value -> "$value%" }, analysis.days.lastOrNull()?.percentPoints?.let { value -> "+${TelegramAnalysisMessages.pace(value, pt).trim()}" })
        )
        val title = "<b>📐 ${TelegramBotMessages.escape(analysis.accountLabel)} · " + (if (pt) "tendência" else "trend") + "</b>"
        val footer = if (pt) "<i>5h % em fatias de 30 min · pp/h e tok/s por hora · semanal por dia; seta contra o trecho anterior</i>" else "<i>5h % in 30 min slices · pp/h and tok/s per hour · weekly per day; arrow against the previous step</i>"
        return "$title\n<pre>${TelegramBotMessages.escape(lines.joinToString("\n"))}</pre>\n$footer"
    }

    /**
     * AB3 · intervalos lado a lado: 1 h a 7 d, cada um com o ritmo da 5h, a vazão e a razão
     * do ritmo contra o de 7 dias — "estou mais rápido que o normal?".
     */
    fun intervals(analysis: SessionAnalysis, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val week = analysis.intervals.firstOrNull { row -> row.span == AnalysisSpan.DAYS_7 }?.pacePerHour
        val head = "".padEnd(7) + "pp/h 5h".padStart(9) + "tok/s".padStart(8) + "vs 7 d".padStart(9)
        val rows = analysis.intervals.map { row ->
            val ratio = when {
                row.span == AnalysisSpan.DAYS_7 -> "—"
                row.pacePerHour == null || week == null || week <= 0.0 -> "—"
                else -> (if (row.pacePerHour >= week) "↑ " else "↓ ") + TelegramAnalysisMessages.pace(row.pacePerHour / week, pt).trim() + "×"
            }
            spanLabel(row.span).padEnd(7) + TelegramAnalysisMessages.pace(row.pacePerHour, pt).padStart(9) +
                (row.tokensPerSecond?.roundToInt()?.toString() ?: "—").padStart(8) + ratio.padStart(9)
        }
        val title = "<b>📐 ${TelegramBotMessages.escape(analysis.accountLabel)} · " + (if (pt) "ritmo por intervalo" else "pace by interval") + "</b>"
        val footer = if (pt) "<i>pp/h da 5h: subida entre leituras ÷ tempo · tok/s: soma de tokens ÷ soma do tempo</i>" else "<i>5h pp/h: rise between readings ÷ time · tok/s: total tokens ÷ total time</i>"
        return "$title\n<pre>${TelegramBotMessages.escape((listOf(head) + rows).joinToString("\n"))}</pre>\n$footer"
    }

    /**
     * AB4 · leitura em frases: o que os números dizem, frase por frase. Frase sem dado medido
     * não sai — sem janela anterior, a comparação some em vez de virar "0% abaixo".
     */
    fun sentences(analysis: SessionAnalysis, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val lines = mutableListOf<String>()
        analysis.fiveHour?.let { window ->
            val reset = window.resetsAt?.let { at -> TelegramBotMessages.clock(at) }
            val ending = window.projectedAtReset?.let { value ->
                val verdict = if (value >= 100) (if (pt) "<b>estoura antes</b>" else "<b>runs out before</b>") else (if (pt) "<b>não estoura</b>" else "<b>does not run out</b>")
                if (pt) " e chega a <b>~$value%</b>" + (reset?.let { at -> " no reinício das $at" } ?: "") + " — $verdict"
                else " and reaches <b>~$value%</b>" + (reset?.let { at -> " at the $at reset" } ?: "") + " — $verdict"
            }.orEmpty()
            lines += (if (pt) "A 5h está em <b>${window.percent}%</b>" else "The 5h window is at <b>${window.percent}%</b>") + "$ending."
        }
        analysis.previous?.let { previous ->
            val current = previous.currentPace
            val before = previous.previousPace
            if (current != null && before != null && before > 0.0) {
                val change = ((current - before) / before * 100).roundToInt()
                val direction = if (change <= 0) (if (pt) "abaixo" else "below") else (if (pt) "acima" else "above")
                lines += if (pt) {
                    "Ritmo médio da janela <b>${TelegramAnalysisMessages.pace(current, true).trim()} pp/h</b>, <b>${kotlin.math.abs(change)}% $direction</b> da janela anterior no mesmo ponto."
                } else {
                    "Window average pace <b>${TelegramAnalysisMessages.pace(current, false).trim()} pp/h</b>, <b>${kotlin.math.abs(change)}% $direction</b> the previous window at the same point."
                }
            }
        }
        analysis.hours.filter { bucket -> (bucket.percentPoints ?: 0.0) > 0.0 }.maxByOrNull { bucket -> bucket.percentPoints ?: 0.0 }?.let { peak ->
            val from = TelegramBotMessages.clock(peak.start).substring(0, 2)
            val points = (peak.percentPoints ?: 0.0).roundToInt()
            lines += if (pt) "Hora mais pesada nas últimas 24 h: <b>${from}h</b> ($points pp)." else "Heaviest hour in the last 24 h: <b>${from}h</b> ($points pp)."
        }
        analysis.weekly?.let { window ->
            val room = window.safePace?.let { value -> TelegramAnalysisMessages.pace(value, pt).trim() }
            lines += if (pt) {
                "Semanal em <b>${window.percent}%</b>" + (room?.let { value -> "; até o reinício cabem <b>$value pp/h</b>." } ?: ".")
            } else {
                "Weekly at <b>${window.percent}%</b>" + (room?.let { value -> "; until the reset there is room for <b>$value pp/h</b>." } ?: ".")
            }
        }
        if (analysis.modelSpeeds.isNotEmpty()) {
            val speeds = analysis.modelSpeeds.joinToString(", ") { speed -> "<b>${TelegramBotMessages.escape(speed.model)} ${speed.tokensPerSecond.roundToInt()} tok/s</b>" }
            lines += (if (pt) "Na última hora: " else "In the last hour: ") + "$speeds."
        }
        val title = "<b>📐 ${TelegramBotMessages.escape(analysis.accountLabel)} · " + (if (pt) "leitura" else "reading") + "</b>"
        if (lines.isEmpty()) return title + "\n" + (if (pt) "Sem leitura suficiente para comentar ainda." else "Not enough readings to comment on yet.")
        return title + "\n" + lines.joinToString("\n") { line -> "• $line" }
    }

    /**
     * AB5 · linha do tempo da janela 5h: uma fatia de 30 min por linha com a barra do que ela
     * consumiu (sobre a maior fatia), os pontos, o ritmo da fatia e a vazão. A última fatia é
     * parcial: o ritmo divide pelo tempo que ela já durou.
     */
    fun timeline(analysis: SessionAnalysis, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val title = "<b>📐 ${TelegramBotMessages.escape(analysis.accountLabel)} · " + (if (pt) "janela 5h" else "5h window") + "</b>"
        val window = analysis.fiveHour
        if (analysis.slices.isEmpty() || window == null) {
            return title + "\n" + (if (pt) "Nenhuma janela 5h aberta agora." else "No 5h window open right now.")
        }
        val max = analysis.slices.mapNotNull { slice -> slice.percentPoints }.maxOrNull()?.takeIf { value -> value > 0.0 }
        val lines = analysis.slices.map { slice ->
            val points = slice.percentPoints
            val cells = if (points == null || max == null) 0 else ((points / max) * BAR_CELLS).roundToInt().coerceIn(0, BAR_CELLS)
            val hours = (minOf(slice.start + SLICE, analysis.generatedAt) - slice.start).inWholeMilliseconds / MILLIS_PER_HOUR
            val rate = if (points == null || hours <= 0.0) "—" else "${(points / hours).roundToInt()}/h"
            TelegramBotMessages.clock(slice.start) + " " + "▰".repeat(cells) + "▱".repeat(BAR_CELLS - cells) +
                (points?.roundToInt()?.let { value -> "$value pp" } ?: "—").padStart(7) + rate.padStart(6) +
                (slice.tokensPerSecond?.roundToInt()?.let { value -> "$value t/s" } ?: "—").padStart(8)
        }
        val reset = window.resetsAt?.let { at -> (if (pt) " · reinicia " else " · resets ") + TelegramBotMessages.clock(at) }.orEmpty()
        val footer = "<i>" + (if (pt) "total " else "total ") + "${window.percent}%$reset</i>"
        return "$title\n<pre>${TelegramBotMessages.escape(lines.joinToString("\n"))}</pre>\n$footer"
    }

    /** Mesmo rótulo nos dois idiomas: "h" e "d" são unidades, não palavras. */
    fun spanLabel(span: AnalysisSpan): String = when (span) {
        AnalysisSpan.HOUR_1 -> "1 h"
        AnalysisSpan.HOURS_3 -> "3 h"
        AnalysisSpan.HOURS_5 -> "5 h"
        AnalysisSpan.HOURS_24 -> "24 h"
        AnalysisSpan.DAYS_7 -> "7 d"
    }

    private fun trendLine(name: String, values: List<Double?>, current: String?, change: String?): String =
        name.padEnd(8) + " " + spark(values).padEnd(TREND_POINTS) + "  " + (current ?: "—").padStart(5) + "  " + (change ?: "")

    /** Blocos ▁ a █ sobre o maior valor da linha; não medido é espaço, para a linha não inventar ponto. */
    fun spark(values: List<Double?>): String {
        val max = values.filterNotNull().maxOrNull()?.takeIf { value -> value > 0.0 } ?: return values.joinToString("") { value -> if (value == null) " " else BLOCKS.first().toString() }
        return values.joinToString("") { value ->
            if (value == null) " " else BLOCKS[((value / max) * (BLOCKS.length - 1)).roundToInt().coerceIn(0, BLOCKS.length - 1)].toString()
        }
    }

    /** Seta do último valor medido contra o anterior medido, com a diferença. */
    private fun arrow(values: List<Double?>, pt: Boolean): String? {
        val measured = values.filterNotNull()
        if (measured.size < 2) return null
        val change = measured.last() - measured[measured.size - 2]
        val mark = when {
            change > 0.05 -> "↑"
            change < -0.05 -> "↓"
            else -> "="
        }
        return "$mark ${TelegramAnalysisMessages.pace(kotlin.math.abs(change), pt).trim()}"
    }

    private const val BLOCKS = "▁▂▃▄▅▆▇█"
    private const val BAR_CELLS = 5
    private const val MILLIS_PER_HOUR = 3_600_000.0
    private val SLICE = 30.minutes
    private const val TREND_POINTS = 7
}
