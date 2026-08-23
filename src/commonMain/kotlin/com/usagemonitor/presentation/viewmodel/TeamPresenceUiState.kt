package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.TeamMemberPresence
import kotlinx.datetime.Instant

sealed interface TeamPresenceUiState {

    data object Loading : TeamPresenceUiState

    data class Error(
        val message: String,
        val accountLabel: String? = null
    ) : TeamPresenceUiState

    /**
     * Lista de presença já classificada.
     *
     * **Nenhum campo de tempo decorrido mora aqui**, e isso é deliberado: um
     * `idleForMillis` ou um `referenceNow` mudariam a cada tique, destruiriam a
     * igualdade do `data class` e fariam a tela recompor de cinco em cinco
     * segundos sem nada ter mudado. A tela mostra hora absoluta, que é estável
     * entre tiques. O desvio de relógio entra arredondado em minutos pelo mesmo
     * motivo — o valor cru oscila alguns milissegundos a cada batida.
     */
    data class Success(
        val entries: List<TeamMemberPresence>,
        /** Conta Anthropic da janela; `null` na visão global. */
        val accountLabel: String? = null,
        /** `true` quando a janela mostra todas as contas do servidor. */
        val isAdminOverview: Boolean = false,
        /**
         * Filtro "só quem está conectado".
         *
         * Mora no estado, e não num `remember` da tela, porque o laço ao vivo
         * republica o `Success` a cada poucos segundos e o filtro se desfaria
         * sozinho.
         */
        val onlyOnline: Boolean = false,
        /**
         * Texto do campo "Filtrar integrante".
         *
         * Mora aqui, ao lado de [onlyOnline], e não num `remember` da tela: os
         * dois respondem à mesma pergunta — o que a lista mostra — e dois donos
         * dariam duas respostas para [isFilteredEmpty]. Como [onlyOnline], é
         * preservado nas emissões do laço de 5s.
         */
        val query: String = "",
        /**
         * Contas com os integrantes à mostra, por [TeamPresenceAccountGroup.groupKey].
         *
         * Mesmo tratamento de [onlyOnline], e vazio por padrão: a visão global
         * abre recolhida, com uma linha por conta.
         */
        val expandedAccountKeys: Set<String> = emptySet(),
        /**
         * Quando o conteúdo mudou pela última vez — não quando a consulta
         * aconteceu. Carimbá-la a cada tique quebraria a igualdade do estado.
         */
        val lastChangedAt: Instant? = null,
        /**
         * Os relógios desta máquina e do servidor divergem.
         *
         * Booleano mais minutos arredondados, e **nunca** o desvio cru: um valor
         * em milissegundos mudaria a cada medida e reemitiria o estado inteiro de
         * 30 em 30 segundos.
         */
        val clockSkewSuspected: Boolean = false,
        val clockSkewMinutes: Long = 0L
    ) : TeamPresenceUiState {

        val onlineCount: Int
            get() = entries.count { entry -> entry.isOnline }

        val workingCount: Int
            get() = entries.count { entry -> entry.isWorkingNow }

        val totalCount: Int
            get() = entries.size

        val isEmpty: Boolean
            get() = entries.isEmpty()

        /**
         * Entradas depois dos dois filtros; é o que a lista percorre.
         *
         * O texto casa por trecho, sem diferenciar maiúsculas, contra apelido
         * **e** nome de máquina: quem procura "note" está atrás da máquina, e
         * filtrar só pelo apelido esconderia a linha que a tela mostra ao lado.
         */
        val visibleEntries: List<TeamMemberPresence>
            get() {
                val trimmed = query.trim()
                return entries.filter { entry ->
                    (!onlyOnline || entry.isOnline) &&
                        (
                            trimmed.isEmpty() ||
                                entry.alias.contains(trimmed, ignoreCase = true) ||
                                entry.machineLabel.contains(trimmed, ignoreCase = true)
                            )
                }
            }

        /** `true` quando o filtro escondeu tudo — estado vazio diferente de lista vazia. */
        val isFilteredEmpty: Boolean
            get() = !isEmpty && visibleEntries.isEmpty()

        /**
         * Integrantes agrupados por conta, na ordem em que a conta aparece.
         *
         * Fora da visão global sai um grupo só, com [accountKey] nulo, e a tela
         * nem desenha cabeçalho — assim ela percorre uma estrutura única nos dois
         * modos, em vez de ter dois laços que precisam concordar. Mesma anatomia
         * de `TeamUsageUiState.memberGroups`.
         */
        val presenceGroups: List<TeamPresenceAccountGroup>
            get() {
                val grouped = LinkedHashMap<String?, MutableList<TeamMemberPresence>>()
                for (entry in visibleEntries) {
                    grouped.getOrPut(entry.accountKey) { mutableListOf() }.add(entry)
                }
                return grouped.map { (accountKey, groupEntries) ->
                    TeamPresenceAccountGroup(
                        accountKey = accountKey,
                        accountLabel = groupEntries.firstNotNullOfOrNull { it.accountLabel },
                        entries = groupEntries
                    )
                }
            }

        /**
         * Se os integrantes desta conta aparecem na lista.
         *
         * Fora da visão global não há faixa para clicar, então recolher não é uma
         * possibilidade — sempre visível.
         */
        fun isAccountExpanded(group: TeamPresenceAccountGroup): Boolean {
            if (!isAdminOverview) {
                return true
            }
            return group.groupKey in expandedAccountKeys
        }
    }
}

/**
 * Uma conta e os integrantes dela na lista de presença.
 *
 * [accountLabel] é o rótulo que o administrador digitou ao emitir a chave.
 * Quando é nulo a conta não tem chave emitida e só o [accountKey] a identifica.
 */
data class TeamPresenceAccountGroup(
    val accountKey: String?,
    val accountLabel: String?,
    val entries: List<TeamMemberPresence>
) {
    /**
     * Identidade do grupo na tela, já normalizada — o `accountKey` é nulo fora da
     * visão global, e um `Set<String?>` no estado só para carregar esse nulo seria
     * pior que normalizá-lo aqui.
     */
    val groupKey: String
        get() = accountKey.orEmpty()

    val onlineCount: Int
        get() = entries.count { entry -> entry.isOnline }

    val workingCount: Int
        get() = entries.count { entry -> entry.isWorkingNow }

    val totalCount: Int
        get() = entries.size
}
