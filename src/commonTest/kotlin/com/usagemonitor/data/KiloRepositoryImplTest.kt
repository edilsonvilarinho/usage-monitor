package com.usagemonitor.data

import com.usagemonitor.data.datasource.KiloModelUsageSnapshot
import com.usagemonitor.data.datasource.KiloUsageDataSource
import com.usagemonitor.data.repository.KiloRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KiloRepositoryImplTest {

    @Test
    fun `returns empty quotas when no Kilo free activity exists`() = runTest {
        val repository = KiloRepositoryImpl(
            dataSource = object : KiloUsageDataSource {
                override suspend fun isAvailable(): Boolean = true
                override suspend fun loadFreeModelUsage(): List<KiloModelUsageSnapshot> = emptyList()
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(ApiSource.KILO, result.source)
        assertTrue(result.quotas.isEmpty())
    }

    @Test
    fun `maps each Kilo free model into 5h and 7d request windows`() = runTest {
        val repository = KiloRepositoryImpl(
            dataSource = object : KiloUsageDataSource {
                override suspend fun isAvailable(): Boolean = true

                override suspend fun loadFreeModelUsage(): List<KiloModelUsageSnapshot> {
                    return listOf(
                        KiloModelUsageSnapshot(
                            modelId = "z-ai/glm-5.5:free",
                            modelName = "z-ai/glm-5.5:free",
                            requestsLastFiveHours = 9,
                            requestsLastSevenDays = 41,
                            capturedAt = Instant.parse("2026-05-07T15:00:00Z")
                        )
                    )
                }
            }
        )

        val quotas = repository.getUsage().getOrThrow().quotas

        assertEquals(2, quotas.size)
        assertEquals("z-ai/glm-5.5:free 5h", quotas[0].label)
        assertEquals(PeriodType.INTERVAL, quotas[0].periodType)
        assertEquals(9L, quotas[0].used)
        assertEquals("z-ai/glm-5.5:free 7d", quotas[1].label)
        assertEquals(PeriodType.WEEKLY, quotas[1].periodType)
        assertEquals(41L, quotas[1].used)
    }

    @Test
    fun `sorts Kilo models by display name`() = runTest {
        val repository = KiloRepositoryImpl(
            dataSource = object : KiloUsageDataSource {
                override suspend fun isAvailable(): Boolean = true

                override suspend fun loadFreeModelUsage(): List<KiloModelUsageSnapshot> {
                    return listOf(
                        KiloModelUsageSnapshot(
                            modelId = "z-model:free",
                            modelName = "z-model:free",
                            requestsLastFiveHours = 1,
                            requestsLastSevenDays = 1,
                            capturedAt = Instant.parse("2026-05-07T15:00:00Z")
                        ),
                        KiloModelUsageSnapshot(
                            modelId = "kilo-auto/free",
                            modelName = "Auto Free Kilo Gateway",
                            requestsLastFiveHours = 2,
                            requestsLastSevenDays = 3,
                            capturedAt = Instant.parse("2026-05-07T15:00:00Z")
                        )
                    )
                }
            }
        )

        val quotas = repository.getUsage().getOrThrow().quotas

        assertEquals("Auto Free Kilo Gateway 5h", quotas[0].label)
        assertEquals("Auto Free Kilo Gateway 7d", quotas[1].label)
        assertEquals("z-model:free 5h", quotas[2].label)
        assertEquals("z-model:free 7d", quotas[3].label)
    }
}
