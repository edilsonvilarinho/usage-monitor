package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.isSamePeriod
import kotlin.time.Instant

/**
 * Uma trilha de uso em células de tempo igual (#441, primitiva Sparkline): o uso de cada
 * célula e as células onde a janela reiniciou. `null` é célula sem leitura, que fica em
 * branco: não medido nunca vira zero.
 */
data class TuiSpark(
    val cells: List<Int?>,
    val resets: Set<Int>,
    val startsAt: Instant,
    val cellMillis: Long
)

/** Monta e desenha a sparkline. Funções puras. */
internal object TuiSparkline {

    /**
     * As leituras de [points] em [cellCount] células de [startsAt] a [endsAt]. O valor da
     * célula é o maior percentual lido nela, pelo piso, como a barra de cota. A célula onde o
     * fim da janela muda entre duas leituras é a do reinício.
     */
    fun build(points: List<UsageHistoryPoint>, startsAt: Instant, endsAt: Instant, cellCount: Int): TuiSpark {
        val span = (endsAt - startsAt).inWholeMilliseconds.coerceAtLeast(1L)
        val cellMillis = (span / cellCount).coerceAtLeast(1L)
        val cells = MutableList<Int?>(cellCount) { null }
        val resets = mutableSetOf<Int>()
        val ordered = points.sortedBy { point -> point.capturedAt }
        ordered.forEachIndexed { index, point ->
            val offset = (point.capturedAt - startsAt).inWholeMilliseconds
            if (offset < 0L || point.capturedAt > endsAt) {
                return@forEachIndexed
            }
            val cell = (offset / cellMillis).toInt().coerceAtMost(cellCount - 1)
            val percent = (point.normalizedUsage * 100f).toInt().coerceIn(0, 100)
            cells[cell] = maxOf(cells[cell] ?: 0, percent)
            val previous = ordered.getOrNull(index - 1)
            if (previous != null && point.hasKnownResetAt && !isSamePeriod(previous.periodEndAt, point.periodEndAt)) {
                resets += cell
            }
        }
        return TuiSpark(cells, resets, startsAt, cellMillis)
    }

    /**
     * Uma célula por degrau na cor da fonte, `┆` muted no reinício e espaço sem leitura. O
     * degrau mais baixo vale para qualquer uso medido, para a trilha não sumir no zero.
     */
    fun render(spark: TuiSpark, color: TerminalColor, glyphs: TerminalGlyphs): List<TerminalSpan> {
        val levels = glyphs.sparkLevels
        val spans = mutableListOf<TerminalSpan>()
        val run = StringBuilder()
        fun flush() {
            if (run.isNotEmpty()) {
                spans += TerminalSpan(run.toString(), color)
                run.clear()
            }
        }
        spark.cells.forEachIndexed { index, value ->
            if (index in spark.resets) {
                flush()
                spans += TerminalSpan(glyphs.resetMark, TerminalColor.Muted)
            } else if (value == null) {
                run.append(' ')
            } else {
                val level = (value * levels.length + 50) / 100
                run.append(levels[(level - 1).coerceIn(0, levels.length - 1)])
            }
        }
        flush()
        return spans
    }
}
