package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.SessionModelMix
import kotlin.time.Instant

/**
 * Os cortes da aba 2. "Hoje" é desde a meia-noite em BRT; os outros, janelas corridas. Corte
 * por parâmetro (`sinceEpochMillis`), nunca por valor novo em `CliSessionRange`.
 */
enum class TuiSessionPeriod { TODAY, LAST_7_DAYS, LAST_30_DAYS }

/** Uma sessão CLI na aba 2, do Claude Code ou do Codex, já com o custo do índice. */
data class TuiSession(
    val id: String,
    /** A fonte do CLI: dá a cor do modelo e diz se há tarifa. */
    val source: ApiSource,
    val project: String?,
    val model: String?,
    val startedAt: Instant,
    val lastAt: Instant,
    val durationMillis: Long,
    val turnCount: Int,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val cacheWriteTokens: Long,
    /** `null` sem tarifa (Codex) ou sem medida; nunca custo zero inventado. */
    val costMicros: Long?,
    /** Algum turno usou modelo sem tarifa: o custo é parcial e leva `+`. */
    val costIncomplete: Boolean,
    val throughput: OutputThroughput?,
    /** Contexto vivo e a janela do modelo; `null` quando não se sabe. */
    val contextTokens: Long? = null,
    val contextWindow: Long? = null,
    /** Os modelos que a sessão usou e a fatia de cada um (#445); vazio = não lido. */
    val modelMix: SessionModelMix = SessionModelMix()
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens
}

/** Um turno da sessão aberta com `enter`. */
data class TuiTurn(
    val at: Instant,
    val model: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long,
    val costMicros: Long?
)

/** O que a aba 2 leu: as sessões do corte e, com `enter`, os turnos da selecionada. */
data class TuiSessions(
    val period: TuiSessionPeriod,
    val sessions: List<TuiSession>,
    /** Os turnos da sessão aberta, do mais recente ao mais antigo; `null` com a lista na tela. */
    val turns: List<TuiTurn>? = null,
    /** A leitura do índice falhou; a frase vai no lugar da lista. */
    val failure: String? = null
)

/** A seleção da aba 2: o corte, a sessão e, com os turnos abertos, a rolagem deles. */
data class TuiSessionsState(
    val period: TuiSessionPeriod = TuiSessionPeriod.TODAY,
    val selected: Int = 0,
    val turnsOpen: Boolean = false,
    val turnsScroll: Int = 0
) {
    /**
     * `↑ ↓` sessão (ou rolagem dos turnos), `← →` corte, `enter` turnos, `esc` volta, `e` CSV.
     * Corte novo e turnos abertos pedem uma releitura.
     */
    fun reduce(key: TuiKey, sessionCount: Int, turnCount: Int): Pair<TuiSessionsState, List<TuiEffect>>? {
        val periods = TuiSessionPeriod.entries
        return when (key) {
            TuiKey.Up -> (if (turnsOpen) copy(turnsScroll = (turnsScroll - 1).coerceAtLeast(0)) else copy(selected = (selected - 1).coerceAtLeast(0))) to emptyList()
            TuiKey.Down -> (
                if (turnsOpen) copy(turnsScroll = (turnsScroll + 1).coerceAtMost((turnCount - 1).coerceAtLeast(0)))
                else copy(selected = (selected + 1).coerceAtMost((sessionCount - 1).coerceAtLeast(0)))
                ) to emptyList()
            TuiKey.Left, TuiKey.Right -> {
                val step = if (key == TuiKey.Left) -1 else 1
                val next = periods[(period.ordinal + step).coerceIn(0, periods.lastIndex)]
                if (next == period) null else TuiSessionsState(period = next) to listOf(TuiEffect.Refresh)
            }
            TuiKey.Enter -> if (!turnsOpen && sessionCount > 0) copy(turnsOpen = true, turnsScroll = 0) to listOf(TuiEffect.Refresh) else null
            TuiKey.Escape -> if (turnsOpen) copy(turnsOpen = false) to emptyList() else null
            TuiKey.Text('e') -> this to listOf(TuiEffect.ExportSessions)
            else -> null
        }
    }
}
