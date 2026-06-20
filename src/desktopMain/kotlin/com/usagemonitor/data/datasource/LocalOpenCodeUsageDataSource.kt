package com.usagemonitor.data.datasource

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.File

class LocalOpenCodeUsageDataSource(
    private val databaseFile: File = defaultDatabaseFile(),
    private val nowProvider: () -> Instant = { Clock.System.now() }
) : OpenCodeUsageDataSource, AutoCloseable {

    private val reader = LocalObservedModelUsageReader(
        databaseFile = databaseFile,
        providerId = OPENCODE_PROVIDER_ID,
        nowProvider = nowProvider,
        isTrackedModel = ::isFreeZenModel,
        displayNameFor = ::displayNameFor
    )

    override suspend fun isAvailable(): Boolean {
        return reader.isAvailable()
    }

    override suspend fun loadFreeModelUsage(): List<OpenCodeModelUsageSnapshot> {
        return reader.loadTrackedModelUsage().map { snapshot ->
            OpenCodeModelUsageSnapshot(
                modelId = snapshot.modelId,
                modelName = snapshot.modelName,
                requestsLastFiveHours = snapshot.requestsLastFiveHours,
                requestsLastSevenDays = snapshot.requestsLastSevenDays,
                capturedAt = snapshot.capturedAt
            )
        }
    }

    override fun close() {
        reader.close()
    }

    private fun isFreeZenModel(modelId: String): Boolean {
        return modelId.endsWith("-free") || modelId == "big-pickle"
    }

    private fun displayNameFor(modelId: String): String {
        return when (modelId) {
            "big-pickle" -> "Big Pickle"
            "minimax-m2.5-free" -> "MiniMax M2.5 Free"
            "hy3-preview-free" -> "Hy3 Preview Free"
            "ling-2.6-flash-free" -> "Ling 2.6 Flash Free"
            "nemotron-3-super-free" -> "Nemotron 3 Super Free"
            "trinity-large-preview-free" -> "Trinity Large Preview Free"
            else -> modelId
                .split('-', '.', '_')
                .filter { token -> token.isNotBlank() }
                .joinToString(" ") { token -> token.replaceFirstChar(Char::titlecase) }
        }
    }

    private companion object {
        const val OPENCODE_PROVIDER_ID = "opencode"

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".local/share/opencode/opencode.db")
        }
    }
}
