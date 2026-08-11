package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Janela temporal aplicada à lista de sessões CLI.
 *
 * O recorte incide sobre os **turnos**: cada sessão é reagregada somando apenas
 * os turnos dentro da janela. Uma sessão antiga com atividade recente aparece
 * com os números dessa atividade, não com o total histórico.
 *
 * As janelas de 5h e 7d **ancoram na quota da conta** quando o reset é conhecido:
 * "5h" significa a janela de quota corrente da Anthropic — a mesma que o card do
 * dashboard mede — e não as últimas cinco horas corridas. Sem isso, uma sessão de
 * uma janela de quota já expirada continuaria contando junto com a atual.
 */
enum class CliSessionRange(val durationMillis: Long?) {
    LAST_5H(5L * 60 * 60 * 1_000),
    LAST_7D(7L * 24 * 60 * 60 * 1_000),
    LAST_30D(30L * 24 * 60 * 60 * 1_000),
    ALL(null);

    /**
     * Resolve o corte para [now], ancorando no fim da janela de quota quando há um
     * conhecido e ainda no futuro.
     */
    fun resolve(now: Instant, windows: CliQuotaWindows = CliQuotaWindows()): CliRangeWindow {
        val duration = durationMillis ?: return CliRangeWindow(null, null, false)

        val anchorEndsAt = when (this) {
            LAST_5H -> windows.fiveHourEndsAt
            LAST_7D -> windows.sevenDayEndsAt
            // Não existe quota de 30 dias: a janela só pode ser corrida.
            LAST_30D, ALL -> null
        }

        // Âncora no passado significa janela de quota expirada sem uso novo: não há
        // janela corrente para ancorar, então volta ao corte corrido.
        if (anchorEndsAt != null && anchorEndsAt > now) {
            return CliRangeWindow(
                cutoffMillis = anchorEndsAt.toEpochMilliseconds() - duration,
                endsAt = anchorEndsAt,
                isAnchored = true
            )
        }

        return CliRangeWindow(
            cutoffMillis = now.toEpochMilliseconds() - duration,
            endsAt = null,
            isAnchored = false
        )
    }

    companion object {
        /** Mesma janela de quota da Anthropic, alinhada aos cards do dashboard. */
        val DEFAULT = LAST_5H
    }
}

/**
 * Fins das janelas de quota da conta, lidos de `QuotaInfo.periodEndAt`.
 *
 * Nulo quando o dashboard ainda não coletou aquela conta ou a fonte não informou
 * o reset — nesse caso o filtro cai para a janela corrida.
 */
data class CliQuotaWindows(
    val fiveHourEndsAt: Instant? = null,
    val sevenDayEndsAt: Instant? = null
)

/** Corte já resolvido: o que consultar no índice e o que mostrar na legenda. */
data class CliRangeWindow(
    /** Epoch millis do início da janela; `null` não corta nada. */
    val cutoffMillis: Long? = null,
    /** Fim da janela de quota, quando ancorada. */
    val endsAt: Instant? = null,
    /** `true` quando o corte veio do reset da quota, não do relógio. */
    val isAnchored: Boolean = false
)
