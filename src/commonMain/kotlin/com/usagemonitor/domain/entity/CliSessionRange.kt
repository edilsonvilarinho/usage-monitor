package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Janela temporal aplicada à lista de sessões CLI.
 *
 * O recorte incide sobre os **turnos**: cada sessão é reagregada somando apenas
 * os turnos dentro da janela. Uma sessão antiga com atividade recente aparece
 * com os números dessa atividade, não com o total histórico.
 *
 * A janela de 5h **ancora na quota da conta** quando o reset é conhecido: "5h"
 * significa a janela de quota corrente da Anthropic — a mesma que o card do
 * dashboard mede — e não as últimas cinco horas corridas. Sem isso, uma sessão de
 * uma janela de quota já expirada continuaria contando junto com a atual.
 *
 * Isso vale também depois do reset vencer: o corte passa a ser o próprio reset,
 * porque a janela nova começa a partir dele. O corte corrido (`now − 5h`)
 * alcançava turnos de antes do reset e os misturava com a janela nova (issue
 * #35).
 *
 * As demais janelas são corridas. A de 7d já ancorou no reset semanal e não pode
 * voltar a fazê-lo: logo depois de um reset semanal o corte ancorado (`reset − 7d`)
 * fica mais recente que o corte de 5h, e sessões visíveis em "5h" sumiam ao trocar
 * para "7 dias" (issue #28). Com o corte corrido vale sempre 5h ⊆ 7d ⊆ 30d ⊆ Total.
 */
enum class CliSessionRange(val durationMillis: Long?) {
    LAST_5H(5L * 60 * 60 * 1_000),
    LAST_7D(7L * 24 * 60 * 60 * 1_000),
    LAST_30D(30L * 24 * 60 * 60 * 1_000),
    ALL(null);

    /**
     * Resolve o corte para [now], ancorando no fim da janela de quota quando há um
     * conhecido.
     */
    fun resolve(now: Instant, windows: CliQuotaWindows = CliQuotaWindows()): CliRangeWindow {
        val duration = durationMillis ?: return CliRangeWindow(null, null, false)

        val anchorEndsAt = when (this) {
            LAST_5H -> windows.fiveHourEndsAt
            // 7d não ancora para não ficar mais estreita que 5h logo após o reset
            // semanal; 30 dias não tem quota correspondente para ancorar.
            LAST_7D, LAST_30D, ALL -> null
        }

        val slidingCutoffMillis = now.toEpochMilliseconds() - duration

        if (anchorEndsAt != null && anchorEndsAt > now) {
            return CliRangeWindow(
                cutoffMillis = anchorEndsAt.toEpochMilliseconds() - duration,
                endsAt = anchorEndsAt,
                isAnchored = true
            )
        }

        // Reset já vencido: a janela de quota corrente começa em algum ponto a
        // partir dele — a Anthropic abre a janela nova na primeira mensagem depois
        // do reset, não em ciclos contíguos. Cortar no próprio reset é o único
        // corte que nunca traz turno da janela anterior, e o corte corrido
        // (`now - 5h`) trazia: era o bug da issue #35.
        //
        // O corte corrido continua sendo o piso: uma âncora velha demais (dado de
        // dashboard parado, app reaberto dias depois) faria a janela de "5h"
        // abranger mais que cinco horas e romper `5h ⊆ 7d ⊆ 30d ⊆ Total`.
        val expiredAnchorMillis = anchorEndsAt?.toEpochMilliseconds()
        if (expiredAnchorMillis != null && expiredAnchorMillis > slidingCutoffMillis) {
            return CliRangeWindow(
                cutoffMillis = expiredAnchorMillis,
                // Fim desconhecido: a janela nova só ganha `resets_at` quando a
                // conta volta a consumir e a API o materializa.
                endsAt = null,
                isAnchored = true
            )
        }

        return CliRangeWindow(
            cutoffMillis = slidingCutoffMillis,
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
 * Fim da janela de quota de 5h da conta, lido de `QuotaInfo.periodEndAt`.
 *
 * Nulo quando o dashboard ainda não coletou aquela conta ou a fonte não informou
 * o reset — nesse caso o filtro cai para a janela corrida.
 */
data class CliQuotaWindows(
    val fiveHourEndsAt: Instant? = null
)

/** Corte já resolvido: o que consultar no índice e o que mostrar na legenda. */
data class CliRangeWindow(
    /** Epoch millis do início da janela; `null` não corta nada. */
    val cutoffMillis: Long? = null,
    /**
     * Fim da janela de quota, quando conhecido.
     *
     * Nulo com [isAnchored] verdadeiro significa janela nova, aberta no reset que
     * acabou de vencer e ainda sem `resets_at` publicado pela API.
     */
    val endsAt: Instant? = null,
    /** `true` quando o corte veio do reset da quota, não do relógio. */
    val isAnchored: Boolean = false
)
