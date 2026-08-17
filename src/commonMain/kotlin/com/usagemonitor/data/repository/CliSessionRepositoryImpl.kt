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
        return runCatching {
            val rows = dataSource.readUsageGroups(profileId, sinceEpochMillis)

            // A hora é acessória, como a grade e as ferramentas: uma falha nela
            // não pode derrubar o resumo, que é a informação principal da aba.
            // Sem medida as horas saem nulas — "não se sabe", não "zero".
            val activeTimes = runCatching { dataSource.readSessionActiveTimes(profileId, sinceEpochMillis) }
                .getOrNull()
                ?.associate { entry -> entry.sessionId to entry.activeMillis }
                .orEmpty()

            rows.toUsageBreakdown(activeTimes)
        }
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
