package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlin.math.roundToInt

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
    private const val TREND_POINTS = 7
}
