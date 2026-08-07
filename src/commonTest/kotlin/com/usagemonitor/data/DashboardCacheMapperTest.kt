package com.usagemonitor.data

import com.usagemonitor.data.mapper.toCacheDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.data.dto.DashboardCacheDto
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DashboardCacheMapperTest {

    private val fixedInstant = Instant.parse("2025-01-01T12:00:00Z")

    @Test
    fun `round trip preserves stats without account context`() {
        val original = ApiUsageStats(
            source = ApiSource.MINIMAX,
            apiName = "MiniMax",
            quotas = listOf(
                QuotaInfo(
                    label = "MiniMax-M*",
                    used = 2223L,
                    total = 4500L,
                    periodEndAt = fixedInstant,
                    unit = UsageUnit.REQUESTS,
                    rawUsed = 2223L,
                    rawTotal = 4500L
                )
            ),
            notices = setOf(ApiUsageNotice.SOURCE_UNSTABLE)
        )

        val restored = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(original.toCacheDto())
        ).toDomain()

        assertEquals(1, restored.size)
        assertEquals(original, restored.first())
    }

    @Test
    fun `round trip preserves stats with account context`() {
        val original = ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "default"),
            apiName = "Anthropic",
            accountContext = UsageAccountContext(
                key = UsageAccountKey(
                    source = ApiSource.ANTHROPIC,
                    providerAccountId = "anthropic-user-a",
                    workspaceId = "anthropic-org-a"
                ),
                email = "account-a@example.com",
                workspaceName = "Org A"
            ),
            profileLabel = "Trabalho",
            quotas = listOf(
                QuotaInfo(
                    label = "Tokens",
                    used = 50000L,
                    total = 200000L,
                    periodEndAt = fixedInstant,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE,
                    rawUsed = 50000L,
                    rawTotal = 200000L
                )
            )
        )

        val restored = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(original.toCacheDto())
        ).toDomain()

        assertEquals(1, restored.size)
        assertEquals(original, restored.first())
    }

    @Test
    fun `entries with unparseable enums are dropped instead of crashing`() {
        val dto = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(
                com.usagemonitor.data.dto.ApiUsageStatsCacheDto(
                    targetKey = "MINIMAX",
                    source = "MINIMAX",
                    apiName = "MiniMax",
                    quotas = listOf(
                        com.usagemonitor.data.dto.QuotaInfoCacheDto(
                            label = "Corrompido",
                            used = 1L,
                            total = 2L,
                            periodEndAtEpochMillis = fixedInstant.toEpochMilliseconds(),
                            periodType = "NAO_EXISTE",
                            unit = "REQUESTS"
                        )
                    )
                )
            )
        )

        assertTrue(dto.toDomain().isEmpty())
    }
}
