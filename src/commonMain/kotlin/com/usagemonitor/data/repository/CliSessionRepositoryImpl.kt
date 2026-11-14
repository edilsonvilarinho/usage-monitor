package com.usagemonitor.data.repository

import com.usagemonitor.domain.entity.OutputThroughput

import com.usagemonitor.data.datasource.CliSessionDataSource
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.toUsageBreakdown
import com.usagemonitor.domain.repository.CliSessionRepository

class CliSessionRepositoryImpl(
    private val dataSource: CliSessionDataSource
) : CliSessionRepository {

    override suspend fun syncIndex(): Result<CliSessionIndexReport> {
        return suspendRunCatching { dataSource.syncIndex() }
    }

    override suspend fun getSessions(
        profileId: String?,
        sinceEpochMillis: Long?
    ): Result<List<CliSessionSummary>> {
        return suspendRunCatching { dataSource.readSessions(profileId, sinceEpochMillis) }
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> {
        return suspendRunCatching { dataSource.readSession(sessionId) }
    }

    override suspend fun getUsageBreakdown(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<CliUsageBreakdown> {
        return suspendRunCatching {
            val rows = dataSource.readUsageGroups(profileId, sinceEpochMillis)

            // A hora é acessória, como a grade e as ferramentas: uma falha nela
            // não pode derrubar o resumo, que é a informação principal da aba.
            // Sem medida as horas saem nulas — "não se sabe", não "zero".
            val activeTimes = suspendRunCatching { dataSource.readSessionActiveTimes(profileId, sinceEpochMillis) }
                .getOrNull()
                ?.associate { entry -> entry.sessionId to entry.activeMillis }
                .orEmpty()

            rows.toUsageBreakdown(activeTimes)
        }
    }

    override suspend fun getModelThroughputs(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<Map<String, OutputThroughput>> {
        return suspendRunCatching { dataSource.readModelThroughputs(profileId, sinceEpochMillis) }
    }

    override suspend fun getHourlyUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliHourlyUsageRow>> {
        return suspendRunCatching { dataSource.readHourlyUsage(profileId, sinceEpochMillis) }
    }

    override suspend fun getToolUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliToolUsage>> {
        return suspendRunCatching { dataSource.readToolUsage(profileId, sinceEpochMillis) }
    }

    override suspend fun getSessionTails(sessionIds: Collection<String>): Result<List<CliSessionTail>> {
        return suspendRunCatching { dataSource.readSessionTails(sessionIds) }
    }
}
