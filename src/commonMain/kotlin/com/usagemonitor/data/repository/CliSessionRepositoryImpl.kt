package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CliSessionDataSource
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.repository.CliSessionRepository

class CliSessionRepositoryImpl(
    private val dataSource: CliSessionDataSource
) : CliSessionRepository {

    override suspend fun syncIndex(): Result<CliSessionIndexReport> {
        return runCatching { dataSource.syncIndex() }
    }

    override suspend fun getSessions(
        profileId: String?,
        includeHidden: Boolean
    ): Result<List<CliSessionSummary>> {
        return runCatching { dataSource.readSessions(profileId, includeHidden) }
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> {
        return runCatching { dataSource.readSession(sessionId) }
    }

    override suspend fun setSessionHidden(sessionId: String, hidden: Boolean): Result<Unit> {
        return runCatching { dataSource.setHidden(sessionId, hidden) }
    }
}
