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
            val usageResponse = apiDataSource.fetchCodexFiveHourUsage(session)
            val stats = CodexMapper.toUsageStats(usageResponse)
                .copy(accountContext = session.accountContext)
            if (stats.quotas.isEmpty()) {
                throw IllegalStateException("A resposta do Codex não trouxe nenhuma janela utilizável.")
            }

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
