package com.usagemonitor.data

import com.usagemonitor.data.datasource.AntigravityUsageDataSource
import com.usagemonitor.data.repository.AntigravityRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ReportedModelQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AntigravityRepositoryImplTest {
    @Test
    fun `keeps CLI metrics separate from percentage quotas`() = runTest {
        val expected = listOf(
            ReportedModelQuota(
                modelName = "Gemini 3.1 Pro",
                remaining = 400L,
                limit = 1_000L,
                unit = UsageUnit.TOKENS,
                resetDescription = "1h"
            )
        )
        val repository = AntigravityRepositoryImpl(object : AntigravityUsageDataSource {
            override suspend fun readUsage() = expected
        })

        val stats = repository.getUsage().getOrThrow()

        assertEquals(ApiSource.ANTIGRAVITY, stats.source)
        assertTrue(stats.quotas.isEmpty())
        assertEquals(expected, stats.reportedModelQuotas)
    }

    @Test
    fun `does not swallow coroutine cancellation`() = runTest {
        val repository = AntigravityRepositoryImpl(object : AntigravityUsageDataSource {
            override suspend fun readUsage(): List<ReportedModelQuota> = throw CancellationException("cancel")
        })

        assertFailsWith<CancellationException> { repository.getUsage() }
    }
}
