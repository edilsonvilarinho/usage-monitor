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
            val session = authDataSource.loadSession()
            val response = apiDataSource.fetchCodexUsage(session)
            CodexMapper.toUsageStats(response)
        }
    }
}
