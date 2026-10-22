package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.presentation.ui.displayQuotaLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

/**
 * Aba 3, Histórico (3.3): uma trilha por cota com percentual, na ordem do painel, com os
 * reinícios marcados, e o uso de agora com a palavra de estado no fim. O eixo é em BRT.
 */
internal object TuiHistoryView {
    private const val MARKER_WIDTH = 2
    private const val PERCENT_WIDTH = 4
    private const val MIN_LABEL_WIDTH = 10
    private const val MAX_LABEL_WIDTH = 22
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    fun render(history: TuiHistory?, state: TuiHistoryState, size: TuiSize, labels: TuiHistoryLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): TuiTabView {
        val shortcuts = listOf(TuiShortcut("←→", labels.keyWindow), TuiShortcut("↑↓", labels.keyQuota))
        val body = mutableListOf(rangeLine(state.range, size.bodyWidth, labels), TerminalLine.EMPTY)
        val failure = history?.failure
        val rows = history?.rows.orEmpty()
        when {
            failure != null -> body += terminalLine(TerminalSpan(profile.glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(failure))
            rows.isEmpty() -> body += terminalLine(TerminalSpan(labels.noQuotas, TerminalColor.Muted))
            else -> body += trails(rows, state, size, labels, terminalLabels, profile)
        }
        return TuiTabView(body, shortcuts)
    }

    /** `janela  24 h   7 dias   30 dias`, a ativa no fundo de seleção, e o tamanho da célula à direita. */
    private fun rangeLine(active: HistoryRange, width: Int, labels: TuiHistoryLabels): TerminalLine {
        val spans = mutableListOf(TerminalSpan(labels.window + " ", TerminalColor.Muted))
        TuiHistoryState.RANGES.forEachIndexed { index, range ->
            if (index > 0) {
                spans += TerminalSpan("  ")
            }
            spans += if (range == active) TerminalSpan(" ${labels.range(range)} ", bold = true, selected = true) else TerminalSpan(" ${labels.range(range)} ", TerminalColor.Muted)
        }
        val left = TerminalLine(spans)
        val cell = labels.cell(active)
        val gap = width - 1 - left.width - cell.length
        return if (gap >= 2) left + terminalLine(TerminalSpan(" ".repeat(gap) + cell, TerminalColor.Muted)) else left
    }

    /**
     * `trabalho Claude 5h`: o apelido e o rótulo do app. Conta sem apelido usa só o rótulo
     * quando ele já começa pelo nome da fonte ("Go 5h" de "OpenCode Go"), para não repetir.
     */
    private fun rowLabel(row: TuiHistoryRow, pt: Boolean): String {
        val quota = displayQuotaLabel(row.quota.label, pt)
        val profile = row.account.profile?.takeIf { label -> label.isNotBlank() }
        if (profile != null) {
            return "$profile $quota"
        }
        val name = row.account.name
        val repeats = name.split(' ').any { word -> word.isNotEmpty() && quota.startsWith(word) }
        return if (repeats) quota else "$name $quota"
    }

    private fun trails(rows: List<TuiHistoryRow>, state: TuiHistoryState, size: TuiSize, labels: TuiHistoryLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        val pt = terminalLabels.pt
        val labelWidth = (rows.maxOf { row -> rowLabel(row, pt).length } + 2).coerceIn(MIN_LABEL_WIDTH, MAX_LABEL_WIDTH)
        val cellRoom = (size.bodyWidth - 1 - MARKER_WIDTH - labelWidth - 2 - PERCENT_WIDTH - 1 - terminalLabels.zoneColumnWidth).coerceAtLeast(8)
        val cells = TuiHistoryState.cellCount(state.range).coerceAtMost(cellRoom)
        // O corpo tem a linha da janela, a em branco, o eixo, outra em branco e a legenda.
        val visible = (size.bodyHeight - 5).coerceAtLeast(1)
        val selected = state.selected.coerceIn(0, rows.lastIndex)
        val start = TuiFrame.scrollStart(selected, rows.size, visible)
        val lines = mutableListOf<TerminalLine>()
        var axisSource: TuiSpark? = null
        rows.subList(start, (start + visible).coerceAtMost(rows.size)).forEachIndexed { offset, row ->
            val shown = row.spark?.let { spark -> lastCells(spark, cells) }
            if (axisSource == null && shown != null) {
                axisSource = shown
            }
            lines += trail(row, shown, cells, labelWidth, start + offset == selected, size.bodyWidth, labels, terminalLabels, profile)
        }
        axisSource?.let { spark -> lines += terminalLine(TerminalSpan(" ".repeat(MARKER_WIDTH + labelWidth) + axis(spark, state.range, labels), TerminalColor.Muted)) }
        lines += TerminalLine.EMPTY
        lines += terminalLine(TerminalSpan(profile.glyphs.resetMark + " " + labels.legend, TerminalColor.Muted))
        return lines
    }

    private fun trail(
        row: TuiHistoryRow,
        spark: TuiSpark?,
        cells: Int,
        labelWidth: Int,
        selected: Boolean,
        width: Int,
        labels: TuiHistoryLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalLine {
        val color = TerminalColor.Source(row.account.source)
        val spans = mutableListOf(TerminalSpan(rowLabel(row, terminalLabels.pt).take(labelWidth - 1).padEnd(labelWidth), color))
        if (spark == null || spark.cells.all { cell -> cell == null }) {
            spans += TerminalSpan(labels.noReading.take(cells).padEnd(cells), TerminalColor.Muted)
        } else {
            spans += TuiSparkline.render(spark, color, profile.glyphs)
        }
        val percent = row.quota.percent
        if (percent != null) {
            spans += TerminalSpan("  " + "$percent%".padStart(PERCENT_WIDTH), bold = true)
            row.account.zoneOf(row.quota)?.let { zone ->
                spans += TerminalSpan(" ")
                spans += TerminalPrimitives.zoneWord(zone, terminalLabels, profile)
            }
        }
        val content = TerminalLine(spans)
        return if (selected) {
            (terminalLine(TerminalSpan(profile.glyphs.selection + " ", TerminalColor.Gold)) + content).fit(width - 1).selected()
        } else {
            terminalLine(TerminalSpan(" ".repeat(MARKER_WIDTH))) + content
        }
    }

    /** As últimas [count] células: em tela estreita, a trilha perde o começo, nunca o agora. */
    private fun lastCells(spark: TuiSpark, count: Int): TuiSpark {
        if (spark.cells.size <= count) {
            return spark
        }
        val dropped = spark.cells.size - count
        return TuiSpark(
            cells = spark.cells.takeLast(count),
            resets = spark.resets.map { index -> index - dropped }.filter { index -> index >= 0 }.toSet(),
            startsAt = spark.startsAt + (spark.cellMillis * dropped).milliseconds,
            cellMillis = spark.cellMillis
        )
    }

    /**
     * O eixo em BRT, alinhado às células: a cada 3 h cheias em 24 h ("15h"), na virada do dia
     * em 7 dias ("qua") e a cada 5 dias em 30 dias ("05/11"). Rótulo que encostaria no anterior
     * fica de fora.
     */
    private fun axis(spark: TuiSpark, range: HistoryRange, labels: TuiHistoryLabels): String {
        val axis = CharArray(spark.cells.size) { ' ' }
        var nextFree = 0
        for (index in spark.cells.indices) {
            val at = (spark.startsAt + (spark.cellMillis * index).milliseconds).toLocalDateTime(SAO_PAULO)
            val cellMinutes = spark.cellMillis / 60_000L
            val minuteOfDay = at.hour * 60 + at.minute
            val text = when (range) {
                HistoryRange.LAST_7_DAYS -> if (minuteOfDay < cellMinutes) labels.weekday(at.dayOfWeek.isoDayNumber) else null
                HistoryRange.LAST_30_DAYS -> if (minuteOfDay < cellMinutes && (at.dayOfMonth - 1) % 5 == 0) labels.dayMonth(at.dayOfMonth, at.monthNumber) else null
                else -> if (at.hour % 3 == 0 && at.minute < cellMinutes) "${at.hour.toString().padStart(2, '0')}h" else null
            } ?: continue
            if (index < nextFree || index + text.length > axis.size) {
                continue
            }
            text.forEachIndexed { offset, char -> axis[index + offset] = char }
            nextFree = index + text.length + 1
        }
        return axis.concatToString().trimEnd()
    }
}
