package com.usagemonitor.data

import com.usagemonitor.data.dto.CodexRateLimitDto
import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.data.mapper.CodexMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexMapperTest {

    @Test
    fun `maps standard five hour and weekly windows`() {
        val result = CodexMapper.toUsageStats(response(primary = window(8L, FIVE_HOURS), secondary = window(11L, SEVEN_DAYS)))

        assertEquals(ApiSource.CODEX, result.source)
        assertEquals(listOf("Codex 5h", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.WEEKLY), result.quotas.map { it.periodType })
        assertEquals(emptySet(), result.notices)
    }

    @Test
    fun `maps only five hour window`() {
        val result = CodexMapper.toUsageStats(response(primary = window(8L, FIVE_HOURS), secondary = null))

        assertEquals(listOf("Codex 5h"), result.quotas.map { it.label })
        assertEquals(emptySet(), result.notices)
    }

    @Test
    fun `maps only weekly window from the primary field`() {
        val result = CodexMapper.toUsageStats(response(primary = window(45L, SEVEN_DAYS), secondary = null))

        assertEquals(listOf("Codex 7d"), result.quotas.map { it.label })
        assertEquals(45L, result.quotas.single().used)
        assertEquals(PeriodType.WEEKLY, result.quotas.single().periodType)
    }

    @Test
    fun `maps monthly window`() {
        val result = CodexMapper.toUsageStats(response(primary = window(45L, THIRTY_DAYS), secondary = null))

        val quota = result.quotas.single()
        assertEquals("Codex mensal", quota.label)
        assertEquals(PeriodType.MONTHLY, quota.periodType)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(emptySet(), result.notices)
    }

    @Test
    fun `maps monthly secondary window alongside five hour`() {
        val result = CodexMapper.toUsageStats(response(primary = window(8L, FIVE_HOURS), secondary = window(45L, THIRTY_ONE_DAYS)))

        assertEquals(listOf("Codex 5h", "Codex mensal"), result.quotas.map { it.label })
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.MONTHLY), result.quotas.map { it.periodType })
    }

    @Test
    fun `keeps unknown window as reported and warns`() {
        val result = CodexMapper.toUsageStats(response(primary = window(12L, 10L * 60L), secondary = null))

        assertEquals(listOf("Codex atual"), result.quotas.map { it.label })
        assertEquals(PeriodType.REPORTED, result.quotas.single().periodType)
        assertEquals(setOf(ApiUsageNotice.SOURCE_UNSTABLE), result.notices)
    }

    @Test
    fun `deduplicates repeated period and warns`() {
        val result = CodexMapper.toUsageStats(response(primary = window(8L, FIVE_HOURS), secondary = window(12L, FIVE_HOURS)))

        assertEquals(listOf("Codex 5h"), result.quotas.map { it.label })
        assertEquals(8L, result.quotas.single().used)
        assertEquals(setOf(ApiUsageNotice.SOURCE_UNSTABLE), result.notices)
    }

    @Test
    fun `accepts response without any window without manufacturing quota`() {
        val result = CodexMapper.toUsageStats(response(primary = null, secondary = null))

        assertEquals(emptyList(), result.quotas)
        assertEquals(emptySet(), result.notices)
    }

    private fun response(
        primary: CodexUsageWindowDto?,
        secondary: CodexUsageWindowDto?
    ): CodexUsageResponse {
        return CodexUsageResponse(
            planType = "plus",
            rateLimit = CodexRateLimitDto(
                allowed = true,
                limitReached = false,
                primaryWindow = primary,
                secondaryWindow = secondary
            )
        )
    }

    private fun window(usedPercent: Long, limitWindowSeconds: Long): CodexUsageWindowDto {
        return CodexUsageWindowDto(
            usedPercent = usedPercent,
            limitWindowSeconds = limitWindowSeconds,
            resetAfterSeconds = limitWindowSeconds,
            resetAt = 1_777_398_377L
        )
    }

    private companion object {
        const val FIVE_HOURS = 18_000L
        const val SEVEN_DAYS = 604_800L
        const val THIRTY_DAYS = 30L * 24L * 60L * 60L
        const val THIRTY_ONE_DAYS = 31L * 24L * 60L * 60L
    }
}
