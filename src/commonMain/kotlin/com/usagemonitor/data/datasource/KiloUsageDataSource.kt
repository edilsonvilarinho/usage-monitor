package com.usagemonitor.data.datasource

import kotlinx.datetime.Instant

/**
 * Fonte local para ler atividade observada do Kilo.
 *
 * A implementação concreta vive no desktopMain porque consulta o SQLite
 * persistido pelo cliente local em disco.
 */
interface KiloUsageDataSource {
    suspend fun isAvailable(): Boolean
    suspend fun loadFreeModelUsage(): List<KiloModelUsageSnapshot>
}

data class KiloModelUsageSnapshot(
    val modelId: String,
    val modelName: String,
    val requestsLastFiveHours: Long,
    val requestsLastSevenDays: Long,
    val capturedAt: Instant
)
