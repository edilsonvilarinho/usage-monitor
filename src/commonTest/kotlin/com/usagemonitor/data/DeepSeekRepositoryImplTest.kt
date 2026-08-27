package com.usagemonitor.data

import com.usagemonitor.data.datasource.CodexSession
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.DeepSeekBalanceInfoDto
import com.usagemonitor.data.dto.DeepSeekBalanceResponse
import com.usagemonitor.data.repository.DeepSeekRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeepSeekRepositoryImplTest {

    private val successResponse = DeepSeekBalanceResponse(
        isAvailable = true,
        balanceInfos = listOf(
            DeepSeekBalanceInfoDto("USD", "10.00", "0.00", "10.00")
        )
    )

    private fun fakeDataSource(response: DeepSeekBalanceResponse): RemoteApiDataSource {
        return object : RemoteApiDataSource(io.ktor.client.HttpClient()) {
            override suspend fun fetchDeepSeekBalance(apiKey: String) = response
        }
    }

    @Test
    fun `returns success when api key present and response ok`() = kotlinx.coroutines.test.runTest {
        val repo = DeepSeekRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isSuccess)
        assertEquals(ApiSource.DEEPSEEK, result.getOrThrow().source)
    }

    @Test
    fun `returns failure when api key missing`() = kotlinx.coroutines.test.runTest {
        val repo = DeepSeekRepositoryImpl(
            apiDataSource = fakeDataSource(successResponse),
            apiKeyReader = { null }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("Chave da API DeepSeek não configurada"))
    }

    @Test
    fun `returns failure when datasource throws`() = kotlinx.coroutines.test.runTest {
        val datasource = object : RemoteApiDataSource(io.ktor.client.HttpClient()) {
            override suspend fun fetchDeepSeekBalance(apiKey: String): DeepSeekBalanceResponse {
                throw IllegalStateException("DeepSeek HTTP 401: Unauthorized")
            }
        }

        val repo = DeepSeekRepositoryImpl(
            apiDataSource = datasource,
            apiKeyReader = { "test-key" }
        )

        val result = repo.getUsage()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }
}
