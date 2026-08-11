package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.TeamMemberUsage
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
        /** Conta Anthropic a que este time pertence. */
        val accountLabel: String? = null,
        /**
         * Integrantes com a lista de sessões aberta.
         *
         * Mora no estado, e não num `remember` da tela, porque o laço ao vivo
         * republica o `Success` a cada poucos segundos — e `loadTeam` tem de
         * carregá-lo do estado anterior, ou os grupos abertos se fechariam
         * sozinhos a cada tique.
         */
        val expandedDeviceIds: Set<String> = emptySet(),
        /**
         * Quando o conteúdo mudou pela última vez.
         *
         * Não é o instante da última consulta: com a atualização ao vivo ela
         * acontece de segundos em segundos, e carimbá-la aqui quebraria a
         * igualdade do estado, recompondo a tela sem nada ter mudado.
         */
        val lastChangedAt: Instant? = null
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

        /** Fatia do integrante no total de tokens do time. */
        fun tokenShareOf(member: TeamMemberUsage): Double {
            val total = totalTokens
            if (total <= 0L) {
                return 0.0
            }
            return member.totalTokens.toDouble() / total.toDouble()
        }
    }
}
