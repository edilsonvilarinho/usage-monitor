package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.repository.CliSessionRepository

/** Carrega o detalhe da sessão já com as métricas derivadas. */
class GetCliSessionDetailUseCase(
    private val repository: CliSessionRepository,
    private val computeAnalytics: ComputeCliSessionAnalyticsUseCase = ComputeCliSessionAnalyticsUseCase()
) {
    suspend operator fun invoke(sessionId: String): Result<CliSessionDetailResult?> {
        return repository.getSessionDetail(sessionId).map { detail ->
            detail?.let { loaded ->
                CliSessionDetailResult(detail = loaded, analytics = computeAnalytics(loaded))
            }
        }
    }
}

data class CliSessionDetailResult(
    val detail: CliSessionDetail,
    val analytics: CliSessionAnalytics
)
