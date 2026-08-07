package com.usagemonitor.data.dto

import kotlinx.serialization.Serializable

/**
 * Snapshot serializável do último `UiState.Success` exibido no dashboard.
 * Usado apenas para reidratar a UI imediatamente ao reabrir o app antes do
 * próximo ciclo de atualização — não substitui o histórico em SQLite
 * (`UsageSnapshotRecord`), que é a fonte de verdade para tendências.
 */
@Serializable
data class DashboardCacheDto(
    val savedAtEpochMillis: Long,
    val entries: List<ApiUsageStatsCacheDto>
)

@Serializable
data class ApiUsageStatsCacheDto(
    val targetKey: String,
    val source: String,
    val apiName: String,
    val quotas: List<QuotaInfoCacheDto>,
    val accountSource: String? = null,
    val accountProviderAccountId: String? = null,
    val accountWorkspaceId: String? = null,
    val accountEmail: String? = null,
    val accountWorkspaceName: String? = null,
    val profileLabel: String? = null,
    val notices: List<String> = emptyList()
)

@Serializable
data class QuotaInfoCacheDto(
    val label: String,
    val used: Long,
    val total: Long,
    val periodEndAtEpochMillis: Long,
    val hasKnownResetAt: Boolean = true,
    val periodType: String,
    val unit: String,
    val rawUsed: Long = 0L,
    val rawTotal: Long = 0L
)
