package com.usagemonitor.data

import com.usagemonitor.data.datasource.CodexAuthDataSource
import com.usagemonitor.data.datasource.CodexSession
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.dto.CodexRateLimitDto
import com.usagemonitor.data.dto.CodexRolloutRateLimitWindowDto
import com.usagemonitor.data.dto.CodexRolloutRateLimitsDto
import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.data.parser.CodexRolloutRateLimit
import com.usagemonitor.data.repository.CodexProfileSources
import com.usagemonitor.data.repository.CodexRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import io.ktor.client.HttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant

class CodexRepositoryImplTest {

    @Test
    fun `uses all recognized windows from the same payload`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    return sampleStableResponse
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(listOf("Codex 5h", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.WEEKLY), result.quotas.map { it.periodType })
        assertEquals("codex@example.com", result.accountContext?.email)
    }

    @Test
    fun `does not call the unimplemented weekly source for a partial payload`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    return sampleMonthlyResponse
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(listOf("Codex mensal"), result.quotas.map { it.label })
    }

    @Test
    fun `accepts only weekly window from primary field`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    return response(window(45L, SEVEN_DAYS), null)
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(listOf("Codex 7d"), result.quotas.map { it.label })
        assertEquals(PeriodType.WEEKLY, result.quotas.single().periodType)
    }

    @Test
    fun `returns failure only when payload has no usable window`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
                    return response(null, null)
                }
            }
        )

        val result = repository.getUsage()

        assertTrue(result.isFailure)
        assertEquals("A resposta do Codex não trouxe nenhuma janela utilizável.", result.exceptionOrNull()?.message)
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
                override suspend fun loadSession(): CodexSession = session(
                    token = "token",
                    userId = "user-a",
                    workspaceId = "workspace-a",
                    email = "codex@example.com"
                )
            },
            apiDataSource = dataSource
        )
    }

    private open class FakeCodexDataSource : RemoteApiDataSource(HttpClient()) {
        override suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
            return sampleStableResponse
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
        const val FIVE_HOURS = 18_000L
        const val SEVEN_DAYS = 604_800L
        const val THIRTY_DAYS = 30L * 24L * 60L * 60L

        val sampleStableResponse = response(window(8L, FIVE_HOURS), window(11L, SEVEN_DAYS))
        val sampleMonthlyResponse = response(window(45L, THIRTY_DAYS), null)

        fun response(
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

        fun window(usedPercent: Long, limitWindowSeconds: Long): CodexUsageWindowDto {
            return CodexUsageWindowDto(
                usedPercent = usedPercent,
                limitWindowSeconds = limitWindowSeconds,
                resetAfterSeconds = limitWindowSeconds,
                resetAt = 1_777_398_377L
            )
        }
    }

    @Test
    fun `rollout model limits are appended after the live windows`() = runTest {
        val repository = CodexRepositoryImpl(
            authDataSource = object : CodexAuthDataSource {
                override suspend fun loadSession(): CodexSession = session("token", "user-a", "workspace-a", "codex@example.com")
            },
            apiDataSource = FakeCodexDataSource(),
            rolloutRateLimits = {
                listOf(
                    CodexRolloutRateLimit(
                        observedAt = Instant.fromEpochSeconds(1_789_999_000L),
                        limits = CodexRolloutRateLimitsDto(
                            limitId = "spark",
                            limitName = "GPT-5.3-Codex-Spark",
                            secondary = CodexRolloutRateLimitWindowDto(usedPercent = 100.0, windowMinutes = 10_080L, resetsAt = 1_791_066_495L),
                            planType = "plus"
                        )
                    )
                )
            },
            clock = object : Clock { override fun now(): Instant = Instant.fromEpochSeconds(1_790_000_000L) }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(listOf("Codex 5h", "Codex 7d", "Codex limit GPT-5.3-Codex-Spark (7d)"), result.quotas.map { it.label })
        assertEquals(100L, result.quotas.last().used)
    }

    @Test
    fun `a failing rollout read keeps the live reading`() = runTest {
        val repository = CodexRepositoryImpl(
            authDataSource = object : CodexAuthDataSource {
                override suspend fun loadSession(): CodexSession = session("token", "user-a", "workspace-a", "codex@example.com")
            },
            apiDataSource = FakeCodexDataSource(),
            rolloutRateLimits = { error("rollout ilegível") }
        )

        assertEquals(listOf("Codex 5h", "Codex 7d"), repository.getUsage().getOrThrow().quotas.map { it.label })
    }

    @Test
    fun `an extra account reads its own directory and carries its target and label`() = runTest {
        val workAuth = object : CodexAuthDataSource {
            override suspend fun loadSession(): CodexSession = session("token-w", "user-w", "workspace-w", "work@example.com")
        }
        val repository = CodexRepositoryImpl(
            authDataSource = object : CodexAuthDataSource {
                override suspend fun loadSession(): CodexSession = error("A conta padrão não pode ser lida aqui")
            },
            apiDataSource = FakeCodexDataSource(),
            profileSources = { profile -> if (profile.id == "codex-work") CodexProfileSources(workAuth) else null }
        )

        val stats = repository.getUsage(CodexProfileRef("codex-work", "trabalho")).getOrThrow()

        assertEquals(UsageTargetKey(ApiSource.CODEX, "codex-work"), stats.targetKey)
        assertEquals("trabalho", stats.profileLabel)
        assertEquals("work@example.com", stats.accountContext?.email)
    }

    @Test
    fun `a profile no longer in the registry fails instead of reading the default account`() = runTest {
        val repository = repositoryWith(FakeCodexDataSource())

        val result = repository.getUsage(CodexProfileRef("codex-gone", "antiga"))

        assertTrue(result.isFailure)
    }
}
