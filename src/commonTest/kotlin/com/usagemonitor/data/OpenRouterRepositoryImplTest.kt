package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.OpenRouterCreditsDto
import com.usagemonitor.data.dto.OpenRouterCreditsResponse
import com.usagemonitor.data.repository.OpenRouterRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenRouterRepositoryImplTest {

    private val successResponse = OpenRouterCreditsResponse(
        data = OpenRouterCreditsDto(totalCredits = 5.0, totalUsage = 0.0)
    )

    private fun fakeDataSource(response: OpenRouterCreditsResponse): RemoteApiDataSource {
        return object : RemoteApiDataSource(io.ktor.client.HttpClient()) {
            override suspend fun fetchOpenRouterCredits(apiKey: String) = response
        }
    }

    @Test
    fun `returns success when api key present and response ok`() = kotlinx.coroutines.test.runTest {
        val repo = OpenRouterRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isSuccess)
        assertEquals(ApiSource.OPENROUTER, result.getOrThrow().source)
    }

    @Test
    fun `returns failure when api key missing`() = kotlinx.coroutines.test.runTest {
        val repo = OpenRouterRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { null }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Chave da API OpenRouter não configurada"))
    }

    @Test
    fun `returns failure when api key is blank`() = kotlinx.coroutines.test.runTest {
        val repo = OpenRouterRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { "   " }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
    }

    @Test
    fun `returns failure when datasource throws`() = kotlinx.coroutines.test.runTest {
        val datasource = object : RemoteApiDataSource(io.ktor.client.HttpClient()) {
            override suspend fun fetchOpenRouterCredits(apiKey: String): OpenRouterCreditsResponse {
                throw IllegalStateException("OpenRouter HTTP 401: Unauthorized")
            }
        }

        val repo = OpenRouterRepositoryImpl(
            apiDataSource = datasource,
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }
}
