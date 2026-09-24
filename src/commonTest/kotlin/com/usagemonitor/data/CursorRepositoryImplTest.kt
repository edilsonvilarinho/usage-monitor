package com.usagemonitor.data

import com.usagemonitor.data.datasource.CursorSessionCredentials
import com.usagemonitor.data.datasource.CursorSessionDataSource
import com.usagemonitor.data.datasource.CursorUsageApiDataSource
import com.usagemonitor.data.datasource.CursorUsageApiException
import com.usagemonitor.data.datasource.CursorUsageApiFailureKind
import com.usagemonitor.data.repository.CursorRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CursorQuotaLabels
import com.usagemonitor.domain.repository.CursorUsageFailureKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CursorRepositoryImplTest {
    private val session = CursorSessionCredentials("user-id", "secret-session-token")
    private val response = Json.parseToJsonElement(
        """{"billingCycleEnd":"2026-10-01T00:00:00Z","individualUsage":{"plan":{"autoPercentUsed":8.5}}}"""
    )

    private fun repository(
        sessionSource: CursorSessionDataSource = FixedSessionDataSource { session },
        api: CursorUsageApiDataSource = FixedApiDataSource { response }
    ) = CursorRepositoryImpl(sessionSource, api)

    @Test
    fun `fetches usage using the current local session`() = runTest {
        var received: CursorSessionCredentials? = null
        val stats = repository(api = FixedApiDataSource { credentials -> received = credentials; response })
            .getUsage().getOrThrow()

        assertEquals(session, received)
        assertEquals(ApiSource.CURSOR, stats.source)
        assertEquals(CursorQuotaLabels.AUTO, stats.quotas.single().label)
        assertEquals(8L, stats.quotas.single().used)
    }

    /**
     * "Não instalado", "sem sessão" e "sessão recusada" pedem ações diferentes, e a
     * primeira versão devolvia a mesma frase genérica para os três.
     */
    @Test
    fun `setup failures keep their own kind and never carry the token`() = runTest {
        val notInstalled = repository(
            sessionSource = FixedSessionDataSource {
                throw com.usagemonitor.domain.repository.CursorUsageException(CursorUsageFailureKind.NOT_INSTALLED)
            }
        ).getUsage().exceptionOrNull()
        assertEquals(CursorUsageFailureKind.NOT_INSTALLED.safeMessage, notInstalled?.message)

        listOf(401, 403).forEach { status ->
            val rejected = repository(
                api = FixedApiDataSource {
                    throw CursorUsageApiException(status, CursorUsageApiFailureKind.AUTHENTICATION_REJECTED)
                }
            ).getUsage().exceptionOrNull()
            assertEquals(CursorUsageFailureKind.SESSION_REJECTED.safeMessage, rejected?.message)
            assertFalse(rejected?.message.orEmpty().contains(session.accessToken))
        }
    }

    @Test
    fun `rate limit and unavailability keep the status the screen markers look for`() = runTest {
        val limited = repository(
            api = FixedApiDataSource { throw CursorUsageApiException(429, CursorUsageApiFailureKind.HTTP_STATUS) }
        ).getUsage().exceptionOrNull()

        assertTrue(limited?.message.orEmpty().contains("HTTP 429"))
    }

    /** O tipo é o que `isConnectivityFailure` lê; embrulhar escondia o banner de rede. */
    @Test
    fun `network failures pass through unwrapped`() = runTest {
        val failure = repository(
            api = FixedApiDataSource { throw ConnectException("proxy unreachable") }
        ).getUsage().exceptionOrNull()

        assertIs<ConnectException>(failure)
    }

    @Test
    fun `an unknown response shape or non-JSON body is a failure, never a zero quota`() = runTest {
        val unknownShape = repository(
            api = FixedApiDataSource { Json.parseToJsonElement("""{"individualUsage":{"plan":{}}}""") }
        ).getUsage()
        assertEquals("Cursor usage response format is unrecognized", unknownShape.exceptionOrNull()?.message)

        val notJson = repository(
            api = FixedApiDataSource { throw CursorUsageApiException(200, CursorUsageApiFailureKind.INVALID_RESPONSE) }
        ).getUsage()
        assertEquals("Cursor usage response format is unrecognized", notJson.exceptionOrNull()?.message)
    }

    @Test
    fun `native errors do not leak their text`() = runTest {
        val failure = repository(
            sessionSource = FixedSessionDataSource { throw IllegalStateException("C:\\Users\\private\\state.vscdb") }
        ).getUsage().exceptionOrNull()

        assertEquals("Cursor local session usage is unavailable", failure?.message)
    }

    @Test
    fun `credentials never print the token`() {
        assertFalse(session.toString().contains(session.accessToken))
        assertTrue(session.toString().contains("[REDACTED]"))
    }

    private class FixedSessionDataSource(
        private val read: () -> CursorSessionCredentials
    ) : CursorSessionDataSource {
        override suspend fun readCredentials(): CursorSessionCredentials = read()
    }

    private class FixedApiDataSource(
        private val fetch: (CursorSessionCredentials) -> JsonElement
    ) : CursorUsageApiDataSource {
        override suspend fun fetchUsage(credentials: CursorSessionCredentials): JsonElement = fetch(credentials)
    }
}
