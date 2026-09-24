package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.AntigravityUsageDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AntigravityRepository
import kotlinx.coroutines.CancellationException

class AntigravityRepositoryImpl(
    private val dataSource: AntigravityUsageDataSource
) : AntigravityRepository {
    override suspend fun getUsage(): Result<ApiUsageStats> {
        return try {
            val quotas = dataSource.readUsage()
            if (quotas.isEmpty()) throw IllegalStateException("Antigravity usage is unavailable")
            Result.success(
                ApiUsageStats(
                    source = ApiSource.ANTIGRAVITY,
                    apiName = "Antigravity CLI",
                    quotas = emptyList(),
                    reportedModelQuotas = quotas
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            Result.failure(error)
        } catch (_: Throwable) {
            // Saída da TUI e erros nativos não são anexados à mensagem ou ao log.
            Result.failure(IllegalStateException("Antigravity CLI usage is unavailable"))
        }
    }
}
