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
            historyRowOfAccount = data.historyRowOfAccount,
            teamMemberCount = data.team?.members?.size ?: 0,
            teamAccountCount = data.team?.accounts?.size ?: 0,
            settingsSections = data.settings?.sections.orEmpty()
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
            // Com um campo de texto aberto, `q`, `r`, `?` e os dígitos são texto: os atalhos comuns somem.
            shortcuts = if (state.tab == TuiTab.SETTINGS && state.settings.editing != null && !state.helpOpen) {
                view.shortcuts
            } else {
                fittingShortcuts(view.shortcuts, labels.commonShortcuts, size.frameWidth - 3)
            },
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
            TuiTab.TEAM -> TuiTeamView.render(data.team, state.team, size, TuiTeamLabels(labels.language), TerminalLabels(labels.language), profile)
            TuiTab.SETTINGS -> TuiSettingsView.render(data.settings, state.settings, data.collector, size, TuiSettingsLabels(labels.language), profile)
        }

    /**
     * O aviso de resultado nas últimas linhas do corpo, `✓` verde ou `▲`, sobre o que estiver
     * ali; o detalhe (o comando a rodar, a ressalva) em muted, abaixo.
     */
    private fun withNotice(body: List<TerminalLine>, notice: TuiNotice?, size: TuiSize, profile: TerminalProfile): List<TerminalLine> {
        notice ?: return body
        val glyphs = profile.glyphs
        val mark = when {
            notice.pending -> TerminalSpan("")
            notice.ok -> TerminalSpan(glyphs.done + " ", TerminalColor.Ok)
            else -> TerminalSpan(glyphs.warning + " ", TerminalColor.Warn)
        }
        // A frase quebra por palavra (a recusa de um valor explica o formato e passa de 90 colunas).
        val textWidth = (size.bodyWidth - 1 - mark.text.length).coerceAtLeast(10)
        val (first, rest) = wrapped(notice.text, textWidth).let { parts -> parts.first() to parts.drop(1) }
        val color = if (notice.pending) TerminalColor.Muted else null
        val continuation = rest.map { part -> terminalLine(TerminalSpan(" ".repeat(mark.text.length) + part, color)) }
        val detail = (continuation + notice.detail.map { text -> terminalLine(TerminalSpan("  $text", TerminalColor.Muted)) }).take(MAX_NOTICE_EXTRA)
        val room = (size.bodyHeight - 1 - detail.size).coerceAtLeast(0)
        val kept = body.take(room)
        return kept + List(room - kept.size) { TerminalLine.EMPTY } + terminalLine(mark, TerminalSpan(first, color)) + detail
    }

    /** [text] em linhas de até [width] células, quebrando no espaço; palavra maior que a linha é cortada. */
    internal fun wrapped(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ')) {
            current = when {
                current.isEmpty() -> word
                current.length + 1 + word.length <= width -> "$current $word"
                else -> {
                    lines += current
                    word
                }
            }
        }
        lines += current
        return lines.map { line -> line.take(width) }
    }

    /** Linhas além da primeira: a continuação da frase e o detalhe (o comando a rodar). */
    private const val MAX_NOTICE_EXTRA = 3

    /**
     * Os atalhos que cabem inteiros em [width]: cortado no meio, o último par ("r atu") não diz
     * nada. Saem primeiro `r` e `1–5` (a ajuda lista todos), depois os da aba pelo fim; `?` e
     * `q` ficam sempre.
     */
    internal fun fittingShortcuts(tab: List<TuiShortcut>, common: List<TuiShortcut>, width: Int): List<TuiShortcut> {
        fun widthOf(list: List<TuiShortcut>): Int = list.sumOf { shortcut -> shortcut.key.length + 1 + shortcut.label.length + 2 } - 2
        val kept = (tab + common).toMutableList()
        val (tabsKey, refresh) = common[0] to common[1]
        val dropOrder = listOf(refresh, tabsKey) + tab.reversed()
        for (shortcut in dropOrder) {
            if (widthOf(kept) <= width) {
                break
            }
            kept.remove(shortcut)
        }
        return kept
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
