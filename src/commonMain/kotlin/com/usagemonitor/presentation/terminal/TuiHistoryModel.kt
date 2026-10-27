package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.QuotaWindowSummary
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageSnapshotQuota

/**
 * Uma cota da aba 3 (#463, AI4): a conta, o uso atual, as janelas do período e as leituras de
 * onde o gráfico alto (AI1) sai.
 */
data class TuiHistoryRow(
    val account: TerminalAccount,
    val quota: UsageSnapshotQuota,
    /** As janelas do período, da mais velha à mais nova, como o histórico do app as corta. */
    val windows: List<QuotaWindowSummary> = emptyList(),
    /** As leituras do período, em ordem; vazias sem histórico. */
    val points: List<UsageHistoryPoint> = emptyList()
) {
    /** As janelas que a tabela lista: a mais nova primeiro, até [TuiHistoryState.MAX_WINDOWS]. */
    val listedWindows: List<QuotaWindowSummary>
        get() = windows.asReversed().take(TuiHistoryState.MAX_WINDOWS)
}

/** O que a aba 3 leu: uma cota com percentual por bloco, na ordem do painel. */
data class TuiHistory(
    val range: HistoryRange,
    val rows: List<TuiHistoryRow>,
    val failure: String? = null
)

/**
 * O período, a janela escolhida e o gráfico aberto na aba 3. [row] é a cota e [window] a janela
 * dela (0 = a mais nova); a escolha pode apontar para cota sem janela (o `enter` da aba 1 abre na
 * primeira cota da conta), e quem acha a janela de verdade é [resolve].
 */
data class TuiHistoryState(
    val range: HistoryRange = HistoryRange.LAST_24_HOURS,
    val row: Int = 0,
    val window: Int = 0,
    val chartOpen: Boolean = false
) {
    /**
     * `↑ ↓` andam pelas janelas de todas as cotas, pulando cota sem janela; `enter` abre o
     * gráfico e `esc` fecha. `← →` trocam o período (e pedem releitura) só com a tabela.
     */
    fun reduce(key: TuiKey, windowCounts: List<Int>): Pair<TuiHistoryState, List<TuiEffect>>? {
        val positions = positions(windowCounts)
        val current = resolve(windowCounts)
        return when (key) {
            TuiKey.Up, TuiKey.Down -> {
                current ?: return null
                val index = positions.indexOf(current) + if (key == TuiKey.Up) -1 else 1
                val (row, window) = positions[index.coerceIn(0, positions.lastIndex)]
                copy(row = row, window = window) to emptyList()
            }
            TuiKey.Enter -> if (chartOpen || current == null) null else copy(row = current.first, window = current.second, chartOpen = true) to emptyList()
            TuiKey.Escape -> if (chartOpen) copy(chartOpen = false) to emptyList() else null
            TuiKey.Left, TuiKey.Right -> {
                if (chartOpen) {
                    return null
                }
                val step = if (key == TuiKey.Left) -1 else 1
                val next = RANGES[(RANGES.indexOf(range) + step).coerceIn(0, RANGES.lastIndex)]
                // O período novo tem outras janelas: a escolha volta para a mais nova da mesma cota.
                if (next == range) null else copy(range = next, window = 0) to listOf(TuiEffect.Refresh)
            }
            else -> null
        }
    }

    /**
     * A janela que a escolha aponta, como `(cota, janela)`: a própria quando existe; senão a mais
     * nova da primeira cota com janela a partir de [row], ou a última da lista. `null` sem janela.
     */
    fun resolve(windowCounts: List<Int>): Pair<Int, Int>? {
        val positions = positions(windowCounts)
        if (positions.isEmpty()) {
            return null
        }
        val count = windowCounts.getOrNull(row) ?: 0
        if (window in 0 until count) {
            return row to window
        }
        return positions.firstOrNull { (candidate, _) -> candidate >= row } ?: positions.last()
    }

    companion object {
        /** Os três períodos da aba, na ordem das setas. O "Total" do app não cabe numa tabela de janelas. */
        val RANGES: List<HistoryRange> = listOf(HistoryRange.LAST_24_HOURS, HistoryRange.LAST_7_DAYS, HistoryRange.LAST_30_DAYS)

        /** Janelas por cota na tabela; as mais velhas viram uma linha "+N janelas". */
        const val MAX_WINDOWS = 8

        /** Toda janela listada, cota por cota, na ordem da tela. */
        private fun positions(windowCounts: List<Int>): List<Pair<Int, Int>> =
            windowCounts.flatMapIndexed { row, count -> (0 until count).map { window -> row to window } }
    }
}
