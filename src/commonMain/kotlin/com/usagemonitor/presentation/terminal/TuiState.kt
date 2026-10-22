package com.usagemonitor.presentation.terminal

/**
 * O estado do modo interativo (#441): a aba, a ajuda aberta e a seleção de cada aba. Valor
 * imutável; quem o muda é o [TuiReducer], função pura testável sem terminal.
 */
data class TuiState(
    val tab: TuiTab = TuiTab.OVERVIEW,
    val helpOpen: Boolean = false
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
    val stacked: Boolean
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

    fun reduce(state: TuiState, key: TuiKey, context: TuiContext): TuiStep {
        if (key == TuiKey.Interrupt) {
            return TuiStep(state, listOf(TuiEffect.Quit))
        }
        if (state.helpOpen) {
            return reduceHelp(state, key)
        }
        val common = reduceCommon(state, key, context)
        if (common != null) {
            return common
        }
        return TuiStep(state)
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
                TuiStep(state.copy(tab = tab))
            }
            else -> null
        }
    }
}
