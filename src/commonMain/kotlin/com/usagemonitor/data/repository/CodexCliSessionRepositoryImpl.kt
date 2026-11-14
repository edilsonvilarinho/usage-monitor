package com.usagemonitor.data.repository

import com.usagemonitor.domain.entity.CodexCliModelUsage

import com.usagemonitor.data.datasource.CodexCliSessionDataSource
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.repository.CodexCliSessionRepository

class CodexCliSessionRepositoryImpl(
    private val dataSource: CodexCliSessionDataSource
) : CodexCliSessionRepository {
    override suspend fun syncIndex(): Result<CodexCliSessionIndexReport> = suspendRunCatching {
        dataSource.syncIndex()
    }

    override suspend fun getSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> = suspendRunCatching {
        dataSource.readSessions(sinceEpochMillis)
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?> = suspendRunCatching {
        dataSource.readSession(sessionId)
    }

    override suspend fun getModelUsage(sinceEpochMillis: Long?): Result<List<CodexCliModelUsage>> = suspendRunCatching {
        dataSource.readModelUsage(sinceEpochMillis)
    }

    override suspend fun getTurnsSince(sinceEpochMillis: Long?): Result<List<CodexCliSessionTurn>> = suspendRunCatching {
        dataSource.readTurnsSince(sinceEpochMillis)
    }
}
