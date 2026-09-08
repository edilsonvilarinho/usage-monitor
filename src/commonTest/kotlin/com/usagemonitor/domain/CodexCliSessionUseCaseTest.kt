package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.repository.CodexCliSessionRepository
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class CodexCliSessionUseCaseTest {
    @Test
    fun `list use case refreshes and forwards temporal filter`() = runTest {
        val repository = FakeRepository()
        val result = GetCodexCliSessionsUseCase(repository)(sinceEpochMillis = 123L)

        assertEquals(1, repository.syncCalls)
        assertEquals(123L, repository.lastSince)
        assertEquals(listOf("session-1"), result.getOrThrow().sessions.map { session -> session.sessionId })
        assertNotNull(result.getOrThrow().indexReport)
    }

    @Test
    fun `list use case preserves sessions when index refresh fails`() = runTest {
        val repository = FakeRepository(indexFailure = IllegalStateException("offline index"))
        val result = GetCodexCliSessionsUseCase(repository)().getOrThrow()

        assertEquals(listOf("session-1"), result.sessions.map { session -> session.sessionId })
        assertEquals("offline index", result.indexError?.message)
    }

    @Test
    fun `detail use case forwards the requested session`() = runTest {
        val repository = FakeRepository()
        val detail = GetCodexCliSessionDetailUseCase(repository)("session-1").getOrThrow()

        assertEquals("session-1", detail?.summary?.sessionId)
        assertEquals("response-1", detail?.turns?.single()?.responseId)
    }

    private class FakeRepository(
        private val indexFailure: Throwable? = null
    ) : CodexCliSessionRepository {
        var syncCalls = 0
        var lastSince: Long? = null

        override suspend fun syncIndex(): Result<CodexCliSessionIndexReport> {
            syncCalls++
            return indexFailure?.let { failure -> Result.failure(failure) }
                ?: Result.success(CodexCliSessionIndexReport(scannedFiles = 1, updatedFiles = 1))
        }

        override suspend fun getSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> {
            lastSince = sinceEpochMillis
            return Result.success(listOf(summary()))
        }

        override suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?> {
            return Result.success(if (sessionId == "session-1") detail() else null)
        }

        private fun summary(): CodexCliSessionSummary {
            return CodexCliSessionSummary(
                sessionId = "session-1",
                filePath = "rollout-session-1.jsonl",
                firstTs = Instant.parse("2026-09-08T12:00:00Z"),
                lastTs = Instant.parse("2026-09-08T12:00:01Z"),
                responseCount = 1,
                turnCount = 1
            )
        }

        private fun detail(): CodexCliSessionDetail {
            val turn = CodexCliSessionTurn(
                sessionId = "session-1",
                turnId = "turn-1",
                responseId = "response-1",
                seq = 0,
                ts = Instant.parse("2026-09-08T12:00:01Z")
            )
            return CodexCliSessionDetail(summary(), listOf(turn))
        }
    }
}
