package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * `usage-monitor analysis` (#463, AI3): a corrida esgotar × reiniciar de cada janela, conta por
 * conta, de uma tacada — o mesmo bloco da aba 6. Sai com 0, ou com 3 ("sem leitura") quando
 * nenhuma conta tem análise. Cabe em 100 colunas, como os outros comandos.
 */
internal object TerminalAnalysisView {
    const val WIDTH = 100

    fun render(
        items: List<Pair<TerminalAccount, SessionAnalysis?>>,
        now: Instant,
        collector: CollectorKind?,
        age: Duration?,
        labels: TerminalAnalysisLabels,
        terminalLabels: TerminalLabels,
        profile: TerminalProfile,
        filter: String? = null
    ): TerminalOutput {
        if (items.isEmpty()) {
            val text = filter?.let(labels::noMatch) ?: labels.noAccounts
            return TerminalOutput(listOf(terminalLine(TerminalSpan(profile.glyphs.warning + " ", TerminalColor.Warn), TerminalSpan(text))), TerminalStatusView.EXIT_NO_READING)
        }
        val lines = mutableListOf<TerminalLine>()
        items.forEachIndexed { index, (account, analysis) ->
            if (index > 0) lines += TerminalLine.EMPTY
            val color = TerminalColor.Source(account.source)
            val name = listOfNotNull(account.name, account.profile?.takeIf { label -> label.isNotBlank() }).joinToString(" ${profile.glyphs.separator} ")
            lines += terminalLine(TerminalPrimitives.sourceMark(color, null, profile.glyphs), TerminalSpan(" $name", color, bold = true))
            val windows = listOfNotNull(analysis?.fiveHour, analysis?.weekly)
            if (windows.isEmpty()) {
                lines += terminalLine(TerminalSpan("  " + labels.noHistory, TerminalColor.Muted))
            }
            windows.forEachIndexed { windowIndex, window ->
                if (windowIndex > 0) lines += TerminalLine.EMPTY
                lines += TerminalRaceView.render(window, color, now, WIDTH, labels, terminalLabels, profile)
            }
        }
        if (collector != null && age != null) {
            lines += TerminalLine.EMPTY
            lines += terminalLine(TerminalSpan(terminalLabels.readBy(collector, age), TerminalColor.Muted))
        }
        return TerminalOutput(lines, TerminalStatusView.EXIT_OK)
    }
}
