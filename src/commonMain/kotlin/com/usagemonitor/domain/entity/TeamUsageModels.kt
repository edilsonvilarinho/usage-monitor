package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/** Identidade da máquina que empurra dados para o servidor de time. */
data class TeamMemberIdentity(
    val deviceId: String,
    val alias: String,
    val hostName: String? = null,
    val organizationUuid: String? = null,
    val organizationName: String? = null
)

/**
 * Lote enviado ao servidor.
 *
 * Reusa [CliSessionSummary] e [CliSessionTurn] em vez de criar entidades
 * paralelas: é exatamente o que o índice local já guarda, e qualquer campo novo
 * ali passa a trafegar sem uma segunda definição para manter em sincronia.
 *
 * Invariante do lote: todo `turn.sessionId` tem de estar em [sessions]. A
 * consulta do servidor faz `JOIN` na sessão, então um turno órfão seria gravado
 * e nunca apareceria — o servidor rejeita o lote em vez de aceitá-lo.
 */
data class TeamIngestPayload(
    val accountKey: String,
    val member: TeamMemberIdentity,
    val sessions: List<CliSessionSummary>,
    val turns: List<CliSessionTurn>
) {
    val isEmpty: Boolean
        get() = turns.isEmpty()
}

data class TeamIngestReceipt(
    val acceptedTurns: Int = 0,
    val ignoredTurns: Int = 0,
    val acceptedSessions: Int = 0
)

/**
 * Recibo de uma batida de presença.
 *
 * [serverTimeAt] é o agora do servidor no instante do carimbo, e é o que permite
 * medir o desvio entre os dois relógios. Vem `null` no caminho de compatibilidade
 * — um servidor anterior à 0.4.0 responde pelo ingest, que não devolve relógio.
 */
data class TeamPresenceReceipt(
    val serverTimeAt: Instant? = null
)

/**
 * Consumo de um integrante do time dentro da janela consultada.
 *
 * [sessions] pode vir vazia: o servidor devolve todos os membros da conta,
 * inclusive quem não teve atividade no período. Quem não consumiu é informação
 * de gestão, não ruído.
 */
data class TeamMemberUsage(
    val deviceId: String,
    val alias: String,
    val hostName: String? = null,
    val organizationName: String? = null,
    val lastSeenAt: Instant? = null,
    val sessions: List<CliSessionSummary> = emptyList(),
    /**
     * Conta a que este integrante pertence.
     *
     * `null` no modal de uma conta só, onde a conta é a da janela inteira e
     * repeti-la em cada integrante seria ruído. Preenchido na visão global do
     * administrador, que mistura contas na mesma lista — e é dele que saem o
     * agrupamento na tela e o escopo do detalhe e da remoção, já que ali não
     * existe "a conta da janela".
     */
    val accountKey: String? = null,
    /** Rótulo da conta; `null` quando ela não tem chave emitida. */
    val accountLabel: String? = null
) {
    val sessionCount: Int
        get() = sessions.size

    val totalTokens: Long
        get() = sessions.sumOf { session -> session.totalTokens }

    val totalCostMicros: Long
        get() = sessions.sumOf { session -> session.costMicros }

    /** O custo cobre tudo apenas quando nenhuma sessão teve modelo sem preço. */
    val isCostComplete: Boolean
        get() = sessions.all { session -> session.isCostComplete }

    val hasActivity: Boolean
        get() = sessions.isNotEmpty()

    /**
     * Pior veredito entre as sessões, ou `null` sem nenhuma avaliável.
     *
     * Fica aqui, junto dos outros campos derivados, e não na tela: é o que
     * permite ler o status de um integrante **recolhido** — sem isso a única
     * sessão saturada de um time fica escondida atrás de um clique que ninguém
     * dá porque nada indica que valha a pena.
     */
    val worstHealth: CliSessionHealth?
        get() = sessions.worstHealth()

    /** Saturadas e em atenção deste integrante, na mesma regra do cabeçalho. */
    val healthTally: CliSessionHealthTally
        get() = sessions.tallyHealth()

    /** Instante do turno mais recente na janela, ou `null` sem atividade. */
    val lastActivityAt: Instant?
        get() = sessions.maxOfOrNull { session -> session.lastTs }

    /** Rótulo da máquina para a lista; cai no alias quando o host é desconhecido. */
    val machineLabel: String
        get() = hostName?.takeIf { it.isNotBlank() } ?: alias

    /**
     * Identidade desta linha na tela.
     *
     * O `deviceId` sozinho não serve na visão global: a mesma máquina logada em
     * duas contas da empresa aparece duas vezes, com o mesmo `deviceId` e
     * números diferentes. Usá-lo como chave faria as duas linhas expandirem
     * juntas e a remoção acertar a conta errada.
     */
    val memberKey: String
        get() = if (accountKey == null) deviceId else "$accountKey/$deviceId"
}

/** Resposta do servidor já dobrada em sessões e agrupada por integrante. */
data class TeamUsageSnapshot(
    val members: List<TeamMemberUsage> = emptyList()
) {
    val totalTokens: Long
        get() = members.sumOf { member -> member.totalTokens }

    val totalCostMicros: Long
        get() = members.sumOf { member -> member.totalCostMicros }

    val isTotalCostComplete: Boolean
        get() = members.all { member -> member.isCostComplete }

    val sessionCount: Int
        get() = members.sumOf { member -> member.sessionCount }

    val activeMemberCount: Int
        get() = members.count { member -> member.hasActivity }

    /** Fatia do integrante no total de tokens do time; zero quando não houve uso. */
    fun tokenShareOf(member: TeamMemberUsage): Double {
        val total = totalTokens
        if (total <= 0L) {
            return 0.0
        }
        return member.totalTokens.toDouble() / total.toDouble()
    }
}
