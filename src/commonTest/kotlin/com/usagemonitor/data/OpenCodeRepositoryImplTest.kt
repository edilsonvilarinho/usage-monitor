package com.usagemonitor.data

import com.usagemonitor.data.datasource.OpenCodeModelUsageSnapshot
import com.usagemonitor.data.datasource.OpenCodeUsageDataSource
import com.usagemonitor.data.repository.OpenCodeRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenCodeRepositoryImplTest {

    @Test
    fun `returns empty quotas when no free activity exists`() = runTest {
        val repository = OpenCodeRepositoryImpl(
            dataSource = object : OpenCodeUsageDataSource {
                override suspend fun loadFreeModelUsage(): List<OpenCodeModelUsageSnapshot> = emptyList()
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(ApiSource.OPENCODE, result.source)
        assertTrue(result.quotas.isEmpty())
    }

    @Test
    fun `maps each free model into 5h and 7d request windows`() = runTest {
        val repository = OpenCodeRepositoryImpl(
            dataSource = object : OpenCodeUsageDataSource {
                override suspend fun loadFreeModelUsage(): List<OpenCodeModelUsageSnapshot> {
                    return listOf(
                        OpenCodeModelUsageSnapshot(
                            modelId = "minimax-m2.5-free",
                            modelName = "MiniMax M2.5 Free",
                            requestsLastFiveHours = 4,
                            requestsLastSevenDays = 19,
                            capturedAt = Instant.parse("2026-05-07T15:00:00Z")
                        )
                    )
                }
            }
        )

        val quotas = repository.getUsage().getOrThrow().quotas

        assertEquals(2, quotas.size)
        assertEquals("MiniMax M2.5 Free 5h", quotas[0].label)
        assertEquals(PeriodType.INTERVAL, quotas[0].periodType)
        assertEquals(4L, quotas[0].used)
        assertEquals("MiniMax M2.5 Free 7d", quotas[1].label)
        assertEquals(PeriodType.WEEKLY, quotas[1].periodType)
        assertEquals(19L, quotas[1].used)
    }
}
