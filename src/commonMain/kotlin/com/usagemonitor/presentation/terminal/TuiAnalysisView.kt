package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.AnalysisHourBar
import com.usagemonitor.presentation.ui.AnalysisPanelModel
import com.usagemonitor.presentation.ui.paceLabel
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlin.time.Instant

/**
 * Aba 6, Análise (#463, AI2): a janela "Análise" do app escrita para o terminal. As duas janelas
 * lado a lado (barra, veredito, ritmos e o que cabe), a corrida esgotar × reiniciar de cada uma
 * (AI3) e as últimas 24 h numa escala só, em % da cota 5h, com a vazão numa linha própria — duas
 * grandezas nunca dividem o mesmo eixo. O corpo passa da tela: `↑ ↓` rolam.
 */
internal object TuiAnalysisView {
    private const val SIDE_BY_SIDE_WIDTH = 80
    private const val COLUMN_GAP = 2
    private const val MAX_WINDOW_BAR = 30
    private const val PACE_LABEL_WIDTH = 22
    private const val CHART_ROWS = 4
    private const val AXIS_WIDTH = 6

    fun render(analysis: TuiAnalysis?, state: TuiAnalysisState, now: Instant, size: TuiSize, labels: TerminalAnalysisLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): TuiTabView {
        val shortcuts = buildList {
            if ((analysis?.accounts?.size ?: 0) > 1) add(TuiShortcut("←→", labels.keyAccount))
            add(TuiShortcut("↑↓", labels.keyScroll))
        }
        val body = body(analysis, now, size.bodyWidth - 1, labels, terminalLabels, profile)
        val start = state.scroll.coerceIn(0, scrollMax(body.size, size))
        return TuiTabView(body.drop(start).take(size.bodyHeight.coerceAtLeast(0)), shortcuts)
    }

    /** Quantas linhas o corpo passa da tela: o limite da rolagem. */
    fun scrollMax(lineCount: Int, size: TuiSize): Int = (lineCount - size.bodyHeight).coerceAtLeast(0)

    fun body(analysis: TuiAnalysis?, now: Instant, width: Int, labels: TerminalAnalysisLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        val account = analysis?.account ?: return listOf(terminalLine(TerminalSpan(labels.noAccounts, TerminalColor.Muted)))
        val color = TerminalColor.Source(account.source)
        val name = listOfNotNull(account.name, account.profile?.takeIf { label -> label.isNotBlank() }).joinToString(" ${profile.glyphs.separator} ")
        val header = TerminalLine(listOf(TerminalPrimitives.sourceMark(color, null, profile.glyphs), TerminalSpan(" $name", color, bold = true)))
        val counter = labels.accountOf(analysis.index, analysis.accounts.size).takeIf { analysis.accounts.size > 1 }
        val lines = mutableListOf(TerminalRaceView.withRight(header, counter, width), TerminalLine.EMPTY)
        val data = analysis.analysis
        val windows = listOfNotNull(data?.fiveHour, data?.weekly)
        if (data == null || windows.isEmpty()) {
            lines += terminalLine(TerminalSpan(labels.noHistory, TerminalColor.Muted))
            return lines
        }
        lines += windowColumns(windows, color, now, width, labels, terminalLabels, profile)
        windows.forEach { window ->
            lines += TerminalLine.EMPTY
            lines += TerminalRaceView.render(window, color, now, width, labels, terminalLabels, profile)
        }
        lines += TerminalLine.EMPTY
        lines += lastDay(data, color, width, labels, profile)
        return lines
    }

    /** As janelas lado a lado a partir de 80 colunas; abaixo disso, uma embaixo da outra. */
    private fun windowColumns(windows: List<AnalysisWindowView>, color: TerminalColor, now: Instant, width: Int, labels: TerminalAnalysisLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        if (windows.size == 1 || width < SIDE_BY_SIDE_WIDTH) {
            return windows.flatMapIndexed { index, window -> (if (index > 0) listOf(TerminalLine.EMPTY) else emptyList()) + windowBlock(window, color, now, width, labels, terminalLabels, profile) }
        }
        val columnWidth = (width - COLUMN_GAP) / 2
        val (left, right) = windows.map { window -> windowBlock(window, color, now, columnWidth, labels, terminalLabels, profile) }
        return (0 until maxOf(left.size, right.size)).map { index ->
            (left.getOrNull(index) ?: TerminalLine.EMPTY).fit(columnWidth) + terminalLine(TerminalSpan(" ".repeat(COLUMN_GAP))) + (right.getOrNull(index) ?: TerminalLine.EMPTY)
        }
    }

