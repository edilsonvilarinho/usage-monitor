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
        val result = CodexMapper.mergeUsage(
            fiveHourQuota = CodexMapper.toFiveHourQuota(sampleResponse),
            weeklyQuota = CodexMapper.toWeeklyQuota(sampleWeeklyResponse())
        )
        assertEquals(ApiSource.CODEX, result.source)
        assertEquals("Codex", result.apiName)
        assertEquals(setOf(ApiUsageNotice.SOURCE_UNSTABLE), result.notices)
    }

    @Test
    fun `produces one quota for five hours and one for weekly`() {
        val result = CodexMapper.mergeUsage(
            fiveHourQuota = CodexMapper.toFiveHourQuota(sampleResponse),
            weeklyQuota = CodexMapper.toWeeklyQuota(sampleWeeklyResponse())
        )
        assertEquals(2, result.quotas.size)
    }

    @Test
    fun `maps primary window to reported percentage quota`() {
        val quota = CodexMapper.toFiveHourQuota(sampleResponse)
        assertEquals("Codex atual", quota.label)
        assertEquals(PeriodType.REPORTED, quota.periodType)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(8L, quota.used)
        assertEquals(100L, quota.total)
        assertEquals(1_777_398_377_000L, quota.periodEndAt.toEpochMilliseconds())
    }

    @Test
    fun `maps weekly response to weekly percentage quota`() {
        val quota = CodexMapper.toWeeklyQuota(sampleWeeklyResponse())
        assertEquals("Codex 7d", quota.label)
        assertEquals(PeriodType.WEEKLY, quota.periodType)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(1L, quota.used)
        assertEquals(100L, quota.total)
        assertEquals(1_777_985_177_000L, quota.periodEndAt.toEpochMilliseconds())
    }

    @Test
    fun `keeps only five hour quota and emits notice when weekly quota is absent`() {
        val result = CodexMapper.mergeUsage(
            fiveHourQuota = CodexMapper.toFiveHourQuota(sampleResponse),
            weeklyQuota = null
        )

        assertEquals(1, result.quotas.size)
        assertEquals("Codex atual", result.quotas.single().label)
        assertEquals(
            setOf(
                ApiUsageNotice.SOURCE_UNSTABLE,
                ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE
            ),
            result.notices
        )
    }

    private fun sampleWeeklyResponse(): CodexWeeklyUsageResponse {
        return CodexWeeklyUsageResponse(
            usedPercent = 1L,
            limitWindowSeconds = 604_800L,
            resetAfterSeconds = 604_088L,
            resetAt = 1_777_985_177L
        )
    }
}
