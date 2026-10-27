package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.SessionAnalysis

/**
 * O que a aba 6, Análise (#463, AI2), leu: as contas que têm análise (Claude Code e Codex, as
 * fontes com índice CLI), a escolhida e a análise dela — a mesma `SessionAnalysis` da janela
 * "Análise" do app e do `/analise` do bot.
 */
data class TuiAnalysis(
    val accounts: List<TerminalAccount>,
    val index: Int,
    /** `null` sem histórico lido para a conta escolhida. */
    val analysis: SessionAnalysis?
) {
    val account: TerminalAccount?
        get() = accounts.getOrNull(index)
}

/** A conta escolhida e a rolagem do corpo na aba 6. */
data class TuiAnalysisState(
    val account: Int = 0,
    val scroll: Int = 0
) {
    /** `← →` trocam a conta (e pedem releitura, voltando ao topo); `↑ ↓` rolam o corpo. */
    fun reduce(key: TuiKey, accountCount: Int, scrollMax: Int): Pair<TuiAnalysisState, List<TuiEffect>>? =
        when (key) {
            TuiKey.Up -> copy(scroll = (scroll - 1).coerceIn(0, scrollMax.coerceAtLeast(0))) to emptyList()
            TuiKey.Down -> copy(scroll = (scroll + 1).coerceIn(0, scrollMax.coerceAtLeast(0))) to emptyList()
            TuiKey.Left, TuiKey.Right -> {
                val next = (account + if (key == TuiKey.Left) -1 else 1).coerceIn(0, (accountCount - 1).coerceAtLeast(0))
                if (next == account) null else TuiAnalysisState(account = next) to listOf(TuiEffect.Refresh)
            }
            else -> null
        }
}
