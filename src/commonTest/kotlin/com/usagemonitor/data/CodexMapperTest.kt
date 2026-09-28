package com.usagemonitor.data

import com.usagemonitor.data.dto.CodexRateLimitDto
import com.usagemonitor.data.dto.CodexRolloutRateLimitWindowDto
import com.usagemonitor.data.dto.CodexRolloutRateLimitsDto
import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.data.mapper.CodexMapper
import com.usagemonitor.data.parser.CodexRolloutRateLimit
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class CodexMapperTest {

    @Test
    fun `maps standard five hour and weekly windows`() {
        val result = CodexMapper.toUsageStats(response(primary = window(8L, FIVE_HOURS), secondary = window(11L, SEVEN_DAYS)))

        assertEquals(ApiSource.CODEX, result.source)
        assertEquals(listOf("Codex 5h", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.WEEKLY), result.quotas.map { it.periodType })
        assertEquals(emptySet(), result.notices)
        // O `plan_type` da própria resposta vira o plano do card.
        assertEquals("ChatGPT Plus", result.planLabel)
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

    @Test
    fun `window start is the reset minus limit_window_seconds`() {
        val quota = CodexMapper.toUsageStats(response(window(10L, FIVE_HOURS), null)).quotas.single()

        assertEquals(Instant.fromEpochSeconds(1_777_398_377L - FIVE_HOURS), quota.periodStartAt)
    }

    @Test
    fun `model limits from the rollout become extra quotas`() {
        val now = Instant.fromEpochSeconds(1_790_000_000L)
        val spark = rolloutLimit("spark", "GPT-5.3-Codex-Spark", plan = "prolite", secondaryUsed = 99.7)
        val account = rolloutLimit("codex", null, plan = "prolite", secondaryUsed = 5.0)

        val quotas = CodexMapper.modelLimitQuotas(listOf(account, spark), livePlanType = "prolite", now = now)

        assertEquals(
            listOf("Codex limit GPT-5.3-Codex-Spark (5h)", "Codex limit GPT-5.3-Codex-Spark (7d)"),
            quotas.map { it.label }
        )
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.WEEKLY), quotas.map { it.periodType })
        assertEquals(99L, quotas.last().used, "Truncado: 99,7% não é esgotado")
        assertEquals(Instant.fromEpochSeconds(1_791_066_495L - 604_800L), quotas.last().periodStartAt)
    }

    @Test
    fun `model limits of another plan or already reset are dropped`() {
        val limits = listOf(rolloutLimit("spark", "Spark", plan = "pro", secondaryUsed = 100.0))

        assertTrue(CodexMapper.modelLimitQuotas(limits, livePlanType = "plus", now = Instant.fromEpochSeconds(1_790_000_000L)).isEmpty())
        assertTrue(
            CodexMapper.modelLimitQuotas(limits, livePlanType = "pro", now = Instant.fromEpochSeconds(1_800_000_000L)).isEmpty(),
            "Janelas vencidas"
        )
    }

    private fun rolloutLimit(id: String, name: String?, plan: String, secondaryUsed: Double): CodexRolloutRateLimit {
        return CodexRolloutRateLimit(
            observedAt = Instant.fromEpochSeconds(1_789_999_000L),
            limits = CodexRolloutRateLimitsDto(
                limitId = id,
                limitName = name,
                primary = CodexRolloutRateLimitWindowDto(usedPercent = 0.0, windowMinutes = 300L, resetsAt = 1_790_567_009L),
                secondary = CodexRolloutRateLimitWindowDto(usedPercent = secondaryUsed, windowMinutes = 10_080L, resetsAt = 1_791_066_495L),
                planType = plan
            )
        )
    }
}
