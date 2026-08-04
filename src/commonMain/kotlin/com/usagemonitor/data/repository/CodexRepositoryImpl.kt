package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CodexAuthDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.CodexMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.CodexRepository

class CodexRepositoryImpl(
    private val authDataSource: CodexAuthDataSource,
    private val apiDataSource: RemoteApiDataSource
) : CodexRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            fetchStableUsage()
        }
    }

    private suspend fun fetchStableUsage(): ApiUsageStats {
        for (attempt in 0..1) {
            val session = authDataSource.loadSession()
            val fiveHourResponse = apiDataSource.fetchCodexFiveHourUsage(session)
            val fiveHourQuota = CodexMapper.toFiveHourQuota(fiveHourResponse)
            val weeklyQuota = runCatching {
                apiDataSource.fetchCodexWeeklyUsage(session)
            }.getOrNull()?.let(CodexMapper::toWeeklyQuota)

            val stats = CodexMapper.mergeUsage(
                fiveHourQuota = fiveHourQuota,
                weeklyQuota = weeklyQuota
            ).copy(accountContext = session.accountContext)

            if (authDataSource.isSessionCurrent(session)) {
                return stats
            }
        }

        throw IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE)
    }

    companion object {
        const val ACCOUNT_CHANGED_DURING_FETCH_MESSAGE =
            "A conta do Codex mudou durante a atualização. Aguarde o login terminar e atualize novamente."
    }
}
