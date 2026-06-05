package com.usagemonitor.data

import com.usagemonitor.data.dto.BaseRespDto
import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import com.usagemonitor.data.dto.ModelRemainDto
import com.usagemonitor.data.mapper.MiniMaxMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes do MiniMaxMapper com dados reais do endpoint /token_plan/remains.
 * Os valores usados refletem a resposta confirmada manualmente.
 */
class MiniMaxMapperTest {

    private val sampleResponse = MiniMaxTokenPlanResponse(
        modelRemains = listOf(
            ModelRemainDto(
                startTime = 1777075200000L,
                endTime = 1777093200000L,
                remainsTime = 10320279L,
                currentIntervalTotalCount = 4500L,
                currentIntervalUsageCount = 0L,
                modelName = "MiniMax-M*",
                currentWeeklyTotalCount = 45000L,
                currentWeeklyUsageCount = 2223L,
                weeklyStartTime = 1776643200000L,
                weeklyEndTime = 1777248000000L,
                weeklyRemainsTime = 165120279L
            ),
            ModelRemainDto(
                startTime = 1777075200000L,
                endTime = 1777161600000L,
                remainsTime = 78720279L,
                currentIntervalTotalCount = 50L,
                currentIntervalUsageCount = 0L,
                modelName = "image-01",
                currentWeeklyTotalCount = 350L,
                currentWeeklyUsageCount = 0L,
                weeklyStartTime = 1776643200000L,
                weeklyEndTime = 1777248000000L,
                weeklyRemainsTime = 165120279L
            )
        ),
        baseResp = BaseRespDto(statusCode = 0, statusMsg = "success")
    )

    @Test
    fun `maps apiName to MiniMax`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals("MiniMax", result.apiName)
    }

    @Test
    fun `maps source to MiniMax`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals(ApiSource.MINIMAX, result.source)
    }

    @Test
    fun `emits only MiniMax-M star quotas`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals(2, result.quotas.size)
        result.quotas.forEach { quota ->
            assertEquals("MiniMax-M*", quota.label)
        }
    }

    @Test
    fun `interval quota comes before weekly quota`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals(PeriodType.INTERVAL, result.quotas[0].periodType)
        assertEquals(PeriodType.WEEKLY, result.quotas[1].periodType)
    }

    @Test
    fun `maps model_name to label for both period types`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals("MiniMax-M*", result.quotas[0].label)
        assertEquals("MiniMax-M*", result.quotas[1].label)
    }

    @Test
    fun `maps interval usage counts correctly`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        val intervalQuota = result.quotas[0]
        assertEquals(0L, intervalQuota.used)
        assertEquals(4500L, intervalQuota.total)
    }

    @Test
    fun `maps weekly usage counts correctly`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        val weeklyQuota = result.quotas[1]
        assertEquals(2223L, weeklyQuota.used)
        assertEquals(45000L, weeklyQuota.total)
    }

    @Test
    fun `unit is REQUESTS for all MiniMax quotas`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        result.quotas.forEach { quota ->
            assertEquals(UsageUnit.REQUESTS, quota.unit)
        }
    }

    @Test
    fun `epoch milliseconds are converted to Instant correctly for interval`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        val periodEnd = result.quotas[0].periodEndAt
        assertEquals(1777093200000L, periodEnd.toEpochMilliseconds())
    }

    @Test
    fun `epoch milliseconds are converted to Instant correctly for weekly`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        val weeklyEnd = result.quotas[1].periodEndAt
        assertEquals(1777248000000L, weeklyEnd.toEpochMilliseconds())
    }

    @Test
    fun `non-M-star models are excluded`() {
        val responseWithOtherModels = MiniMaxTokenPlanResponse(
            modelRemains = listOf(
                ModelRemainDto(
                    startTime = 1777075200000L,
                    endTime = 1777093200000L,
                    remainsTime = 10320279L,
                    currentIntervalTotalCount = 4500L,
                    currentIntervalUsageCount = 0L,
                    modelName = "MiniMax-M*",
                    currentWeeklyTotalCount = 45000L,
                    currentWeeklyUsageCount = 2223L,
                    weeklyStartTime = 1776643200000L,
                    weeklyEndTime = 1777248000000L,
                    weeklyRemainsTime = 165120279L
                ),
                ModelRemainDto(
                    startTime = 1777075200000L,
                    endTime = 1777161600000L,
                    remainsTime = 78720279L,
                    currentIntervalTotalCount = 50L,
                    currentIntervalUsageCount = 0L,
                    modelName = "speech-hd",
                    currentWeeklyTotalCount = 0L,
                    currentWeeklyUsageCount = 0L,
                    weeklyStartTime = 0L,
                    weeklyEndTime = 0L,
                    weeklyRemainsTime = 0L
                )
            ),
            baseResp = BaseRespDto(statusCode = 0, statusMsg = "success")
        )
        val result = MiniMaxMapper.toUsageStats(responseWithOtherModels)
        assertEquals(2, result.quotas.size)
        assertEquals("MiniMax-M*", result.quotas[0].label)
        assertEquals("MiniMax-M*", result.quotas[1].label)
    }
}
