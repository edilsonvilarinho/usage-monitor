package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CliSessionDataSource
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.toUsageBreakdown
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

    override suspend fun getUsageBreakdown(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<CliUsageBreakdown> {
        return runCatching { dataSource.readUsageGroups(profileId, sinceEpochMillis).toUsageBreakdown() }
    }

    override suspend fun getHourlyUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliHourlyUsageRow>> {
        return runCatching { dataSource.readHourlyUsage(profileId, sinceEpochMillis) }
    }

    override suspend fun getToolUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliToolUsage>> {
        return runCatching { dataSource.readToolUsage(profileId, sinceEpochMillis) }
    }
}
