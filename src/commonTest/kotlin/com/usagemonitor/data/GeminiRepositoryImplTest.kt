package com.usagemonitor.data

import com.usagemonitor.data.datasource.GeminiMessageUsage
import com.usagemonitor.data.datasource.GeminiSessionUsage
import com.usagemonitor.data.datasource.GeminiUsageDataSource
import com.usagemonitor.data.repository.GeminiRepositoryImpl
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.GeminiUsageException
import com.usagemonitor.domain.repository.GeminiUsageFailureKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiRepositoryImplTest {
    private val now = Instant.parse("2026-09-23T12:00:00Z")

    @Test
    fun `keeps observed tokens per model separate from account quotas`() = runTest {
        val repository = GeminiRepositoryImpl(
            dataSource = FixedGeminiDataSource(
                listOf(
                    session("session-one", message("duplicate", "gemini-2.5-pro", 100L, "2026-09-23T11:00:00Z"), message("old", "gemini-2.5-pro", 900L, "2026-09-10T11:00:00Z")),
                    session("session-one", message("duplicate", "gemini-2.5-pro", 150L, "2026-09-23T11:30:00Z")),
                    session("session-two", message("second", "gemini-2.5-flash", 50L, "2026-09-23T06:00:00Z"))
                )
            ),
            nowProvider = { now }
        )

        val stats = repository.getUsage().getOrThrow()
        val quotasByLabel = stats.quotas.associateBy { quota -> quota.label }

        assertEquals(ApiSource.GEMINI, stats.source)
        assertEquals(150L, quotasByLabel.getValue("gemini-2.5-pro 5h").used)
        assertEquals(150L, quotasByLabel.getValue("gemini-2.5-pro 7d").used)
        assertEquals(0L, quotasByLabel.getValue("gemini-2.5-flash 5h").used)
        assertEquals(50L, quotasByLabel.getValue("gemini-2.5-flash 7d").used)
        // Nenhuma linha que finja ser modelo: só tokens, por modelo, sem teto.
        assertEquals(4, stats.quotas.size)
        assertTrue(stats.quotas.all { quota -> quota.unit == UsageUnit.TOKENS && quota.total == 0L })
        assertFalse(stats.quotas.any { quota -> quota.unit == UsageUnit.PERCENTAGE })
        assertFalse(stats.quotas.any { quota -> quota.unit == UsageUnit.CURRENCY_USD })
    }

    @Test
    fun `source errors return a sanitized unavailable state instead of zero usage`() = runTest {
        val repository = GeminiRepositoryImpl(
            dataSource = object : GeminiUsageDataSource {
                override suspend fun loadSessions(): List<GeminiSessionUsage> {
                    error("C:\\Users\\private\\.gemini\\token-content.jsonl")
                }
            },
            nowProvider = { now }
        )

        val result = repository.getUsage()

        assertTrue(result.isFailure)
        assertEquals("Gemini CLI local usage is unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun knownLocalSourceFailurePreservesOnlyItsSafeReason() = runTest {
        val repository = GeminiRepositoryImpl(
            dataSource = object : GeminiUsageDataSource {
                override suspend fun loadSessions(): List<GeminiSessionUsage> {
                    throw GeminiUsageException(GeminiUsageFailureKind.SESSION_HISTORY_UNREADABLE)
                }
            },
            nowProvider = { now }
        )

        val failure = repository.getUsage().exceptionOrNull()

        assertEquals(
            GeminiUsageFailureKind.SESSION_HISTORY_UNREADABLE.safeMessage,
            failure?.message
        )
        assertFalse(failure?.message.orEmpty().contains("C:\\"))
    }

    private fun session(sessionId: String, vararg messages: GeminiMessageUsage) =
        GeminiSessionUsage(sessionId, messages.toList())

    private fun message(id: String, model: String, tokens: Long, at: String) = GeminiMessageUsage(
        messageId = id,
        capturedAt = Instant.parse(at),
        modelName = model,
        totalTokens = tokens
    )

    private class FixedGeminiDataSource(
        private val sessions: List<GeminiSessionUsage>
    ) : GeminiUsageDataSource {
        override suspend fun loadSessions(): List<GeminiSessionUsage> = sessions
    }
}
