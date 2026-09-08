package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.repository.CodexCliSessionRepository
import kotlinx.datetime.Clock

class GetCodexCliSessionsUseCase(
    private val repository: CodexCliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(sinceEpochMillis: Long? = null): Result<CodexCliSessionListResult> {
        val indexResult = repository.syncIndex()
        return repository.getSessions(sinceEpochMillis).map { sessions ->
            CodexCliSessionListResult(
                sessions = sessions,
                indexReport = indexResult.getOrNull(),
                indexError = indexResult.exceptionOrNull(),
                readAt = clock.now()
            )
        }
    }
}

data class CodexCliSessionListResult(
    val sessions: List<CodexCliSessionSummary>,
    val indexReport: CodexCliSessionIndexReport? = null,
    val indexError: Throwable? = null,
    val readAt: kotlinx.datetime.Instant
)
