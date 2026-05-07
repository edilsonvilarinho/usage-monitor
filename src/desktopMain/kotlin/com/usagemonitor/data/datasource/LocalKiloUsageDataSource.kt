package com.usagemonitor.data.datasource

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

class LocalKiloUsageDataSource(
    private val databaseFile: File = defaultDatabaseFile(),
    private val nowProvider: () -> Instant = { Clock.System.now() }
) : KiloUsageDataSource {

    private val reader = LocalObservedModelUsageReader(
        databaseFile = databaseFile,
        providerId = KILO_PROVIDER_ID,
        nowProvider = nowProvider,
        isTrackedModel = ::isFreeKiloModel,
        displayNameFor = ::displayNameFor
    )

    override suspend fun isAvailable(): Boolean {
        return reader.isAvailable()
    }

    override suspend fun loadFreeModelUsage(): List<KiloModelUsageSnapshot> {
        return reader.loadTrackedModelUsage().map { snapshot ->
            KiloModelUsageSnapshot(
                modelId = snapshot.modelId,
                modelName = snapshot.modelName,
                requestsLastFiveHours = snapshot.requestsLastFiveHours,
                requestsLastSevenDays = snapshot.requestsLastSevenDays,
                capturedAt = snapshot.capturedAt
            )
        }
    }

    private fun isFreeKiloModel(modelId: String): Boolean {
        return modelId == "kilo-auto/free" ||
            modelId.endsWith("/free") ||
            modelId.contains(":free")
    }

    private fun displayNameFor(modelId: String): String {
        return when (modelId) {
            "kilo-auto/free" -> "Auto Free Kilo Gateway"
            else -> modelId
        }
    }

    private companion object {
        const val KILO_PROVIDER_ID = "kilo"

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".local/share/kilo/kilo.db")
        }
    }
}
