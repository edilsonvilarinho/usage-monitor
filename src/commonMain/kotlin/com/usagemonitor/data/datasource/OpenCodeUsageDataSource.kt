package com.usagemonitor.data.datasource

import kotlinx.datetime.Instant

/**
 * Fonte local para ler atividade observada do OpenCode.
 *
 * A implementação concreta vive no desktopMain porque consulta o SQLite
 * persistido pelo cliente local em disco.
 */
interface OpenCodeUsageDataSource {
    suspend fun loadFreeModelUsage(): List<OpenCodeModelUsageSnapshot>
}

data class OpenCodeModelUsageSnapshot(
    val modelId: String,
    val modelName: String,
    val requestsLastFiveHours: Long,
    val requestsLastSevenDays: Long,
    val capturedAt: Instant
)
