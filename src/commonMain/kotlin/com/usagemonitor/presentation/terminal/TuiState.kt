package com.usagemonitor.presentation.terminal

/**
 * O estado do modo interativo (#441): a aba, a ajuda aberta e a seleção de cada aba. Valor
 * imutável; quem o muda é o [TuiReducer], função pura testável sem terminal.
 */
data class TuiState(
    val tab: TuiTab = TuiTab.OVERVIEW,
    val helpOpen: Boolean = false,
    /** A conta escolhida na aba 1. */
    val overview: TuiListState = TuiListState(),
    /** O corte e a sessão da aba 2. */
    val sessions: TuiSessionsState = TuiSessionsState(),
    /** A janela e a trilha da aba 3. */
    val history: TuiHistoryState = TuiHistoryState(),
    /** A conta do time e a pessoa da aba 4. */
    val team: TuiTeamState = TuiTeamState(),
    /** A seção, o campo e a edição da aba 5. */
    val settings: TuiSettingsState = TuiSettingsState(),
    /** O resultado da última ação (o CSV, uma mudança da aba 5), até a próxima tecla. */
    val notice: TuiNotice? = null
) {
    /** A aba 4 sumiu (o time foi desfeito entre duas leituras): volta para a 1. */
    fun normalized(context: TuiContext): TuiState =
        if (tab == TuiTab.TEAM && !context.teamAvailable) copy(tab = TuiTab.OVERVIEW) else this
}

/** O que o redutor precisa saber da tela e dos dados para decidir uma tecla. */
data class TuiContext(
    /** A aba 4 só existe com time configurado. */
    val teamAvailable: Boolean,
    /** Abaixo de 80 colunas lista e detalhe se empilham, e `enter` abre o detalhe. */
    val stacked: Boolean,
    /** Quantas contas a aba 1 lista. */
    val accountCount: Int = 0,
    /** Quantas sessões a aba 2 lista, e quantos turnos tem a aberta. */
    val sessionCount: Int = 0,
    val turnCount: Int = 0,
    /** Quantas trilhas a aba 3 mostra. */
    val historyRowCount: Int = 0,
    /** A primeira trilha de cada conta da aba 1 na aba 3; `null` para conta sem percentual. */
    val historyRowOfAccount: List<Int?> = emptyList(),
    /** Quantas pessoas a aba 4 lista, e em quantas contas do time `t` anda. */
    val teamMemberCount: Int = 0,
    val teamAccountCount: Int = 0,
    /** As seções da aba 5, com os campos que a seleção visita. */
    val settingsSections: List<TuiSettingsSection> = emptyList()
) {
    /** As abas visíveis, na ordem e com o número fixo de cada uma. */
    val tabs: List<TuiTab>
        get() = TuiTab.entries.filter { tab -> tab != TuiTab.TEAM || teamAvailable }
}

/** O estado depois de uma tecla e o que o laço faz em seguida. */
data class TuiStep(val state: TuiState, val effects: List<TuiEffect> = emptyList())

/**
 * As teclas comuns a todas as abas (`1–5 aba`, `r atualizar`, `? ajuda`, `q sair`) e a ajuda.
 * O que sobra vai para a aba ativa.
 */
object TuiReducer {

    fun reduce(current: TuiState, key: TuiKey, context: TuiContext): TuiStep {
        // O aviso de resultado vale até a próxima tecla.
        val state = current.copy(notice = null)
        if (key == TuiKey.Interrupt) {
            return TuiStep(state, listOf(TuiEffect.Quit))
        }
        if (state.helpOpen) {
            return reduceHelp(state, key)
        }
        // Um campo de texto aberto recebe tudo: `q`, `r`, `?` e os dígitos são texto ali.
        if (state.tab == TuiTab.SETTINGS && state.settings.editing != null) {
            return reduceTab(state, key, context) ?: TuiStep(state)
        }
        val common = reduceCommon(state, key, context)
        if (common != null) {
            return common
        }
        return reduceTab(state, key, context) ?: TuiStep(state)
    }

    /** As teclas da aba ativa; `null` quando a aba não usa a tecla. */
    private fun reduceTab(state: TuiState, key: TuiKey, context: TuiContext): TuiStep? =
        when (state.tab) {
            TuiTab.OVERVIEW -> openHistory(state, key, context)
                ?: state.overview.reduce(key, context.accountCount, context.stacked)?.let { overview -> TuiStep(state.copy(overview = overview)) }
            TuiTab.SESSIONS -> state.sessions.reduce(key, context.sessionCount, context.turnCount)?.let { (sessions, effects) -> TuiStep(state.copy(sessions = sessions), effects) }
            TuiTab.HISTORY -> state.history.reduce(key, context.historyRowCount)?.let { (history, effects) -> TuiStep(state.copy(history = history), effects) }
            TuiTab.TEAM -> state.team.reduce(key, context.teamMemberCount, context.teamAccountCount)?.let { (team, effects) -> TuiStep(state.copy(team = team), effects) }
            TuiTab.SETTINGS -> state.settings.reduce(key, context.settingsSections)?.let { (settings, effects) -> TuiStep(state.copy(settings = settings), effects) }
        }

    /**
     * `enter` na aba 1, lado a lado: abre o histórico da conta, na primeira trilha dela.
     * Empilhado, o `enter` é o do detalhe, e quem decide é a lista.
     */
    private fun openHistory(state: TuiState, key: TuiKey, context: TuiContext): TuiStep? {
        if (key != TuiKey.Enter || context.stacked) {
            return null
        }
        val row = context.historyRowOfAccount.getOrNull(state.overview.selected) ?: return null
        return TuiStep(state.copy(tab = TuiTab.HISTORY, history = state.history.copy(selected = row)), listOf(TuiEffect.Refresh))
    }

    private fun reduceHelp(state: TuiState, key: TuiKey): TuiStep =
        when (key) {
            TuiKey.Escape, TuiKey.Enter, TuiKey.Text('?') -> TuiStep(state.copy(helpOpen = false))
            TuiKey.Text('q') -> TuiStep(state, listOf(TuiEffect.Quit))
            else -> TuiStep(state)
        }

    private fun reduceCommon(state: TuiState, key: TuiKey, context: TuiContext): TuiStep? {
        val char = (key as? TuiKey.Text)?.char ?: return null
        return when {
            char == '?' -> TuiStep(state.copy(helpOpen = true))
            char == 'q' -> TuiStep(state, listOf(TuiEffect.Quit))
            char == 'r' -> TuiStep(state, listOf(TuiEffect.Refresh))
            char.isDigit() -> {
                val tab = TuiTab.ofNumber(char.digitToInt())?.takeIf { candidate -> candidate in context.tabs } ?: return TuiStep(state)
                // Cada aba lê o que é dela: a troca pede uma releitura.
                if (tab == state.tab) TuiStep(state) else TuiStep(state.copy(tab = tab), listOf(TuiEffect.Refresh))
            }
            else -> null
        }
    }
}
