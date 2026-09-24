package com.usagemonitor.data

import com.usagemonitor.data.datasource.CursorSessionCredentials
import com.usagemonitor.data.datasource.CursorSessionDataSource
import com.usagemonitor.data.datasource.CursorUsageApiDataSource
import com.usagemonitor.data.datasource.CursorUsageApiException
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind
import com.usagemonitor.data.repository.CursorRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CursorRepositoryImplTest {
    private val capturedAt = Instant.parse("2026-09-23T12:00:00Z")
    private val session = CursorSessionCredentials("user-id", "secret-session-token")
    private val response = Json.parseToJsonElement(
        """{"billingCycleEnd":"2026-10-01T00:00:00Z","individualUsage":{"plan":{"totalPercentUsed":8.5}}}"""
    )

    @Test
    fun `fetches usage using the current local session`() = runTest {
        var received: CursorSessionCredentials? = null
        val repository = CursorRepositoryImpl(
            sessionDataSource = FixedSessionDataSource(session),
            usageApiDataSource = object : CursorUsageApiDataSource {
                override suspend fun fetchUsage(credentials: CursorSessionCredentials): JsonElement {
                    received = credentials
                    return response
                }
            },
            nowProvider = { capturedAt }
        )

        val stats = repository.getUsage().getOrThrow()

        assertEquals(session, received)
        assertEquals(ApiSource.CURSOR, stats.source)
        assertEquals(9L, stats.quotas.single().used)
    }

    @Test
    fun `missing credentials and auth rejection are failures and never include the token`() = runTest {
        val missing = CursorRepositoryImpl(
            sessionDataSource = FixedSessionDataSource(null),
            usageApiDataSource = FixedApiDataSource(response),
            nowProvider = { capturedAt }
        ).getUsage()
        val rejected = CursorRepositoryImpl(
            sessionDataSource = FixedSessionDataSource(session),
            usageApiDataSource = object : CursorUsageApiDataSource {
                override suspend fun fetchUsage(credentials: CursorSessionCredentials): JsonElement {
                    throw CursorUsageApiException(401, CursorUsageApiFailureKind.AUTHENTICATION_REJECTED)
                }
            },
            nowProvider = { capturedAt }
        ).getUsage()

        assertTrue(missing.isFailure)
        assertEquals("Cursor local session usage is unavailable", missing.exceptionOrNull()?.message)
        assertTrue(rejected.isFailure)
        assertEquals("Cursor rejected the local session (HTTP 401)", rejected.exceptionOrNull()?.message)
        assertFalse(rejected.exceptionOrNull()?.message.orEmpty().contains(session.accessToken))
    }

    @Test
    fun `unknown response shape is unavailable and not a zero quota`() = runTest {
        val repository = CursorRepositoryImpl(
            sessionDataSource = FixedSessionDataSource(session),
            usageApiDataSource = FixedApiDataSource(Json.parseToJsonElement("""{"individualUsage":{"plan":{}}}""")),
            nowProvider = { capturedAt }
        )

        val result = repository.getUsage()

        assertTrue(result.isFailure)
        assertEquals("Cursor local session usage is unavailable", result.exceptionOrNull()?.message)
    }

    private class FixedSessionDataSource(
        private val credentials: CursorSessionCredentials?
    ) : CursorSessionDataSource {
        override suspend fun readCredentials(): CursorSessionCredentials? = credentials
    }

    private class FixedApiDataSource(
        private val response: JsonElement
    ) : CursorUsageApiDataSource {
        override suspend fun fetchUsage(credentials: CursorSessionCredentials): JsonElement = response
    }
}