    /** `JANELA 5H`, o veredito, a barra, o reinício, os dois ritmos e o que cabe. */
    private fun windowBlock(window: AnalysisWindowView, color: TerminalColor, now: Instant, width: Int, labels: TerminalAnalysisLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        val weekly = window.periodType == PeriodType.WEEKLY
        val reset = window.resetsAt?.takeIf { at -> at > now }
        val barWidth = (width - 6).coerceIn(8, MAX_WINDOW_BAR)
        val language = labels.language
        val fits = (window.projectedAtReset ?: 0) < 100
        val safe = window.safePace
        val toFit = when {
            window.projectedAtReset == null || safe == null -> TerminalSpan("—", TerminalColor.Muted)
            fits -> TerminalSpan(labels.currentPaceFits, TerminalColor.Ok)
            else -> TerminalSpan(labels.atMost(paceLabel(safe, language)), TerminalColor.Crit)
        }
        return listOf(
            terminalLine(TerminalSpan(labels.windowHeader(weekly), color, bold = true)),
            TerminalLine(TerminalRaceView.verdict(window, now, labels, profile)),
            TerminalLine(TerminalPrimitives.quotaBar(window.percent, barWidth, color, profile.glyphs) + TerminalSpan("  ${window.percent}%", bold = true)),
            terminalLine(TerminalSpan(reset?.let { at -> labels.resets + " " + terminalLabels.resetAt(at, now) + " ${profile.glyphs.separator} " + terminalLabels.inDuration(at - now) } ?: labels.noReset, TerminalColor.Muted)),
            TerminalLine.EMPTY,
            paceLine(labels.paceLastHour, TerminalSpan(paceLabel(window.paceNow, language), bold = true)),
            paceLine(labels.paceAverage, TerminalSpan(paceLabel(window.paceAverage, language), bold = true)),
            paceLine(labels.toFit, toFit)
        )
    }

    private fun paceLine(label: String, value: TerminalSpan): TerminalLine =
        terminalLine(TerminalSpan(label.padEnd(PACE_LABEL_WIDTH), TerminalColor.Muted), value)

    /**
     * As últimas 24 h: quatro linhas de colunas na escala da hora de maior consumo (% da 5h), o
     * eixo de 3 em 3 horas e, embaixo, a vazão na escala dela. Hora sem leitura fica em branco.
     */
    private fun lastDay(analysis: SessionAnalysis, color: TerminalColor, width: Int, labels: TerminalAnalysisLabels, profile: TerminalProfile): List<TerminalLine> {
        val bars = AnalysisPanelModel.hours(analysis)
        val separator = " ${profile.glyphs.separator} "
        val lines = mutableListOf(
            terminalLine(TerminalSpan(labels.last24h, bold = true), TerminalSpan(separator + labels.last24hNote + separator + AnalysisPanelModel.peakLabel(bars, labels.language), TerminalColor.Muted))
        )
        val max = bars.mapNotNull { bar -> bar.points }.maxOrNull()?.takeIf { value -> value > 0.0 } ?: return lines
        val top = ceil(max).toLong().coerceAtLeast(1L)
        val cell = ((width - AXIS_WIDTH) / bars.size).coerceIn(1, 3)
        lines += pointRows(bars, top, cell, color, profile)
        lines += terminalLine(TerminalSpan(" ".repeat(AXIS_WIDTH) + hourAxis(bars, cell), TerminalColor.Muted))
        lines += throughputRow(bars, cell, labels, profile)
        analysis.modelSpeeds.firstOrNull()?.let { speed ->
            lines += terminalLine(TerminalSpan(labels.lastHourModel(speed.tokensPerSecond.roundToLong(), speed.model), TerminalColor.Muted))
        }
        return lines
    }

