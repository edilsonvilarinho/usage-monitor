package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.toCodexUsageBreakdown
import com.usagemonitor.domain.repository.CodexCliSessionRepository
import kotlin.time.Clock

class GetCodexCliSessionsUseCase(
    private val repository: CodexCliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(sinceEpochMillis: Long? = null): Result<CodexCliSessionListResult> {
        val indexResult = repository.syncIndex()
        return repository.getSessions(sinceEpochMillis).map { sessions ->
            // Falha na leitura dos turnos deixa o quadro em null ("não medido"); a lista segue.
            val usageBreakdown = repository.getTurnsSince(sinceEpochMillis).getOrNull()?.toCodexUsageBreakdown()
            CodexCliSessionListResult(
                sessions = sessions,
                indexReport = indexResult.getOrNull(),
                indexError = indexResult.exceptionOrNull(),
                readAt = clock.now(),
                usageBreakdown = usageBreakdown
            )
        }
    }
}

data class CodexCliSessionListResult(
    val sessions: List<CodexCliSessionSummary>,
    val indexReport: CodexCliSessionIndexReport? = null,
    val indexError: Throwable? = null,
    val readAt: kotlin.time.Instant,
    val usageBreakdown: CliUsageBreakdown? = null
)
