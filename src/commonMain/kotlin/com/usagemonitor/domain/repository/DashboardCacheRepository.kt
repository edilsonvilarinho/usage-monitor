package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats
import kotlinx.datetime.Instant

/**
 * Persiste o último snapshot exibido no dashboard para reidratar a UI
 * imediatamente ao reabrir o app antes do próximo ciclo de atualização.
 * Não é fonte de verdade para tendências — isso é responsabilidade de
 * `UsageHistoryRepository`.
 */
interface DashboardCacheRepository {
    suspend fun saveSnapshot(stats: List<ApiUsageStats>, capturedAt: Instant)

    suspend fun loadSnapshot(): List<ApiUsageStats>
}