    private fun pointRows(bars: List<AnalysisHourBar>, top: Long, cell: Int, color: TerminalColor, profile: TerminalProfile): List<TerminalLine> {
        val glyphs = profile.glyphs
        val levels = glyphs.sparkLevels
        return (CHART_ROWS - 1 downTo 0).map { row ->
            val axis = when (row) {
                CHART_ROWS - 1 -> "$top%".padStart(4) + " " + glyphs.box.teeRight
                CHART_ROWS / 2 - 1 -> "${top / 2}%".padStart(4) + " " + glyphs.box.teeRight
                0 -> "0%".padStart(4) + " " + glyphs.box.teeRight
                else -> " ".repeat(5) + glyphs.box.vertical
            }
            val spans = mutableListOf(TerminalSpan(axis, TerminalColor.Muted))
            bars.forEach { bar ->
                val points = bar.points
                val eighths = if (points == null) 0 else ((points / top * CHART_ROWS * 8).toInt() - row * 8).coerceIn(0, 8)
                val filled = if (cell > 1) cell - 1 else 1
                val glyph = when {
                    points == null -> TerminalSpan(" ".repeat(filled))
                    eighths >= 8 -> TerminalSpan(glyphs.barFull.toString().repeat(filled), color)
                    eighths > 0 -> TerminalSpan(levels[eighths - 1].toString().repeat(filled), color)
                    row == 0 && points > 0.0 -> TerminalSpan(levels[0].toString().repeat(filled), color)
                    row == 0 -> TerminalSpan(levels[0].toString().repeat(filled), TerminalColor.Muted)
                    else -> TerminalSpan(" ".repeat(filled))
                }
                spans += glyph
                if (cell > 1) spans += TerminalSpan(" ")
            }
            TerminalLine(spans)
        }
    }

    /** `20h   23h   02h…`: de 3 em 3 horas, alinhado às colunas; rótulo que encostaria no anterior sai. */
    private fun hourAxis(bars: List<AnalysisHourBar>, cell: Int): String {
        val axis = CharArray(bars.size * cell) { ' ' }
        var nextFree = 0
        bars.forEachIndexed { index, bar ->
            val text = bar.label + "h"
            val at = index * cell
            if (index % 3 != 0 || at < nextFree || at + text.length > axis.size) return@forEachIndexed
            text.forEachIndexed { offset, char -> axis[at + offset] = char }
            nextFree = at + text.length + 1
        }
        return axis.concatToString().trimEnd()
    }

    /** A vazão de cada hora em `▁…█`, na escala da maior; sem medida, a frase diz que falta. */
    private fun throughputRow(bars: List<AnalysisHourBar>, cell: Int, labels: TerminalAnalysisLabels, profile: TerminalProfile): TerminalLine {
        val label = TerminalSpan(labels.tokensPerSecond.padEnd(AXIS_WIDTH), TerminalColor.Muted)
        val max = bars.mapNotNull { bar -> bar.tokensPerSecond }.maxOrNull()?.takeIf { value -> value > 0.0 }
            ?: return terminalLine(label, TerminalSpan(labels.noThroughput, TerminalColor.Muted))
        val levels = profile.glyphs.sparkLevels
        val filled = if (cell > 1) cell - 1 else 1
        val spans = mutableListOf(label)
        bars.forEach { bar ->
            val value = bar.tokensPerSecond
            spans += if (value == null) {
                TerminalSpan(" ".repeat(filled))
            } else {
                val level = ceil(value / max * levels.length).toInt().coerceIn(1, levels.length)
                TerminalSpan(levels[level - 1].toString().repeat(filled), TerminalColor.Output)
            }
            if (cell > 1) spans += TerminalSpan(" ")
        }
        spans += TerminalSpan(" " + labels.maxTokens(max.roundToLong()), TerminalColor.Muted)
        return TerminalLine(spans)
    }
}
