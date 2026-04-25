package com.usagemonitor.data

import com.usagemonitor.data.dto.BaseRespDto
import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import com.usagemonitor.data.dto.ModelRemainDto
import com.usagemonitor.data.mapper.MiniMaxMapper
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Testes do MiniMaxMapper com dados reais do endpoint /token_plan/remains.
 * Os valores usados refletem a resposta confirmada manualmente.
 */
class MiniMaxMapperTest {

    // Resposta real simplificada (apenas 2 modelos para testar mapeamento)
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
    fun `maps all model_remains entries`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals(2, result.quotas.size)
    }

    @Test
    fun `maps model_name to label`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        assertEquals("MiniMax-M*", result.quotas[0].label)
        assertEquals("image-01", result.quotas[1].label)
    }

    @Test
    fun `maps usage counts correctly`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        val miniMaxModel = result.quotas[0]

        assertEquals(0L, miniMaxModel.used)
        assertEquals(4500L, miniMaxModel.total)
        assertEquals(2223L, miniMaxModel.weeklyUsed)
        assertEquals(45000L, miniMaxModel.weeklyTotal)
    }

    @Test
    fun `unit is REQUESTS for all MiniMax quotas`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        result.quotas.forEach { quota ->
            assertEquals(UsageUnit.REQUESTS, quota.unit)
        }
    }

    @Test
    fun `epoch milliseconds are converted to Instant correctly`() {
        val result = MiniMaxMapper.toUsageStats(sampleResponse)
        // 1777093200000 ms = 2026-04-23T05:00:00Z (verifica conversão)
        val periodEnd = result.quotas[0].periodEndAt
        assertEquals(1777093200000L, periodEnd.toEpochMilliseconds())
    }
}
