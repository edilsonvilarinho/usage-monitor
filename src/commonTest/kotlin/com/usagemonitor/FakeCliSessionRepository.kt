package com.usagemonitor

import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.repository.CliSessionRepository

/**
 * Índice CLI de mentira, para exercitar caso de uso e view model **reais**.
 *
 * Existe como arquivo próprio porque dois testes em pacotes diferentes precisam
 * dele — e porque a alternativa seria herdar do caso de uso e sobrescrever o
 * método, que é exatamente o padrão que o CLAUDE.md registra como cobertura sem
 * costura: com o método sobrescrito, nenhuma linha do código real executa.
 */
internal class FakeCliSessionRepository(
    var sessions: List<CliSessionSummary> = emptyList()
) : CliSessionRepository {

    var syncCalls: Int = 0
    var sessionCalls: Int = 0
    var lastSinceEpochMillis: Long? = null
    var lastProfileId: String? = null
    var sessionsFailure: Throwable? = null

    override suspend fun syncIndex(): Result<CliSessionIndexReport> {
        syncCalls += 1
        return Result.success(CliSessionIndexReport())
    }

    override suspend fun getSessions(
        profileId: String?,
        sinceEpochMillis: Long?
    ): Result<List<CliSessionSummary>> {
        sessionCalls += 1
        lastProfileId = profileId
        lastSinceEpochMillis = sinceEpochMillis
        sessionsFailure?.let { failure -> return Result.failure(failure) }
        return Result.success(sessions)
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> {
        return Result.success(null)
    }

    override suspend fun getUsageBreakdown(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<CliUsageBreakdown> {
        return Result.success(CliUsageBreakdown())
    }

    override suspend fun getHourlyUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliHourlyUsageRow>> {
        return Result.success(emptyList())
    }

    override suspend fun getToolUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliToolUsage>> {
        return Result.success(emptyList())
    }

    override suspend fun getSessionTails(sessionIds: Collection<String>): Result<List<CliSessionTail>> {
        return Result.success(emptyList())
    }
}
