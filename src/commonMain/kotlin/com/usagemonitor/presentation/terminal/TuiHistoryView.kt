package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.presentation.ui.PaceDiagonalModels
import com.usagemonitor.presentation.ui.TelegramBotMessages
import com.usagemonitor.presentation.ui.displayQuotaLabel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Aba 3, Histórico (3.3; #463, AI4): uma tabela de janelas, um bloco por cota com percentual na
 * ordem do painel e uma linha por janela — início, faixa ativa, pico e como terminou. `enter` abre
 * o gráfico alto da janela escolhida ([TuiHistoryChartView]). Horários em BRT.
 */
internal object TuiHistoryView {
    private const val MARKER_WIDTH = 2
    private const val START_WIDTH = 13
    private const val ACTIVE_WIDTH = 14
    private const val PEAK_BAR_WIDTH = 10
    private const val PERCENT_WIDTH = 5
    private const val PEAK_WIDTH = PEAK_BAR_WIDTH + PERCENT_WIDTH + 3

    /** Linhas fixas do alto do corpo: o período, uma em branco e o cabeçalho da tabela. */
    private const val HEADER_ROWS = 3
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    fun render(history: TuiHistory?, state: TuiHistoryState, now: Instant, size: TuiSize, labels: TuiHistoryLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): TuiTabView {
        val rows = history?.rows.orEmpty()
        val selected = state.resolve(rows.map { row -> row.listedWindows.size })
        if (state.chartOpen && history?.failure == null && selected != null) {
            val row = rows[selected.first]
            return TuiHistoryChartView.render(row, row.listedWindows[selected.second], now, size, labels, terminalLabels, profile)
        }
        val shortcuts = listOf(TuiShortcut("↑↓", labels.keyWindow), TuiShortcut("enter", labels.keyChart), TuiShortcut("←→", labels.keyPeriod))
        val body = mutableListOf(rangeLine(state.range, size.bodyWidth, labels), TerminalLine.EMPTY)
        val failure = history?.failure
        when {
            failure != null -> body += terminalLine(TerminalSpan(profile.glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(failure))
            rows.isEmpty() -> body += terminalLine(TerminalSpan(labels.noQuotas, TerminalColor.Muted))
            else -> body += table(rows, selected, now, size, labels, terminalLabels, profile)
        }
        return TuiTabView(body, shortcuts)
    }

    /** `período  24 h   7 dias   30 dias`, o ativo no fundo de seleção, e "uma linha = uma janela" à direita. */
    private fun rangeLine(active: HistoryRange, width: Int, labels: TuiHistoryLabels): TerminalLine {
        val spans = mutableListOf(TerminalSpan(labels.period + " ", TerminalColor.Muted))
        TuiHistoryState.RANGES.forEachIndexed { index, range ->
            if (index > 0) {
                spans += TerminalSpan("  ")
            }
            spans += if (range == active) TerminalSpan(" ${labels.range(range)} ", bold = true, selected = true) else TerminalSpan(" ${labels.range(range)} ", TerminalColor.Muted)
        }
        val left = TerminalLine(spans)
        val note = labels.oneLinePerWindow
        val gap = width - 1 - left.width - note.length
        return if (gap >= 2) left + terminalLine(TerminalSpan(" ".repeat(gap) + note, TerminalColor.Muted)) else left
    }

    /**
     * `trabalho Claude 5h`: o apelido e o rótulo do app. Conta sem apelido usa só o rótulo
     * quando ele já começa pelo nome da fonte ("Go 5h" de "OpenCode Go"), para não repetir.
     */
    fun rowLabel(row: TuiHistoryRow, pt: Boolean): String {
        val quota = displayQuotaLabel(row.quota.label, pt)
        val profile = row.account.profile?.takeIf { label -> label.isNotBlank() }
        if (profile != null) {
            return "$profile $quota"
        }
        val name = row.account.name
        val repeats = name.split(' ').any { word -> word.isNotEmpty() && quota.startsWith(word) }
        return if (repeats) quota else "$name $quota"
    }

    /** O cabeçalho fixo e os blocos, rolados para a janela escolhida ficar à vista. */
    private fun table(
        rows: List<TuiHistoryRow>,
        selected: Pair<Int, Int>?,
        now: Instant,
        size: TuiSize,
        labels: TuiHistoryLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile
    ): List<TerminalLine> {
        val header = " ".repeat(MARKER_WIDTH) + labels.columnStart.padEnd(START_WIDTH) + labels.columnActive.padEnd(ACTIVE_WIDTH) + labels.columnPeak.padEnd(PEAK_WIDTH) + labels.columnEnd
        val lines = mutableListOf<TerminalLine>()
        var selectedLine = 0
        rows.forEachIndexed { rowIndex, row ->
            if (rowIndex > 0) {
                lines += TerminalLine.EMPTY
            }
            lines += blockHeader(row, labels, terminalLabels, profile)
            val listed = row.listedWindows
            if (listed.isEmpty()) {
                lines += terminalLine(TerminalSpan(" ".repeat(MARKER_WIDTH) + labels.noWindow, TerminalColor.Muted))
            }
            listed.forEachIndexed { windowIndex, window ->
                val isSelected = selected == rowIndex to windowIndex
                if (isSelected) {
                    selectedLine = lines.size
                }
                lines += windowLine(row, window, isSelected, now, size.bodyWidth, labels, terminalLabels, profile)
            }
            val older = row.windows.size - listed.size
            if (older > 0) {
                lines += terminalLine(TerminalSpan(" ".repeat(MARKER_WIDTH) + labels.olderWindows(older), TerminalColor.Muted))
            }
        }
        val visible = (size.bodyHeight - HEADER_ROWS).coerceAtLeast(1)
        val start = TuiFrame.scrollStart(selectedLine, lines.size, visible)
        return listOf(terminalLine(TerminalSpan(header, TerminalColor.Muted, bold = true))) + lines.drop(start).take(visible)
    }

    /** `● trabalho Claude 5h  91% crítico · 7 janelas no período · 1 esgotou`. */
    private fun blockHeader(row: TuiHistoryRow, labels: TuiHistoryLabels, terminalLabels: TerminalLabels, profile: TerminalProfile): TerminalLine {
        val color = TerminalColor.Source(row.account.source)
        val zone = row.account.zoneOf(row.quota)
        val spans = mutableListOf(
            TerminalPrimitives.sourceMark(color, zone, profile.glyphs),
            TerminalSpan(" " + rowLabel(row, terminalLabels.pt), color, bold = true)
        )
        row.quota.percent?.let { percent ->
            spans += TerminalSpan("  $percent%", bold = true)
            zone?.let { found ->
                spans += TerminalSpan(" ")
                spans += TerminalPrimitives.zoneWord(found, terminalLabels, profile)
            }
        }
        if (row.windows.isNotEmpty()) {
            val separator = " ${profile.glyphs.separator} "
            spans += TerminalSpan(separator + labels.windowCount(row.windows.size), TerminalColor.Muted)
            val exhausted = row.windows.count { window -> window.exhaustedAt != null }
            if (exhausted > 0) {
                spans += TerminalSpan(separator + labels.exhaustedCount(exhausted), TerminalColor.Muted)
            }
        }
        return TerminalLine(spans)
    }

    private fun windowLine(
        row: TuiHistoryRow,
        window: QuotaWindowSummary,
        selected: Boolean,
        now: Instant,
        width: Int,
        labels: TuiHistoryLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalLine {
        val color = TerminalColor.Source(row.account.source)
        val spans = mutableListOf(
            TerminalSpan(dateTime(window.firstObservedAt, labels).padEnd(START_WIDTH)),
            TerminalSpan(activeSpan(window, labels).padEnd(ACTIVE_WIDTH), TerminalColor.Muted)
        )
        spans += TerminalPrimitives.quotaBar(window.peakPercent, PEAK_BAR_WIDTH, color, profile.glyphs)
        spans += TerminalSpan("${window.peakPercent}%".padStart(PERCENT_WIDTH), bold = true)
        spans += TerminalSpan("   ")
        spans += ending(window, now, labels, terminalLabels, profile, room = width - 1 - MARKER_WIDTH - START_WIDTH - ACTIVE_WIDTH - PEAK_WIDTH)
        val content = TerminalLine(spans)
        return if (selected) {
            (terminalLine(TerminalSpan(profile.glyphs.selection + " ", TerminalColor.Gold)) + content).fit(width - 1).selected()
        } else {
            terminalLine(TerminalSpan(" ".repeat(MARKER_WIDTH))) + content
        }
    }

    /**
     * Como a janela terminou: aberta e quando reinicia, quanto sobrou, ou `▲ esgotou após 3 h
     * 6 min · parado 1 h 54 min` — contado da primeira leitura, como o Histórico do app, porque o
     * início nominal a API não informa. Em [room] células: sem espaço, o "parado" sai inteiro,
     * nunca pela metade.
     */
    fun ending(window: QuotaWindowSummary, now: Instant, labels: TuiHistoryLabels, terminalLabels: TerminalLabels, profile: TerminalProfile, room: Int = Int.MAX_VALUE): List<TerminalSpan> {
        val exhaustedAt = window.exhaustedAt
        return when {
            exhaustedAt != null -> {
                val after = labels.exhaustedAfter(PaceDiagonalModels.durationLabel((exhaustedAt - window.firstObservedAt).inWholeMilliseconds))
                val idle = (window.resetsAt.coerceAtMost(if (window.isOpen) now else window.resetsAt) - exhaustedAt).inWholeMilliseconds
                val short = profile.glyphs.warning + " " + after
                val full = "$short ${profile.glyphs.separator} ${labels.idleFor(PaceDiagonalModels.durationLabel(idle))}"
                listOf(TerminalSpan(if (idle > 0L && full.length <= room) full else short, TerminalColor.Crit))
            }
            window.isOpen -> listOf(TerminalSpan(labels.open(terminalLabels.resetAt(window.resetsAt, now)), TerminalColor.Muted))
            else -> listOf(TerminalSpan(labels.leftover((100 - window.peakPercent).coerceAtLeast(0)), TerminalColor.Muted))
        }
    }

    /** `23/10 15:09`, em BRT, na ordem de dia e mês do app. */
    fun dateTime(at: Instant, labels: TuiHistoryLabels): String {
        val local = at.toLocalDateTime(SAO_PAULO)
        return labels.dayMonth(local.dayOfMonth, local.monthNumber) + " " + TelegramBotMessages.clock(at)
    }

    /**
     * A faixa ativa (#382): `15:09–19:07` no mesmo dia, `20/10–23/10` quando atravessa dias, e
     * `—` quando o uso não subiu na janela.
     */
    fun activeSpan(window: QuotaWindowSummary, labels: TuiHistoryLabels): String {
        val from = window.activeFrom
        val until = window.activeUntil
        if (from == null || until == null || from == until) {
            return "—"
        }
        val fromDate = from.toLocalDateTime(SAO_PAULO)
        val untilDate = until.toLocalDateTime(SAO_PAULO)
        if (fromDate.date == untilDate.date) {
            return TelegramBotMessages.clock(from) + "–" + TelegramBotMessages.clock(until)
        }
        return labels.dayMonth(fromDate.dayOfMonth, fromDate.monthNumber) + "–" + labels.dayMonth(untilDate.dayOfMonth, untilDate.monthNumber)
    }
}
