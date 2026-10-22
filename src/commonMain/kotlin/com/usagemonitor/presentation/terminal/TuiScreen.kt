package com.usagemonitor.presentation.terminal

import com.usagemonitor.presentation.ui.TelegramBotMessages

/** O corpo e os atalhos de uma aba; a moldura é a mesma para todas. */
internal data class TuiTabView(val body: List<TerminalLine>, val shortcuts: List<TuiShortcut> = emptyList())

/**
 * A tela do modo interativo: a moldura com a aba ativa, ou a ajuda (3.5) no lugar do corpo.
 * Função pura: o mesmo estado e os mesmos dados desenham a mesma tela.
 */
internal object TuiScreen {

    fun context(data: TuiData, size: TuiSize): TuiContext =
        TuiContext(
            teamAvailable = data.teamConfigured,
            stacked = size.stacked,
            accountCount = data.accounts.size,
            sessionCount = data.sessions?.sessions?.size ?: 0,
            turnCount = data.sessions?.turns?.size ?: 0,
            historyRowCount = data.history?.rows?.size ?: 0,
            historyRowOfAccount = data.historyRowOfAccount
        )

    fun render(state: TuiState, data: TuiData, size: TuiSize, labels: TuiLabels, profile: TerminalProfile): List<TerminalLine> {
        if (size.tooSmall) {
            return TuiFrame.tooSmall(size, labels)
        }
        val context = context(data, size)
        val view = if (state.helpOpen) {
            TuiTabView(help(size, labels, profile), listOf(TuiShortcut("esc", labels.keyCloseHelpLabel)))
        } else {
            tabView(state, data, size, labels, profile)
        }
        return TuiFrame.render(
            size = size,
            tabs = context.tabs,
            active = state.tab,
            status = status(data, labels),
            body = withNotice(view.body, state.notice, size, profile),
            shortcuts = view.shortcuts + labels.commonShortcuts,
            labels = labels,
            profile = profile
        )
    }

    /**
     * O status da borda de cima. Ao vivo só com a coleta de pé e a última leitura certa; senão,
     * a hora da leitura mostrada, em `--term-warn`: leitura velha nunca se passa por atual.
     */
    fun status(data: TuiData, labels: TuiLabels): TerminalSpan {
        val last = data.lastCollectedAt ?: return TerminalSpan(labels.statusNoReading, TerminalColor.Warn)
        if (data.collector != null && !data.lastFailed) {
            return TerminalSpan(labels.statusLive(TelegramBotMessages.clock(data.now)), TerminalColor.Muted)
        }
        val shown = if (data.lastFailed) data.oldestFailedReading ?: last else last
        return TerminalSpan(labels.statusReadingOf(TelegramBotMessages.clock(shown)), TerminalColor.Warn)
    }

    /** O corpo de cada aba entra com a atividade dela (A04 a A08 do plano da #441). */
    private fun tabView(state: TuiState, data: TuiData, size: TuiSize, labels: TuiLabels, profile: TerminalProfile): TuiTabView =
        when (state.tab) {
            TuiTab.OVERVIEW -> TuiOverviewView.render(data, state.overview, size, TuiOverviewLabels(labels.language), TerminalLabels(labels.language), profile)
            TuiTab.SESSIONS -> TuiSessionsView.render(data.sessions, state.sessions, size, TuiSessionsLabels(labels.language), profile)
            TuiTab.HISTORY -> TuiHistoryView.render(data.history, state.history, size, TuiHistoryLabels(labels.language), TerminalLabels(labels.language), profile)
            TuiTab.TEAM, TuiTab.SETTINGS -> TuiTabView(emptyList())
        }

    /** O aviso de resultado na última linha do corpo, `✓` verde ou `▲`, sobre o que estiver ali. */
    private fun withNotice(body: List<TerminalLine>, notice: TuiNotice?, size: TuiSize, profile: TerminalProfile): List<TerminalLine> {
        notice ?: return body
        val glyphs = profile.glyphs
        val mark = if (notice.ok) TerminalSpan(glyphs.done + " ", TerminalColor.Ok) else TerminalSpan(glyphs.warning + " ", TerminalColor.Warn)
        val room = (size.bodyHeight - 1).coerceAtLeast(0)
        val kept = body.take(room)
        return kept + List(room - kept.size) { TerminalLine.EMPTY } + terminalLine(mark, TerminalSpan(notice.text))
    }

    /** A ajuda (3.5): todos os atalhos numa caixa, centrada no corpo. */
    fun help(size: TuiSize, labels: TuiLabels, profile: TerminalProfile): List<TerminalLine> {
        val keyWidth = 9
        val descriptionWidth = 20
        fun pair(leftKey: String, left: String, rightKey: String, right: String): TerminalLine =
            terminalLine(
                TerminalSpan(leftKey.padEnd(keyWidth), TerminalColor.Gold),
                TerminalSpan(left.padEnd(descriptionWidth)),
                TerminalSpan(rightKey.padEnd(keyWidth), TerminalColor.Gold),
                TerminalSpan(right)
            )
        val lines = listOf(
            terminalLine(TerminalSpan(labels.helpAnyTab.padEnd(keyWidth + descriptionWidth), bold = true), TerminalSpan(labels.helpInLists, bold = true)),
            TerminalLine.EMPTY,
            pair(labels.helpTabsKey, labels.helpTabs, "↑ ↓", labels.helpMove),
            pair("r", labels.helpRefresh, "enter", labels.helpOpen),
            pair("?", labels.helpHelp, "← →", labels.helpPeriod),
            pair("q", labels.helpQuit, labels.helpSpaceKey, labels.helpToggle),
            TerminalLine.EMPTY
        ) + labels.helpSettingsNote.map { text -> terminalLine(TerminalSpan(text, TerminalColor.Muted)) }
        val inner = (size.bodyWidth - 4).coerceAtMost(HELP_INNER_WIDTH)
        val box = TuiFrame.box(TerminalSpan(labels.helpTitle, TerminalColor.Gold, bold = true), lines, inner, profile.glyphs.box)
        val indent = TerminalSpan(" ".repeat(((size.bodyWidth - inner - 2) / 2).coerceAtLeast(0)))
        return listOf(TerminalLine.EMPTY) + box.map { line -> terminalLine(indent) + line }
    }

    private const val HELP_INNER_WIDTH = 60
}
