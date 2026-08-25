package com.usagemonitor.data

import com.usagemonitor.data.mapper.toCacheDto
import com.usagemonitor.data.mapper.toDomain
import com.usagemonitor.data.dto.DashboardCacheDto
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
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
    fun `round trip preserves stable Codex windows and account context`() {
        val original = ApiUsageStats(
            source = ApiSource.CODEX,
            targetKey = UsageTargetKey.forSource(ApiSource.CODEX),
            apiName = "Codex",
            accountContext = UsageAccountContext(
                key = UsageAccountKey(
                    source = ApiSource.CODEX,
                    providerAccountId = "codex-user-a",
                    workspaceId = "codex-workspace-a"
                ),
                email = "codex@example.com",
                workspaceName = "Codex Workspace"
            ),
            quotas = listOf(
                QuotaInfo(
                    label = "Codex 5h",
                    used = 23L,
                    total = 100L,
                    periodEndAt = fixedInstant,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                ),
                QuotaInfo(
                    label = "Codex 7d",
                    used = 11L,
                    total = 100L,
                    periodEndAt = fixedInstant,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )

        val restored = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(original.toCacheDto())
        ).toDomain()

        assertEquals(listOf("Codex 5h", "Codex 7d"), restored.first().quotas.map { it.label })
        assertEquals(listOf(23L, 11L), restored.first().quotas.map { it.used })
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.WEEKLY), restored.first().quotas.map { it.periodType })
        assertEquals(original.accountContext, restored.first().accountContext)
        assertEquals(original, restored.first())
    }

    @Test
    fun `round trip preserves Codex reported fallback and notices`() {
        val original = ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = listOf(
                QuotaInfo(
                    label = "Codex atual",
                    used = 42L,
                    total = 100L,
                    periodEndAt = fixedInstant,
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.PERCENTAGE
                ),
                QuotaInfo(
                    label = "Codex 7d",
                    used = 17L,
                    total = 100L,
                    periodEndAt = fixedInstant,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE
                )
            ),
            notices = setOf(
                ApiUsageNotice.SOURCE_UNSTABLE,
                ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE
            )
        )

        val restored = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(original.toCacheDto())
        ).toDomain()

        assertEquals(original, restored.first())
    }

    @Test
    fun `round trip preserves the quota currency code`() {
        val original = ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "default"),
            apiName = "Anthropic",
            quotas = listOf(
                QuotaInfo(
                    label = AnthropicQuotaLabels.EXTRA_CREDITS,
                    used = 60L,
                    total = 100L,
                    periodEndAt = fixedInstant,
                    hasKnownResetAt = false,
                    periodType = PeriodType.REPORTED,
                    unit = UsageUnit.PERCENTAGE,
                    rawUsed = 32784L,
                    rawTotal = 55000L,
                    currencyCode = "BRL"
                )
            )
        )

        val restored = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(original.toCacheDto())
        ).toDomain()

        assertEquals(1, restored.size)
        assertEquals(original, restored.first())
        assertEquals("BRL", restored.first().quotas.first().currencyCode)
    }

    @Test
    fun `quotas cached before the currency code default to USD`() {
        val dto = DashboardCacheDto(
            savedAtEpochMillis = fixedInstant.toEpochMilliseconds(),
            entries = listOf(
                com.usagemonitor.data.dto.ApiUsageStatsCacheDto(
                    targetKey = "DEEPSEEK",
                    source = "DEEPSEEK",
                    apiName = "DeepSeek",
                    quotas = listOf(
                        com.usagemonitor.data.dto.QuotaInfoCacheDto(
                            label = "Saldo",
                            used = 0L,
                            total = 385L,
                            periodEndAtEpochMillis = fixedInstant.toEpochMilliseconds(),
                            periodType = "INTERVAL",
                            unit = "CURRENCY_USD"
                        )
                    )
                )
            )
        )

        assertEquals("USD", dto.toDomain().first().quotas.first().currencyCode)
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
