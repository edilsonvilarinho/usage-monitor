package com.usagemonitor.data

import com.usagemonitor.data.dto.CodexRateLimitDto
import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.data.dto.CodexWeeklyUsageResponse
import com.usagemonitor.data.mapper.CodexMapper
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexMapperTest {

    private val sampleResponse = CodexUsageResponse(
        planType = "plus",
        rateLimit = CodexRateLimitDto(
            allowed = true,
            limitReached = false,
            primaryWindow = CodexUsageWindowDto(
                usedPercent = 8L,
                limitWindowSeconds = 18_000L,
                resetAfterSeconds = 17_288L,
                resetAt = 1_777_398_377L
            ),
            secondaryWindow = CodexUsageWindowDto(
                usedPercent = 1L,
                limitWindowSeconds = 604_800L,
                resetAfterSeconds = 604_088L,
                resetAt = 1_777_985_177L
            )
        )
    )

    @Test
    fun `maps source and apiName to Codex`() {
        val result = CodexMapper.mergeStableUsage(
            intervalQuota = CodexMapper.toIntervalQuota(sampleResponse),
            weeklyQuota = CodexMapper.toWeeklyQuota(sampleResponse.rateLimit.secondaryWindow!!)
        )
        assertEquals(ApiSource.CODEX, result.source)
        assertEquals("Codex", result.apiName)
        assertEquals(emptySet(), result.notices)
    }

    @Test
    fun `produces one quota for five hours and one for weekly`() {
        val result = CodexMapper.mergeStableUsage(
            intervalQuota = CodexMapper.toIntervalQuota(sampleResponse),
            weeklyQuota = CodexMapper.toWeeklyQuota(sampleResponse.rateLimit.secondaryWindow!!)
        )
        assertEquals(2, result.quotas.size)
    }

    @Test
    fun `maps primary window to interval percentage quota`() {
        val quota = CodexMapper.toIntervalQuota(sampleResponse)
        assertEquals("Codex 5h", quota.label)
        assertEquals(PeriodType.INTERVAL, quota.periodType)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(8L, quota.used)
        assertEquals(100L, quota.total)
        assertEquals(1_777_398_377_000L, quota.periodEndAt.toEpochMilliseconds())
    }

    @Test
    fun `maps embedded weekly window to weekly percentage quota`() {
        val quota = CodexMapper.toWeeklyQuota(sampleResponse.rateLimit.secondaryWindow!!)
        assertEquals("Codex 7d", quota.label)
        assertEquals(PeriodType.WEEKLY, quota.periodType)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(1L, quota.used)
        assertEquals(100L, quota.total)
        assertEquals(1_777_985_177_000L, quota.periodEndAt.toEpochMilliseconds())
    }

    @Test
    fun `keeps both quota labels when weekly value comes from the fallback source`() {
        val result = CodexMapper.mergeDegradedUsage(
            intervalQuota = CodexMapper.toIntervalQuota(sampleResponse),
            weeklyQuota = CodexMapper.toWeeklyQuota(
                CodexWeeklyUsageResponse(
                    usedPercent = 12L,
                    limitWindowSeconds = 604_800L,
                    resetAfterSeconds = 604_088L,
                    resetAt = 1_777_985_177L
                )
            )
        )

        assertEquals(listOf("Codex 5h", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(setOf(ApiUsageNotice.SOURCE_UNSTABLE), result.notices)
    }
}
