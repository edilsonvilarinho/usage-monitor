package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.tallyHealth
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import kotlinx.datetime.Instant

sealed interface TeamUsageUiState {

    data object Loading : TeamUsageUiState

    data class Error(
        val message: String,
        val range: CliSessionRange = CliSessionRange.DEFAULT,
        val accountLabel: String? = null
    ) : TeamUsageUiState

    data class Success(
        /** Integrantes com as sessões já recortadas por [range]. */
        val members: List<TeamMemberUsage>,
        val range: CliSessionRange = CliSessionRange.DEFAULT,
        /** Fim da janela de quota quando o corte está ancorado nela. */
        val rangeEndsAt: Instant? = null,
        /** `true` quando o corte veio do reset da quota, não do relógio. */
        val rangeAnchored: Boolean = false,
        /** Conta Anthropic a que este time pertence; `null` na visão global. */
        val accountLabel: String? = null,
        /**
         * `true` quando a janela mostra todas as contas do servidor.
         *
         * A tela usa isto para agrupar por conta e para avisar que o recorte de
         * 5h aqui é deslizante: contas diferentes resetam em horas diferentes, e
         * ancorar numa delas daria um número que não corresponde a nenhuma.
         */
        val isAdminOverview: Boolean = false,
        /**
         * Integrantes com a lista de sessões aberta, por [TeamMemberUsage.memberKey].
         *
         * Mora no estado, e não num `remember` da tela, porque o laço ao vivo
         * republica o `Success` a cada poucos segundos — e `loadTeam` tem de
         * carregá-lo do estado anterior, ou os grupos abertos se fechariam
         * sozinhos a cada tique.
         *
         * A chave é o `memberKey` e não o `deviceId` porque a visão global mistura
         * contas: a mesma máquina em duas delas expandiria as duas de uma vez.
         */
        val expandedMemberKeys: Set<String> = emptySet(),
        /**
         * Quando o conteúdo mudou pela última vez.
         *
         * Não é o instante da última consulta: com a atualização ao vivo ela
         * acontece de segundos em segundos, e carimbá-la aqui quebraria a
         * igualdade do estado, recompondo a tela sem nada ter mudado.
         */
        val lastChangedAt: Instant? = null,
        /**
         * `null` mostra a lista; qualquer outro valor mostra o detalhe da sessão.
         *
         * Mesmo tratamento de [expandedDeviceIds]: mora no estado e `loadTeam`
         * tem de carregá-lo do valor anterior, ou o tique do laço ao vivo
         * fecharia o painel na cara de quem está lendo.
         */
        val detail: TeamSessionDetailUiState? = null,
        /** Bloco Avançado do detalhe. Mesmo tratamento de [detail]. */
        val advancedExpanded: Boolean = false,
        /** Painel "Como ler esta tela". Mesmo tratamento de [detail]. */
        val glossaryExpanded: Boolean = false
    ) : TeamUsageUiState {

        val totalTokens: Long
            get() = members.sumOf { member -> member.totalTokens }

        val totalCostMicros: Long
            get() = members.sumOf { member -> member.totalCostMicros }

        val sessionCount: Int
            get() = members.sumOf { member -> member.sessionCount }

        val activeMemberCount: Int
            get() = members.count { member -> member.hasActivity }

        /** Ao menos um integrante tem turnos sem preço: o total exibido é parcial. */
        val isTotalCostComplete: Boolean
            get() = members.all { member -> member.isCostComplete }

        val isEmpty: Boolean
            get() = members.none { member -> member.hasActivity }

        /**
         * Quantas sessões do time estão saturadas ou em atenção.
         *
         * Vai para o cabeçalho porque o veredito por sessão vive dois níveis
         * abaixo — dentro de um integrante recolhido — e ninguém expande cinco
         * integrantes para descobrir que há uma sessão saturada.
         */
        val healthTally: CliSessionHealthTally
            get() = members.flatMap { member -> member.sessions }.tallyHealth()

        /** Fatia do integrante no total de tokens do time. */
        fun tokenShareOf(member: TeamMemberUsage): Double {
            val total = totalTokens
            if (total <= 0L) {
                return 0.0
            }
            return member.totalTokens.toDouble() / total.toDouble()
        }

        /**
         * Integrantes agrupados por conta, na ordem em que a conta aparece.
         *
         * Fora da visão global sai um grupo só, com [accountKey] nulo, e a tela
         * nem desenha cabeçalho — assim ela percorre uma estrutura única nos dois
         * modos, em vez de ter dois laços que precisam concordar.
         *
         * A ordem dos grupos vem da lista já ordenada por consumo: a conta do
         * integrante que mais gastou vem primeiro.
         */
        val memberGroups: List<TeamAccountGroup>
            get() {
                val grouped = LinkedHashMap<String?, MutableList<TeamMemberUsage>>()
                for (member in members) {
                    grouped.getOrPut(member.accountKey) { mutableListOf() }.add(member)
                }
                return grouped.map { (accountKey, groupMembers) ->
                    TeamAccountGroup(
                        accountKey = accountKey,
                        accountLabel = groupMembers.firstNotNullOfOrNull { it.accountLabel },
                        members = groupMembers
                    )
                }
            }
    }
}

/**
 * Uma conta e os integrantes dela dentro da lista.
 *
 * [accountLabel] é o rótulo que o administrador digitou ao emitir a chave —
 * costuma ser o e-mail da pessoa, mas o servidor não o verifica. Quando é nulo a
 * conta não tem chave emitida e só o [accountKey] a identifica.
 */
data class TeamAccountGroup(
    val accountKey: String?,
    val accountLabel: String?,
    val members: List<TeamMemberUsage>
) {
    val totalTokens: Long
        get() = members.sumOf { member -> member.totalTokens }

    val totalCostMicros: Long
        get() = members.sumOf { member -> member.totalCostMicros }

    val activeMemberCount: Int
        get() = members.count { member -> member.hasActivity }
}

/**
 * Painel de detalhe de uma sessão do time.
 *
 * Espelha [CliSessionDetailUiState] e carrega também o `deviceId`: o `sessionId`
 * identifica a sessão dentro de uma máquina, e a leitura no servidor é escopada
 * por `(conta, máquina)`.
 */
sealed interface TeamSessionDetailUiState {

    val deviceId: String
    val sessionId: String

    /**
     * Conta da sessão aberta; `null` no modal de uma conta só.
     *
     * Compõe a identidade do painel junto com `(deviceId, sessionId)`: na visão
     * global a mesma máquina pode aparecer em duas contas, e sem isto a resposta
     * de uma delas seria publicada no painel da outra.
     */
    val accountKey: String?

    data class Loading(
        override val deviceId: String,
        override val sessionId: String,
        override val accountKey: String? = null
    ) : TeamSessionDetailUiState

    data class Error(
        override val deviceId: String,
        override val sessionId: String,
        val message: String,
        override val accountKey: String? = null
    ) : TeamSessionDetailUiState

    data class Ready(
        override val deviceId: String,
        override val sessionId: String,
        override val accountKey: String? = null,
        val result: CliSessionDetailResult,
        /**
         * `true` quando o servidor não devolveu os turnos — versão anterior à
         * rota `/v1/session`, ou sessão que já saiu da retenção.
         *
         * O painel então mostra só o que o agregado da lista prova, sem os
         * gráficos por turno, e diz isso ao usuário. Não vira erro: o modal não
         * pode quebrar porque o servidor da empresa ficou para trás do app.
         */
        val turnsUnavailable: Boolean = false
    ) : TeamSessionDetailUiState
}
