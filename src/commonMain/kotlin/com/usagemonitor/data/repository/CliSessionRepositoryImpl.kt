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
        sinceEpochMillis: Long?
    ): Result<List<CliSessionSummary>> {
        return runCatching { dataSource.readSessions(profileId, sinceEpochMillis) }
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> {
        return runCatching { dataSource.readSession(sessionId) }
    }
}
