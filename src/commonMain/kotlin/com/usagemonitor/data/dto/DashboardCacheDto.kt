package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Snapshot serializável do último `UiState.Success` exibido no dashboard.
 * Usado apenas para reidratar a UI imediatamente ao reabrir o app antes do
 * próximo ciclo de atualização — não substitui o histórico em SQLite
 * (`UsageSnapshotRecord`), que é a fonte de verdade para tendências.
 */
@Serializable
data class DashboardCacheDto(
    @SerialName("savedAtEpochMillis") val savedAtEpochMillis: Long,
    @SerialName("entries") val entries: List<ApiUsageStatsCacheDto>
)

@Serializable
data class ApiUsageStatsCacheDto(
    @SerialName("targetKey") val targetKey: String,
    @SerialName("source") val source: String,
    @SerialName("apiName") val apiName: String,
    @SerialName("quotas") val quotas: List<QuotaInfoCacheDto>,
    @SerialName("accountSource") val accountSource: String? = null,
    @SerialName("accountProviderAccountId") val accountProviderAccountId: String? = null,
    @SerialName("accountWorkspaceId") val accountWorkspaceId: String? = null,
    @SerialName("accountEmail") val accountEmail: String? = null,
    @SerialName("accountWorkspaceName") val accountWorkspaceName: String? = null,
    @SerialName("profileLabel") val profileLabel: String? = null,
    @SerialName("notices") val notices: List<String> = emptyList(),
    @SerialName("planLabel") val planLabel: String? = null,
    // Default nulo mantém legível o cache anterior à #269; a leitura dele herda
    // o `savedAtEpochMillis` do arquivo.
    @SerialName("fetchedAtEpochMillis") val fetchedAtEpochMillis: Long? = null,
    // Nulo no cache gravado antes dos resets guardados (issue #571); 0 é "nenhum", não ausência.
    @SerialName("bankedResetCount") val bankedResetCount: Int? = null
)

@Serializable
data class QuotaInfoCacheDto(
    @SerialName("label") val label: String,
    @SerialName("used") val used: Long,
    @SerialName("total") val total: Long,
    @SerialName("periodEndAtEpochMillis") val periodEndAtEpochMillis: Long,
    @SerialName("hasKnownResetAt") val hasKnownResetAt: Boolean = true,
    @SerialName("periodType") val periodType: String,
    @SerialName("unit") val unit: String,
    @SerialName("rawUsed") val rawUsed: Long = 0L,
    @SerialName("rawTotal") val rawTotal: Long = 0L,
    // Default mantém legível o cache gravado antes da leitura de créditos.
    @SerialName("currencyCode") val currencyCode: String = "USD",
    // Nulo no cache gravado antes da marca de ritmo (issue #327).
    @SerialName("periodStartAtEpochMillis") val periodStartAtEpochMillis: Long? = null,
    // Nulo no cache gravado antes do bloqueio de créditos (issue #567).
    @SerialName("blockReason") val blockReason: String? = null
)
