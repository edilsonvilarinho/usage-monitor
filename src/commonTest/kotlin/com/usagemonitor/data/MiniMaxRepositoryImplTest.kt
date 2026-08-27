package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.BaseRespDto
import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import com.usagemonitor.data.dto.ModelRemainDto
import com.usagemonitor.data.repository.MiniMaxRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MiniMaxRepositoryImplTest {

    @Test
    fun `returns failure when api key missing`() = runTest {
        val repo = MiniMaxRepositoryImpl(
            apiDataSource = stubRemote(successResponse()),
            apiKeyReader = { null }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("Chave da API MiniMax não configurada"))
    }

    @Test
    fun `returns failure when API status_code is non-zero`() = runTest {
        val response = MiniMaxTokenPlanResponse(
            modelRemains = emptyList(),
            baseResp = BaseRespDto(statusCode = 1004, statusMsg = "invalid api key")
        )
        val repo = MiniMaxRepositoryImpl(
            apiDataSource = stubRemote(response),
            apiKeyReader = { "fake-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("invalid api key"))
        assertTrue(message.contains("1004"))
    }

    @Test
    fun `returns friendly failure when MiniMax has no active token plan`() = runTest {
        val response = MiniMaxTokenPlanResponse(
            modelRemains = null,
            baseResp = BaseRespDto(
                statusCode = 2062,
                statusMsg = "no active token plan subscription"
            )
        )
        val repo = MiniMaxRepositoryImpl(
            apiDataSource = stubRemote(response),
            apiKeyReader = { "fake-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message ?: ""
        assertTrue(message.contains("MiniMax sem plano/token ativo"))
    }

    @Test
    fun `returns success and maps response when status_code is 0`() = runTest {
        val repo = MiniMaxRepositoryImpl(
            apiDataSource = stubRemote(successResponse()),
            apiKeyReader = { "fake-key" }
        )

        val stats = repo.getUsage().getOrNull()

        assertNotNull(stats)
        assertEquals(ApiSource.MINIMAX, stats.source)
        assertTrue(stats.quotas.isNotEmpty())
    }

    private fun successResponse(): MiniMaxTokenPlanResponse {
        return MiniMaxTokenPlanResponse(
            modelRemains = listOf(
                ModelRemainDto(
                    startTime = 1_700_000_000_000L,
                    endTime = 1_700_003_600_000L,
                    remainsTime = 3_600_000L,
                    currentIntervalTotalCount = 100,
                    currentIntervalUsageCount = 25,
                    modelName = "MiniMax-M*",
                    currentWeeklyTotalCount = 1000,
                    currentWeeklyUsageCount = 200,
                    weeklyStartTime = 1_699_400_000_000L,
                    weeklyEndTime = 1_700_004_800_000L,
                    weeklyRemainsTime = 600_000_000L
                )
            ),
            baseResp = BaseRespDto(statusCode = 0, statusMsg = "success")
        )
    }

    private fun stubRemote(response: MiniMaxTokenPlanResponse): RemoteApiDataSource {
        return object : RemoteApiDataSource(noopHttpClient()) {
            override suspend fun fetchMiniMaxTokenPlan(apiKey: String): MiniMaxTokenPlanResponse = response
        }
    }

    private fun noopHttpClient(): HttpClient = HttpClient(MockEngine { respond("") })
}
