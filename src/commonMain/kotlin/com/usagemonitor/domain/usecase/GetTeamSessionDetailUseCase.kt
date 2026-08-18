package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.repository.TeamAdminRepository
import com.usagemonitor.domain.repository.TeamUsageRepository

/**
 * Detalhe de uma sessão de outra máquina, já com as métricas derivadas.
 *
 * Espelha [GetCliSessionDetailUseCase] de propósito, inclusive o
 * [ComputeCliSessionAnalyticsUseCase]: o painel é o mesmo dos dois lados, então
 * os números têm de sair do mesmo cálculo. Só a origem dos turnos muda — aqui
 * eles vêm do servidor de time, lá do índice SQLite local.
 *
 * `null` significa que o servidor não tem o detalhe (sessão desconhecida ou
 * versão anterior à rota). Quem chama decide o que mostrar no lugar; não é erro.
 */
class GetTeamSessionDetailUseCase(
    private val repository: TeamUsageRepository,
    private val computeAnalytics: ComputeCliSessionAnalyticsUseCase = ComputeCliSessionAnalyticsUseCase()
) {
    suspend operator fun invoke(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetailResult?> {
        return repository.fetchSessionDetail(
            accountKey = accountKey,
            deviceId = deviceId,
            sessionId = sessionId
        ).withAnalytics(computeAnalytics)
    }
}

/**
 * Detalhe equivalente para a visão global, lido exclusivamente com autoridade
 * administrativa. Não reutiliza a chave de time da instalação.
 */
class GetAdminTeamSessionDetailUseCase(
    private val repository: TeamAdminRepository,
    private val computeAnalytics: ComputeCliSessionAnalyticsUseCase = ComputeCliSessionAnalyticsUseCase()
) {
    suspend operator fun invoke(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetailResult?> {
        return repository.fetchSessionDetail(
            accountKey = accountKey,
            deviceId = deviceId,
            sessionId = sessionId
        ).withAnalytics(computeAnalytics)
    }
}

private fun Result<CliSessionDetail?>.withAnalytics(
    computeAnalytics: ComputeCliSessionAnalyticsUseCase
): Result<CliSessionDetailResult?> = map { detail ->
    if (detail == null) {
        null
    } else {
        CliSessionDetailResult(detail = detail, analytics = computeAnalytics(detail))
    }
}
