package com.usagemonitor.data.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.DriverManager

class LocalOpenCodeUsageDataSource(
    private val databaseFile: File = defaultDatabaseFile(),
    private val nowProvider: () -> Instant = { Clock.System.now() }
) : OpenCodeUsageDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadFreeModelUsage(): List<OpenCodeModelUsageSnapshot> {
        return withContext(Dispatchers.IO) {
            if (!databaseFile.exists()) {
                return@withContext emptyList()
            }

            val now = nowProvider()
            val fiveHourCutoff = now.toEpochMilliseconds() - FIVE_HOURS_MILLIS
            val sevenDayCutoff = now.toEpochMilliseconds() - SEVEN_DAYS_MILLIS
            val countsByModel = linkedMapOf<String, MutableModelCounts>()

            DriverManager.getConnection(databaseUrl()).use { connection ->
                connection.prepareStatement(SELECT_RECENT_MESSAGES_SQL).use { statement ->
                    statement.setLong(1, sevenDayCutoff)

                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            val createdAt = resultSet.getLong("time_created")
                            val payload = runCatching {
                                json.parseToJsonElement(resultSet.getString("data")).jsonObject
                            }.getOrNull() ?: continue

                            if (payload.string("role") != "assistant") {
                                continue
                            }

                            if (payload.string("providerID") != OPENCODE_PROVIDER_ID) {
                                continue
                            }

                            val modelId = payload.string("modelID") ?: continue
                            if (!isFreeZenModel(modelId)) {
                                continue
                            }

                            val displayName = displayNameFor(modelId)
                            val counts = countsByModel.getOrPut(modelId) {
                                MutableModelCounts(modelName = displayName)
                            }

                            counts.requestsLastSevenDays += 1L
                            if (createdAt >= fiveHourCutoff) {
                                counts.requestsLastFiveHours += 1L
                            }
                        }
                    }
                }
            }

            countsByModel.map { (modelId, counts) ->
                OpenCodeModelUsageSnapshot(
                    modelId = modelId,
                    modelName = counts.modelName,
                    requestsLastFiveHours = counts.requestsLastFiveHours,
                    requestsLastSevenDays = counts.requestsLastSevenDays,
                    capturedAt = now
                )
            }
        }
    }

    private fun databaseUrl(): String {
        return "jdbc:sqlite:${databaseFile.absolutePath}"
    }

    private fun JsonObject.string(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
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

    private data class MutableModelCounts(
        val modelName: String,
        var requestsLastFiveHours: Long = 0L,
        var requestsLastSevenDays: Long = 0L
    )

    private companion object {
        const val OPENCODE_PROVIDER_ID = "opencode"
        const val FIVE_HOURS_MILLIS = 5L * 60L * 60L * 1000L
        const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L

        const val SELECT_RECENT_MESSAGES_SQL = """
            SELECT time_created, data
            FROM message
            WHERE time_created >= ?
            ORDER BY time_created DESC;
        """

        fun defaultDatabaseFile(): File {
            val homeDir = System.getProperty("user.home")
                ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

            return File(homeDir, ".local/share/opencode/opencode.db")
        }
    }
}
