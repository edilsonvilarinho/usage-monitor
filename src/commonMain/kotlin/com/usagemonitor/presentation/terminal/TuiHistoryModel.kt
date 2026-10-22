package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageSnapshotQuota

/** Uma trilha da aba 3: a cota de uma conta, o uso atual dela e a sparkline da janela. */
data class TuiHistoryRow(
    val account: TerminalAccount,
    val quota: UsageSnapshotQuota,
    /** `null` sem leitura no histórico para esta cota. */
    val spark: TuiSpark?
)

/** O que a aba 3 leu: uma trilha por cota com percentual, na ordem do painel. */
data class TuiHistory(
    val range: HistoryRange,
    val rows: List<TuiHistoryRow>,
    val failure: String? = null
)

/** A janela e a trilha escolhidas na aba 3. */
data class TuiHistoryState(
    val range: HistoryRange = HistoryRange.LAST_24_HOURS,
    val selected: Int = 0
) {
    /** `← →` trocam a janela (e pedem releitura), `↑ ↓` a trilha. */
    fun reduce(key: TuiKey, rowCount: Int): Pair<TuiHistoryState, List<TuiEffect>>? =
        when (key) {
            TuiKey.Up -> copy(selected = (selected - 1).coerceAtLeast(0)) to emptyList()
            TuiKey.Down -> copy(selected = (selected + 1).coerceAtMost((rowCount - 1).coerceAtLeast(0))) to emptyList()
            TuiKey.Left, TuiKey.Right -> {
                val step = if (key == TuiKey.Left) -1 else 1
                val next = RANGES[(RANGES.indexOf(range) + step).coerceIn(0, RANGES.lastIndex)]
                if (next == range) null else copy(range = next) to listOf(TuiEffect.Refresh)
            }
            else -> null
        }

    companion object {
        /** As três janelas da aba, na ordem das setas. O "Total" do app não cabe numa trilha. */
        val RANGES: List<HistoryRange> = listOf(HistoryRange.LAST_24_HOURS, HistoryRange.LAST_7_DAYS, HistoryRange.LAST_30_DAYS)

        /** Células por janela: meia hora em 24 h, 3 h em 7 dias, 12 h em 30 dias. */
        fun cellCount(range: HistoryRange): Int =
            when (range) {
                HistoryRange.LAST_7_DAYS -> 56
                HistoryRange.LAST_30_DAYS -> 60
                else -> 48
            }
    }
}
