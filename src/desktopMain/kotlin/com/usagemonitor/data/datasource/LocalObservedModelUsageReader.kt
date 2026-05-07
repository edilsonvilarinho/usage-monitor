package com.usagemonitor.data.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.DriverManager

internal data class ObservedModelUsageSnapshot(
    val modelId: String,
    val modelName: String,
    val requestsLastFiveHours: Long,
    val requestsLastSevenDays: Long,
    val capturedAt: Instant
)

internal class LocalObservedModelUsageReader(
    private val databaseFile: File,
    private val providerId: String,
    private val nowProvider: () -> Instant,
    private val isTrackedModel: (String) -> Boolean,
    private val displayNameFor: (String) -> String
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isAvailable(): Boolean {
        return withContext(Dispatchers.IO) { databaseFile.exists() }
    }

    suspend fun loadTrackedModelUsage(): List<ObservedModelUsageSnapshot> {
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

                            if (payload.string("providerID") != providerId) {
                                continue
                            }

                            val modelId = payload.string("modelID") ?: continue
                            if (!isTrackedModel(modelId)) {
                                continue
                            }

                            val counts = countsByModel.getOrPut(modelId) {
                                MutableModelCounts(modelName = displayNameFor(modelId))
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
                ObservedModelUsageSnapshot(
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

    private data class MutableModelCounts(
        val modelName: String,
        var requestsLastFiveHours: Long = 0L,
        var requestsLastSevenDays: Long = 0L
    )

    companion object {
        const val FIVE_HOURS_MILLIS = 5L * 60L * 60L * 1000L
        const val SEVEN_DAYS_MILLIS = 7L * 24L * 60L * 60L * 1000L

        const val SELECT_RECENT_MESSAGES_SQL = """
            SELECT time_created, data
            FROM message
            WHERE time_created >= ?
            ORDER BY time_created DESC;
        """
    }
}
