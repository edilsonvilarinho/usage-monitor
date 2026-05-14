package com.usagemonitor.data

import com.usagemonitor.data.datasource.CredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.AnthropicExtraUsage
import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnthropicRepositoryImplTest {

    @Test
    fun `retries once after scope requirement failure and succeeds with refreshed token`() = runTest {
        val credentialDataSource = FakeCredentialDataSource(tokens = listOf("stale-token", "fresh-token"))
        val repository = AnthropicRepositoryImpl(
            credentialDataSource = credentialDataSource,
            apiDataSource = object : RemoteApiDataSource(noopHttpClient()) {
                override suspend fun fetchAnthropicUsage(accessToken: String): AnthropicUsageResponse {
                    if (accessToken == "stale-token") {
                        throw IllegalStateException(scopeRequirementPayload())
                    }
                    return successResponse()
                }
            }
        )

        val result = repository.getUsage()
        val stats = result.getOrNull()

        assertNotNull(stats)
        assertEquals(1, credentialDataSource.invalidateCalls)
        assertEquals("Anthropic", stats.apiName)
        assertTrue(stats.quotas.isNotEmpty())
    }

    @Test
    fun `returns guided message when retry also fails after scope requirement failure`() = runTest {
        val credentialDataSource = FakeCredentialDataSource(tokens = listOf("stale-token", "fresh-token"))
        val repository = AnthropicRepositoryImpl(
            credentialDataSource = credentialDataSource,
            apiDataSource = object : RemoteApiDataSource(noopHttpClient()) {
                override suspend fun fetchAnthropicUsage(accessToken: String): AnthropicUsageResponse {
                    throw IllegalStateException(scopeRequirementPayload())
                }
            }
        )

        val result = repository.getUsage()

        assertTrue(result.isFailure)
        assertEquals(1, credentialDataSource.invalidateCalls)
        val message = result.exceptionOrNull()?.message ?: ""
        assertEquals(AnthropicRepositoryImpl.ANTHROPIC_REAUTH_GUIDANCE_MESSAGE, message)
        assertTrue(!message.contains("permission_error", ignoreCase = true))
    }

    private fun successResponse(): AnthropicUsageResponse {
        return AnthropicUsageResponse(
            fiveHour = AnthropicUsageWindow(
                utilization = 25.0,
                resetsAt = "2026-05-14T16:30:00Z"
            ),
            sevenDay = AnthropicUsageWindow(
                utilization = 10.0,
                resetsAt = "2026-05-19T04:00:00Z"
            ),
            extraUsage = AnthropicExtraUsage(isEnabled = false)
        )
    }

    private fun scopeRequirementPayload(): String {
        return "Anthropic HTTP 403: {\"type\":\"error\",\"error\":{\"type\":\"permission_error\",\"message\":\"OAuth token does not meet scope requirement user:profile\"}}"
    }

    private fun noopHttpClient(): HttpClient = HttpClient(MockEngine { respond("") })

    private class FakeCredentialDataSource(
        private val tokens: List<String>
    ) : CredentialDataSource {
        private var currentTokenIndex = 0
        var invalidateCalls = 0
            private set

        override suspend fun loadAnthropicAccessToken(): String {
            return tokens[currentTokenIndex]
        }

        override fun invalidateAnthropicAccessTokenCache() {
            invalidateCalls += 1
            if (currentTokenIndex < tokens.lastIndex) {
                currentTokenIndex += 1
            }
        }
    }
}
