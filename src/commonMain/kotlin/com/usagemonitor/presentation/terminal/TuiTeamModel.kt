package com.usagemonitor.presentation.terminal

import kotlin.time.Instant

/** Uma pessoa na aba 4: a presença e o consumo do dia, como a janela de time do app mostra. */
data class TuiTeamMember(
    val alias: String,
    /** O app dela está aberto (batida de presença em 90 s). */
    val online: Boolean,
    /** Turno do Claude Code na janela ativa. */
    val workingNow: Boolean,
    val activeSessions: Int,
    val lastActivityAt: Instant?,
    val lastSeenAt: Instant?,
    val sessions: Int,
    val tokens: Long,
    /** Custo calculado aqui, pela `ModelPricingTable`: o servidor não precifica. */
    val costMicros: Long,
    val costIncomplete: Boolean
)

/** O que a aba 4 leu do servidor do time, para uma das contas que participam. */
data class TuiTeam(
    /** Os rótulos das contas que participam do time, na ordem das preferências; `t` anda por eles. */
    val accounts: List<String>,
    val accountIndex: Int,
    val members: List<TuiTeamMember>,
    /** O agora do servidor (relógio local mais o desvio medido), base de "há 3 min". */
    val referenceNow: Instant,
    /** A leitura falhou: a frase vai no alto, e os números são os da anterior. */
    val failure: String? = null,
    /** A falha foi de conexão (classificada pelo tipo da exceção): a frase diz isso, não a do socket. */
    val unreachable: Boolean = false,
    /** Quando foram lidos os números da tela; `null` sem leitura nenhuma ainda. */
    val readAt: Instant? = null
)

/** A conta e a pessoa escolhidas na aba 4. */
data class TuiTeamState(val accountIndex: Int = 0, val selected: Int = 0) {
    /** `↑ ↓` pessoa; `t` troca de conta do time e pede releitura. */
    fun reduce(key: TuiKey, memberCount: Int, accountCount: Int): Pair<TuiTeamState, List<TuiEffect>>? =
        when (key) {
            TuiKey.Up -> copy(selected = (selected - 1).coerceAtLeast(0)) to emptyList()
            TuiKey.Down -> copy(selected = (selected + 1).coerceAtMost((memberCount - 1).coerceAtLeast(0))) to emptyList()
            TuiKey.Text('t') -> if (accountCount > 1) TuiTeamState(accountIndex = (accountIndex + 1) % accountCount) to listOf(TuiEffect.Refresh) else null
            else -> null
        }
}
