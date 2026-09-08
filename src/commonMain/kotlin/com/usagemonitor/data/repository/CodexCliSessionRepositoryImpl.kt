package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CodexCliSessionDataSource
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.repository.CodexCliSessionRepository

class CodexCliSessionRepositoryImpl(
    private val dataSource: CodexCliSessionDataSource
) : CodexCliSessionRepository {
    override suspend fun syncIndex(): Result<CodexCliSessionIndexReport> = runCatching {
        dataSource.syncIndex()
    }

    override suspend fun getSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> = runCatching {
        dataSource.readSessions(sinceEpochMillis)
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?> = runCatching {
        dataSource.readSession(sessionId)
    }
}
