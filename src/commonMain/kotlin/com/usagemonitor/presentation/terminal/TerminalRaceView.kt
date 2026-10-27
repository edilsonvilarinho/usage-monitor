package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.quotaWindowLengthMillis
import com.usagemonitor.presentation.ui.AnalysisPanelModel
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Esgotar × reiniciar de uma janela (#463, AI3): quanto falta para o reinício e quanto falta para
 * a cota acabar no ritmo da última hora e no médio, na mesma régua. `null` é não medido; um
 * ritmo parado não esgota. O esgotar é o tempo cru, mesmo depois do reinício: a régua é que o
 * corta na duração da janela.
 */
internal data class TerminalRace(
    val percent: Int,
    /** `null` sem reinício futuro conhecido. */
    val resetIn: Duration?,
    val lastHour: Run?,
    val average: Run?,
    /** A duração nominal da janela (5 h, 7 d), o teto da régua; `null` sem ela. */
    val length: Duration?
) {
    /** Um ritmo e quando ele esgota a cota; [exhaustIn] `null` com o ritmo parado. */
    data class Run(val pacePerHour: Double, val exhaustIn: Duration?)

    /** A régua: do agora até o mais longe que se desenha, sem passar da duração da janela. */
    val ruler: Duration?
        get() {
            val candidates = listOfNotNull(resetIn) + listOfNotNull(lastHour?.exhaustIn, average?.exhaustIn).map { value -> length?.let { cap -> minOf(value, cap) } ?: value }
            return candidates.maxOrNull()?.takeIf { value -> value.isPositive() }
        }

    companion object {
        fun of(window: AnalysisWindowView, now: Instant): TerminalRace {
            fun run(pace: Double?): Run? {
                pace ?: return null
                if (pace <= 0.0 || window.percent >= 100) return Run(pace.coerceAtLeast(0.0), null)
                return Run(pace, ((100 - window.percent) / pace * MILLIS_PER_HOUR).toLong().milliseconds)
            }
            return TerminalRace(
                percent = window.percent,
                resetIn = window.resetsAt?.takeIf { at -> at > now }?.let { at -> at - now },
                lastHour = run(window.paceNow),
                average = run(window.paceAverage),
                length = quotaWindowLengthMillis(window.quotaLabel, window.periodType)?.milliseconds
            )
        }

        private const val MILLIS_PER_HOUR = 3_600_000.0
    }
}

/** A corrida desenhada: o título com o veredito e uma barra por linha na mesma régua. */
internal object TerminalRaceView {
    private const val LABEL_WIDTH = 12
    private const val TEXT_RESERVE = 44
    private const val MIN_BAR = 8
    private const val MAX_BAR = 40

    fun render(window: AnalysisWindowView, color: TerminalColor, now: Instant, width: Int, labels: TerminalAnalysisLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): List<TerminalLine> {
        val race = TerminalRace.of(window, now)
        val weekly = window.periodType == PeriodType.WEEKLY
        val separator = TerminalSpan(" ${profile.glyphs.separator} ", TerminalColor.Muted)
        val title = TerminalLine(listOf(TerminalSpan(labels.windowName(weekly), color, bold = true), separator, TerminalSpan(labels.used(window.percent)), separator) + verdict(window, now, labels, profile))
        val ruler = race.ruler
        val lines = mutableListOf(withRight(title, ruler?.let { span -> labels.ruler(PaceDiagonalModels.durationLabel(span.inWholeMilliseconds)) }, width))
        if (ruler == null || race.resetIn == null) {
            lines += terminalLine(TerminalSpan("  " + labels.noReset, TerminalColor.Muted))
            return lines
        }
        val barWidth = (width - LABEL_WIDTH - 2 - TEXT_RESERVE).coerceIn(MIN_BAR, MAX_BAR)
        val resetsAt = now + race.resetIn
        lines += bar(labels.resets, race.resetIn, ruler, barWidth, TerminalColor.Muted, PaceDiagonalModels.durationLabel(race.resetIn.inWholeMilliseconds) + " · " + terminalLabels.resetAt(resetsAt, now), profile)
        if (race.percent >= 100) {
            lines += terminalLine(TerminalSpan("  " + labels.runsOut.padEnd(LABEL_WIDTH - 2), TerminalColor.Muted), TerminalSpan(labels.alreadyOut, TerminalColor.Crit))
            return lines
        }
        val runs = listOfNotNull(race.lastHour?.let { run -> run to true }, race.average?.let { run -> run to false })
        if (runs.isEmpty()) {
            lines += terminalLine(TerminalSpan("  " + labels.runsOut.padEnd(LABEL_WIDTH - 2), TerminalColor.Muted), TerminalSpan(labels.noPace, TerminalColor.Muted))
        }
        runs.forEachIndexed { index, (run, lastHour) ->
            val label = if (index == 0) labels.runsOut else ""
            val exhaustIn = run.exhaustIn
            lines += when {
                exhaustIn == null -> terminalLine(TerminalSpan("  " + label.padEnd(LABEL_WIDTH - 2), TerminalColor.Muted), TerminalSpan(labels.stalled, TerminalColor.Muted))
                race.length != null && exhaustIn > race.length ->
                    bar(label, ruler, ruler, barWidth, color, labels.beyond(PaceDiagonalModels.durationLabel(race.length.inWholeMilliseconds), lastHour), profile)
                else -> {
                    val before = exhaustIn < race.resetIn
                    val text = labels.exhaustAt(PaceDiagonalModels.durationLabel(exhaustIn.inWholeMilliseconds), terminalLabels.resetAt(now + exhaustIn, now), lastHour)
                    bar(label, exhaustIn, ruler, barWidth, if (before) TerminalColor.Crit else color, text, profile, textColor = if (before) TerminalColor.Crit else TerminalColor.Muted)
                }
            }
        }
        return lines
    }

    /** O veredito do card do app: `✓ cabe até o reinício`, `▲ esgota antes do reinício` ou `sem projeção`. */
    fun verdict(window: AnalysisWindowView, now: Instant, labels: TerminalAnalysisLabels, profile: TerminalProfile): List<TerminalSpan> {
        val card = AnalysisPanelModel.windowCard(window, now, labels.language)
        return when (card.verdictTone) {
            AppTone.CRITICAL -> listOf(TerminalSpan(profile.glyphs.warning + " " + card.verdict, TerminalColor.Crit))
            AppTone.OK -> listOf(TerminalSpan(profile.glyphs.done + " " + card.verdict, TerminalColor.Ok))
            else -> listOf(TerminalSpan(card.verdict, TerminalColor.Muted))
        }
    }

    /** Uma barra de cota com [value] sobre [ruler]: nunca some, mesmo quando o valor é mínimo. */
    private fun bar(label: String, value: Duration, ruler: Duration, width: Int, color: TerminalColor, text: String, profile: TerminalProfile, textColor: TerminalColor = TerminalColor.Muted): TerminalLine {
        val percent = (value.inWholeMilliseconds * 100 / ruler.inWholeMilliseconds.coerceAtLeast(1L)).toInt().coerceIn(1, 100)
        val spans = mutableListOf(TerminalSpan("  " + label.padEnd(LABEL_WIDTH - 2), TerminalColor.Muted))
        spans += TerminalPrimitives.quotaBar(percent, width, color, profile.glyphs)
        spans += TerminalSpan("  $text", textColor)
        return TerminalLine(spans)
    }

    /** [line] com [right] encostado à direita em [width], quando cabe. */
    fun withRight(line: TerminalLine, right: String?, width: Int): TerminalLine {
        right ?: return line
        val gap = width - line.width - right.length
        return if (gap >= 2) line + terminalLine(TerminalSpan(" ".repeat(gap) + right, TerminalColor.Muted)) else line
    }
}
