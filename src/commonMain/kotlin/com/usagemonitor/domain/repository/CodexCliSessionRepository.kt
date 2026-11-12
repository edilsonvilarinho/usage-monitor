package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliModelUsage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn

interface CodexCliSessionRepository {
    suspend fun syncIndex(): Result<CodexCliSessionIndexReport>

    suspend fun getSessions(
        sinceEpochMillis: Long? = null
    ): Result<List<CodexCliSessionSummary>>

    suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?>

    /** Uso por modelo (#386); vazio quando a fonte não mede. */
    suspend fun getModelUsage(sinceEpochMillis: Long? = null): Result<List<CodexCliModelUsage>> = Result.success(emptyList())

    /** Turnos desde o corte, para o quadro de uso (#560); `null` lê todos. Vazio quando a fonte não mede. */
    suspend fun getTurnsSince(sinceEpochMillis: Long? = null): Result<List<CodexCliSessionTurn>> = Result.success(emptyList())
}
