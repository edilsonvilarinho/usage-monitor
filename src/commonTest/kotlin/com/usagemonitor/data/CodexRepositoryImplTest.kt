package com.usagemonitor.data

import com.usagemonitor.data.datasource.CodexAuthDataSource
import com.usagemonitor.data.datasource.CodexSession
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.CodexRateLimitDto
import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.data.dto.CodexWeeklyUsageResponse
import com.usagemonitor.data.repository.CodexRepositoryImpl
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexRepositoryImplTest {

    @Test
    fun `uses five hour and weekly windows from the same payload when secondary window is present`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
                    throw IllegalStateException("weekly source should not be called")
                }

                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    return sampleStableResponse
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(2, result.quotas.size)
        assertEquals("codex@example.com", result.accountContext?.email)
        assertEquals(listOf("Codex 5h", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(emptySet(), result.notices)
    }

    @Test
    fun `fails closed when weekly quota is unavailable`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
                    throw UnsupportedOperationException("weekly source unavailable")
                }
            }
        )

        val result = repository.getUsage()

        assertTrue(result.isFailure)
        assertEquals("weekly source unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `keeps both quota labels when primary payload lacks secondary window`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
                    return sampleWeeklyResponse
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(listOf("Codex 5h", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(setOf(ApiUsageNotice.SOURCE_UNSTABLE), result.notices)
    }

    @Test
    fun `fails when five hour source fails even if weekly source would succeed`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    throw IllegalStateException("five hour source failed")
                }

                override suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
                    return sampleWeeklyResponse
                }
            }
        )

        val result = repository.getUsage()

        assertTrue(result.isFailure)
        assertEquals("five hour source failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `retries with new account when Codex credentials change during fetch`() = runTest {
        val sessions = listOf(
            session("token-a", "user-a", "workspace-a", "a@example.com"),
            session("token-b", "user-b", "workspace-b", "b@example.com")
        )
        var sessionIndex = 0
        var apiCalls = 0
        val repository = CodexRepositoryImpl(
            authDataSource = object : CodexAuthDataSource {
                override suspend fun loadSession(): CodexSession {
                    val session = sessions[sessionIndex]
                    if (sessionIndex < sessions.lastIndex) {
                        sessionIndex += 1
                    }
                    return session
                }

                override suspend fun isSessionCurrent(session: CodexSession): Boolean {
                    return session.accountContext.key.providerAccountId == "user-b"
                }
            },
            apiDataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    apiCalls += 1
                    return sampleStableResponse
                }
            }
        )

        val stats = repository.getUsage().getOrThrow()

        assertEquals(2, apiCalls)
        assertEquals("user-b", stats.accountContext?.key?.providerAccountId)
        assertEquals("b@example.com", stats.accountContext?.email)
    }

    private fun repositoryWith(dataSource: RemoteApiDataSource): CodexRepositoryImpl {
        return CodexRepositoryImpl(
            authDataSource = object : CodexAuthDataSource {
                override suspend fun loadSession(): CodexSession = CodexSession(
                    accessToken = "token",
                    capSid = "cap",
                    accountContext = UsageAccountContext(
                        key = UsageAccountKey(
                            source = ApiSource.CODEX,
                            providerAccountId = "user-a",
                            workspaceId = "workspace-a"
                        ),
                        email = "codex@example.com"
                    )
                )
            },
            apiDataSource = dataSource
        )
    }

    private open class FakeCodexDataSource : RemoteApiDataSource(HttpClient()) {
        override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
            return sampleFiveHourResponse
        }
    }

    private fun session(
        token: String,
        userId: String,
        workspaceId: String,
        email: String
    ): CodexSession {
        return CodexSession(
            accessToken = token,
            capSid = "cap-$workspaceId",
            accountContext = UsageAccountContext(
                key = UsageAccountKey(
                    source = ApiSource.CODEX,
                    providerAccountId = userId,
                    workspaceId = workspaceId
                ),
                email = email
            )
        )
    }

    private companion object {
        val sampleStableResponse = CodexUsageResponse(
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
                    usedPercent = 11L,
                    limitWindowSeconds = 604_800L,
                    resetAfterSeconds = 604_088L,
                    resetAt = 1_777_985_177L
                )
            )
        )

        val sampleFiveHourResponse = CodexUsageResponse(
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
                secondaryWindow = null
            )
        )

        val sampleWeeklyResponse = CodexWeeklyUsageResponse(
            usedPercent = 11L,
            limitWindowSeconds = 604_800L,
            resetAfterSeconds = 604_088L,
            resetAt = 1_777_985_177L
        )
    }
}
