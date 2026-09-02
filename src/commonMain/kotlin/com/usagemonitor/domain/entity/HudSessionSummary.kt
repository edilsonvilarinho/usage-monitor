package com.usagemonitor.domain.entity

/**
 * O que a máquina gastou na janela de 5h, para o rodapé da barra HUD (issue #164).
 *
 * As linhas do HUD falam de **cota** — o teto que o fornecedor impõe. Este
 * resumo fala do outro lado: quanto o CLI queimou de fato no mesmo período, e
 * quantas sessões estão vivas neste instante.
 */
data class HudSessionSummary(
    /** Sessões com turno nos últimos [ACTIVE_SESSION_WINDOW_MILLIS]. */
    val activeSessionCount: Int = 0,
    /**
     * Sessões com algum turno na janela de 5h.
     *
     * Separado de [activeSessionCount] porque zero **ativas** com trabalho na
     * janela é o caso comum — ninguém digitando agora, o gasto da tarde inteira
     * ainda contando para a quota. Colapsar os dois faria o rodapé sumir
     * justamente quando o número interessa.
     */
    val windowSessionCount: Int = 0,
    val costMicros: Long = 0L,
    val totalTokens: Long = 0L,
    /**
     * Turnos cujo modelo não está na tabela de preços.
     *
     * Maior que zero, [costMicros] é **piso** e não total — a apresentação marca
     * com `+`, como o resumo por eixo já faz. Custo zero silencioso afirmaria
     * que não custou nada.
     */
    val unpricedTurnCount: Int = 0
) {
    companion object {
        val Empty = HudSessionSummary()
    }
}
