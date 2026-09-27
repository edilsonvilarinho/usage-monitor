package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase

// Partes sem estado do `TeamUsageViewModel`, fora da classe pelo limite de
// 800 linhas (#305): nenhuma lê nem escreve o estado do ViewModel.

/** Membros já rotulados, a janela aplicada e o resumo, de um dos dois escopos. */
internal data class LoadedTeam(
    val members: List<TeamMemberUsage>,
    val window: CliRangeWindow,
    val breakdown: CliUsageBreakdown
)

/**
 * Junta os integrantes de todas as contas numa lista só.
 *
 * **A conta é a chave primária da ordem, e o consumo desce para dentro dela.**
 * Com o consumo no topo, a faixa de uma conta aparecia onde o integrante que
 * mais gastou a levasse: a mesma conta subia e descia a lista entre dois
 * tiques do laço ao vivo, e procurar uma pessoa exigia ler a tela inteira. O
 * rótulo é o e-mail que o administrador digitou ao emitir a chave e não muda
 * sozinho, então é ele que dá uma posição estável.
 *
 * Dentro da conta continua sendo quem mais consumiu primeiro — é a pergunta
 * que esta tela responde — e sem atividade no fim, em ordem alfabética.
 *
 * [TeamUsageUiState.Success.memberGroups] agrupa por ordem de primeira
 * aparição, então ordenar os integrantes assim já ordena as faixas de conta;
 * uma segunda ordenação lá seria um segundo dono da mesma decisão.
 *
 * A ordem é **total e determinística**, como a de `toTeamPresence`: duas
 * leituras iguais têm de produzir listas iguais, ou o `StateFlow` reemite e a
 * tela recompõe a cada 5s.
 */
internal fun flattenTeamAccounts(accounts: List<TeamAccountUsage>): List<TeamMemberUsage> {
    return accounts
        .flatMap { account ->
            account.snapshot.members.map { member ->
                val fallbackEmail = account.label?.trim()?.lowercase()?.takeIf { label ->
                    val at = label.indexOf('@')
                    at > 0 && at == label.lastIndexOf('@') &&
                        label.substring(at + 1).contains('.') && label.none(Char::isWhitespace)
                }
                member.copy(
                    accountKey = account.accountKey,
                    accountLabel = account.label,
                    accountEmail = account.accountEmail ?: fallbackEmail,
                    accountEmailSource = account.emailSource
                        ?: fallbackEmail?.let { com.usagemonitor.domain.entity.TeamAccountEmailSource.LABEL }
                )
            }
        }
        .sortedWith(
            // Conta sem rótulo emitido vai depois de todas as identificadas,
            // por um degrau próprio do comparador e não por uma sentinela de
            // texto: ela não tem e-mail para comparar, e abrir a lista com um
            // uuid cru seria pior que fechá-la com ele.
            compareBy<TeamMemberUsage> { member -> if (member.accountEmail == null) 1 else 0 }
                .thenBy { member -> member.accountEmail?.lowercase().orEmpty() }
                // Duas contas sem rótulo empatam acima; o uuid as separa e
                // mantém a ordem total.
                .thenBy { member -> member.accountKey.orEmpty() }
                .thenByDescending { member -> member.totalTokens }
                .thenBy { member -> member.alias.lowercase() }
        )
}

/**
 * Detalhe possível sem os turnos: só o que o agregado da lista já prova.
 *
 * É o que a tela mostra contra um servidor anterior à rota `/v1/session`.
 * Sem turno não há série nem distribuição de custo, e a tela deixa isso
 * explícito em vez de desenhar gráfico vazio. Só vira erro quando nem o
 * agregado existe — aí não há nada a apresentar.
 */
internal fun aggregatedTeamSessionDetail(
    current: TeamUsageUiState,
    deviceId: String,
    sessionId: String,
    scopedAccountKey: String?,
    computeAnalytics: ComputeCliSessionAnalyticsUseCase
): TeamSessionDetailUiState {
    val summary = findTeamSessionSummary(current, deviceId, sessionId, scopedAccountKey)
        ?: return TeamSessionDetailUiState.Error(
            deviceId = deviceId,
            sessionId = sessionId,
            message = SESSION_GONE_MESSAGE,
            accountKey = scopedAccountKey
        )

    return TeamSessionDetailUiState.Ready(
        deviceId = deviceId,
        sessionId = sessionId,
        accountKey = scopedAccountKey,
        result = CliSessionDetailResult(
            detail = CliSessionDetail(summary = summary, turns = emptyList()),
            analytics = computeAnalytics.fromSummary(summary)
        ),
        turnsUnavailable = true
    )
}

private fun findTeamSessionSummary(
    state: TeamUsageUiState,
    deviceId: String,
    sessionId: String,
    scopedAccountKey: String?
): CliSessionSummary? {
    val current = state as? TeamUsageUiState.Success ?: return null
    val member = current.members.firstOrNull { entry ->
        entry.deviceId == deviceId && entry.accountKey == scopedAccountKey
    } ?: return null
    return member.sessions.firstOrNull { session -> session.sessionId == sessionId }
}
