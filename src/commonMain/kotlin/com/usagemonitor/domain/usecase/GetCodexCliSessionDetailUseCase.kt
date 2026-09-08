package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.repository.CodexCliSessionRepository

class GetCodexCliSessionDetailUseCase(
    private val repository: CodexCliSessionRepository
) {
    suspend operator fun invoke(sessionId: String): Result<CodexCliSessionDetail?> {
        return repository.getSessionDetail(sessionId)
    }
}
