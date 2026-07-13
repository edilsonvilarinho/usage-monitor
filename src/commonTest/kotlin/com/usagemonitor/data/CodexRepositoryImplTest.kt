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
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexRepositoryImplTest {

    @Test
    fun `merges five hour and weekly quotas when both sources succeed`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
                    return sampleWeeklyResponse
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(2, result.quotas.size)
        assertEquals(listOf("Codex atual", "Codex 7d"), result.quotas.map { it.label })
        assertEquals(setOf(ApiUsageNotice.SOURCE_UNSTABLE), result.notices)
    }

    @Test
    fun `keeps five hour quota and emits notice when weekly source is unavailable`() = runTest {
        val repository = repositoryWith(
            dataSource = object : FakeCodexDataSource() {
                override suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
                    throw UnsupportedOperationException("weekly source unavailable")
                }
            }
        )

        val result = repository.getUsage().getOrThrow()

        assertEquals(1, result.quotas.size)
        assertEquals("Codex atual", result.quotas.single().label)
        assertEquals(
            setOf(
                ApiUsageNotice.SOURCE_UNSTABLE,
                ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE
            ),
            result.notices
        )
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

    private fun repositoryWith(dataSource: RemoteApiDataSource): CodexRepositoryImpl {
        return CodexRepositoryImpl(
            authDataSource = object : CodexAuthDataSource {
                override suspend fun loadSession(): CodexSession = CodexSession(
                    accessToken = "token",
                    capSid = "cap"
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

    private companion object {
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
