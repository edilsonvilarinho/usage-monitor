package com.usagemonitor.data

import com.usagemonitor.data.datasource.CredentialDataSource
import com.usagemonitor.data.datasource.AnthropicSession
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.AnthropicExtraUsage
import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.data.repository.AnthropicRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
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
        assertEquals(2, credentialDataSource.loadCalls)
        assertEquals("Anthropic", stats.apiName)
        assertEquals("account-a", stats.accountContext?.key?.providerAccountId)
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
        assertEquals(2, credentialDataSource.loadCalls)
        val message = result.exceptionOrNull()?.message ?: ""
        assertEquals(AnthropicRepositoryImpl.ANTHROPIC_REAUTH_GUIDANCE_MESSAGE, message)
        assertTrue(!message.contains("permission_error", ignoreCase = true))
    }

    @Test
    fun `retries with new account when credentials change during fetch`() = runTest {
        val sessions = listOf(
            session("token-a", "account-a", "a@example.com"),
            session("token-b", "account-b", "b@example.com")
        )
        var loadIndex = 0
        var apiCalls = 0
        val credentialDataSource = object : CredentialDataSource {
            override suspend fun loadAnthropicSession(): AnthropicSession {
                val session = sessions[loadIndex]
                if (loadIndex < sessions.lastIndex) {
                    loadIndex += 1
                }
                return session
            }

            override suspend fun isAnthropicSessionCurrent(session: AnthropicSession): Boolean {
                return session.accountContext.key.providerAccountId == "account-b"
            }
        }
        val repository = AnthropicRepositoryImpl(
            credentialDataSource = credentialDataSource,
            apiDataSource = object : RemoteApiDataSource(noopHttpClient()) {
                override suspend fun fetchAnthropicUsage(accessToken: String): AnthropicUsageResponse {
                    apiCalls += 1
                    return successResponse()
                }
            }
        )

        val stats = repository.getUsage().getOrThrow()

        assertEquals(2, apiCalls)
        assertEquals("account-b", stats.accountContext?.key?.providerAccountId)
        assertEquals("b@example.com", stats.accountContext?.email)
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

    private fun session(token: String, accountId: String, email: String): AnthropicSession {
        return AnthropicSession(
            accessToken = token,
            accountContext = UsageAccountContext(
                key = UsageAccountKey(
                    source = ApiSource.ANTHROPIC,
                    providerAccountId = accountId,
                    workspaceId = "org"
                ),
                email = email,
                workspaceName = "Org"
            )
        )
    }

    private class FakeCredentialDataSource(
        private val tokens: List<String>
    ) : CredentialDataSource {
        private var currentTokenIndex = 0
        var loadCalls = 0
            private set

        override suspend fun loadAnthropicSession(): AnthropicSession {
            val token = tokens[currentTokenIndex]
            if (currentTokenIndex < tokens.lastIndex) {
                currentTokenIndex += 1
            }
            loadCalls += 1
            return AnthropicSession(
                accessToken = token,
                accountContext = UsageAccountContext(
                    key = UsageAccountKey(
                        source = ApiSource.ANTHROPIC,
                        providerAccountId = "account-a",
                        workspaceId = "org-a"
                    ),
                    email = "a@example.com",
                    workspaceName = "Org A"
                )
            )
        }

        override suspend fun isAnthropicSessionCurrent(session: AnthropicSession): Boolean = true
    }
}
