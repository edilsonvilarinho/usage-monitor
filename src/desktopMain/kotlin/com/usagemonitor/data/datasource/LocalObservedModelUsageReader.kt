package com.usagemonitor.data.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.io.File

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
) : AutoCloseable {

    private val connectionManager = SqliteConnectionManager(databaseFile)

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

            connectionManager.useConnection { connection ->
                connection.prepareStatement(SELECT_RECENT_MESSAGES_SQL).use { statement ->
                    statement.setLong(1, fiveHourCutoff)
                    statement.setLong(2, sevenDayCutoff)
                    statement.setString(3, providerId)

                    statement.executeQuery().use { resultSet ->
                        while (resultSet.next()) {
                            val modelId = resultSet.getString("model_id") ?: continue
                            if (!isTrackedModel(modelId)) {
                                continue
                            }

                            val counts = countsByModel.getOrPut(modelId) {
                                MutableModelCounts(modelName = displayNameFor(modelId))
                            }
                            counts.requestsLastFiveHours = resultSet.getLong("requests_last_five_hours")
                            counts.requestsLastSevenDays = resultSet.getLong("requests_last_seven_days")
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

    override fun close() {
        connectionManager.close()
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
            SELECT
                json_extract(data, '$.modelID') AS model_id,
                SUM(CASE WHEN time_created >= ? THEN 1 ELSE 0 END) AS requests_last_five_hours,
                COUNT(*) AS requests_last_seven_days
            FROM message
            WHERE time_created >= ?
              AND json_extract(data, '$.role') = 'assistant'
              AND json_extract(data, '$.providerID') = ?
            GROUP BY json_extract(data, '$.modelID')
            ORDER BY requests_last_seven_days DESC, model_id ASC;
        """
    }
}
