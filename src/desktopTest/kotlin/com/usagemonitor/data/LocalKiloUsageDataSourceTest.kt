package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalKiloUsageDataSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalKiloUsageDataSourceTest {

    @Test
    fun `counts Kilo free models from the local database`() = runTest {
        val tempDir = createTempDirectory()
        val databaseFile = tempDir.resolve("kilo.db").toFile()
        seedDatabase(databaseFile)

        val dataSource = LocalKiloUsageDataSource(
            databaseFile = databaseFile,
            nowProvider = { Instant.parse("2026-05-07T15:00:00Z") }
        )

        val snapshots = dataSource.loadFreeModelUsage().sortedBy { it.modelName }

        assertEquals(2, snapshots.size)
        assertEquals("Auto Free Kilo Gateway", snapshots[0].modelName)
        assertEquals(1L, snapshots[0].requestsLastFiveHours)
        assertEquals(2L, snapshots[0].requestsLastSevenDays)
        assertEquals("z-ai/glm-5.5:free", snapshots[1].modelName)
        assertEquals(1L, snapshots[1].requestsLastFiveHours)
        assertEquals(1L, snapshots[1].requestsLastSevenDays)

        Files.deleteIfExists(tempDir.resolve("kilo.db"))
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun `returns empty list when Kilo local database is missing`() = runTest {
        val tempDir = createTempDirectory()
        val missingDatabase = tempDir.resolve("missing-kilo.db").toFile()

        val dataSource = LocalKiloUsageDataSource(
            databaseFile = missingDatabase,
            nowProvider = { Instant.parse("2026-05-07T15:00:00Z") }
        )

        assertTrue(dataSource.loadFreeModelUsage().isEmpty())

        Files.deleteIfExists(tempDir)
    }

    private fun seedDatabase(databaseFile: File) {
        DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE message (
                        id TEXT PRIMARY KEY,
                        session_id TEXT NOT NULL,
                        time_created INTEGER NOT NULL,
                        time_updated INTEGER NOT NULL,
                        data TEXT NOT NULL
                    );
                    """.trimIndent()
                )
            }

            connection.prepareStatement(
                """
                INSERT INTO message (id, session_id, time_created, time_updated, data)
                VALUES (?, 'session', ?, ?, ?);
                """.trimIndent()
            ).use { statement ->
                insertMessage(
                    statement = statement,
                    id = "1",
                    createdAt = Instant.parse("2026-05-07T14:00:00Z").toEpochMilliseconds(),
                    payload = assistantPayload("kilo", "kilo-auto/free")
                )
                insertMessage(
                    statement = statement,
                    id = "2",
                    createdAt = Instant.parse("2026-05-06T10:00:00Z").toEpochMilliseconds(),
                    payload = assistantPayload("kilo", "kilo-auto/free")
                )
                insertMessage(
                    statement = statement,
                    id = "3",
                    createdAt = Instant.parse("2026-05-07T13:00:00Z").toEpochMilliseconds(),
                    payload = assistantPayload("kilo", "z-ai/glm-5.5:free")
                )
                insertMessage(
                    statement = statement,
                    id = "4",
                    createdAt = Instant.parse("2026-05-07T12:00:00Z").toEpochMilliseconds(),
                    payload = assistantPayload("kilo", "openai/gpt-5")
                )
                insertMessage(
                    statement = statement,
                    id = "5",
                    createdAt = Instant.parse("2026-05-07T11:00:00Z").toEpochMilliseconds(),
                    payload = userPayload("kilo", "kilo-auto/free")
                )
            }
        }
    }

    private fun insertMessage(
        statement: java.sql.PreparedStatement,
        id: String,
        createdAt: Long,
        payload: String
    ) {
        statement.setString(1, id)
        statement.setLong(2, createdAt)
        statement.setLong(3, createdAt)
        statement.setString(4, payload)
        statement.executeUpdate()
    }

    private fun assistantPayload(providerId: String, modelId: String): String {
        return """{"role":"assistant","providerID":"$providerId","modelID":"$modelId"}"""
    }

    private fun userPayload(providerId: String, modelId: String): String {
        return """{"role":"user","providerID":"$providerId","modelID":"$modelId"}"""
    }
}
