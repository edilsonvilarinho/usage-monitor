package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.repository.CodexCliSessionRepository
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CodexCliSessionsViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.coroutines.CoroutineDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class CodexCliSessionsViewModelTest {
    @Test
    fun `refresh exposes list and index warning without failing the screen`() = runTest {
        val repository = FakeRepository(indexFailure = IllegalStateException("partial index"))
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.refresh()
        testScheduler.runCurrent()

        val state = assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("session-1"), state.sessions.map { session -> session.sessionId })
        assertEquals("partial index", state.indexWarning)
        viewModel.onDestroy()
    }

    @Test
    fun `range selection reloads with the selected cutoff`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.setRange(CodexCliSessionRange.ALL)
        testScheduler.runCurrent()

        assertEquals(null, repository.lastSince)
        assertEquals(CodexCliSessionRange.ALL, assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).range)
        viewModel.onDestroy()
    }

    @Test
    fun `seven day selection uses a seven day cutoff and publishes the selected range`() = runTest {
        val repository = FakeRepository()
        val now = Instant.parse("2026-09-08T15:00:00Z")
        val viewModel = viewModel(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = object : Clock {
                override fun now(): Instant = now
            }
        )

        viewModel.refresh()
        testScheduler.runCurrent()
        viewModel.setRange(CodexCliSessionRange.LAST_7D)
        testScheduler.runCurrent()

        val expectedCutoff = now.toEpochMilliseconds() - 7L * 24L * 60L * 60L * 1000L
        assertEquals(expectedCutoff, repository.lastSince)
        assertEquals(
            CodexCliSessionRange.LAST_7D,
            assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).range
        )
        viewModel.onDestroy()
    }

    @Test
    fun `opening the window always resets to the five hour range`() = runTest {
        val repository = FakeRepository()
        val now = Instant.parse("2026-09-08T15:00:00Z")
        val viewModel = viewModel(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = object : Clock {
                override fun now(): Instant = now
            }
        )

        viewModel.refresh()
        testScheduler.runCurrent()
        viewModel.setRange(CodexCliSessionRange.ALL)
        testScheduler.runCurrent()
        viewModel.openWindow()
        testScheduler.runCurrent()

        val expectedCutoff = now.toEpochMilliseconds() - 5L * 60L * 60L * 1000L
        assertEquals(expectedCutoff, repository.lastSince)
        assertEquals(
            CodexCliSessionRange.LAST_5H,
            assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).range
        )
        viewModel.onDestroy()
    }

    @Test
    fun `refreshes automatically while the window is open and stops after close`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
            liveIntervalMillis = 5_000L
        )

        viewModel.openWindow()
        testScheduler.runCurrent()
        val callsAfterOpen = repository.syncCalls

        advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(callsAfterOpen + 1, repository.syncCalls)

        viewModel.closeWindow()
        advanceTimeBy(5_000L)
        testScheduler.runCurrent()
        assertEquals(callsAfterOpen + 1, repository.syncCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `automatic refresh preserves and reloads the open detail`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(
            repository = repository,
            dispatcher = StandardTestDispatcher(testScheduler),
            liveIntervalMillis = 5_000L
        )

        viewModel.openWindow()
        testScheduler.runCurrent()
        viewModel.openSession("session-1")
        testScheduler.runCurrent()
        val detailCallsAfterOpen = repository.detailCalls

        advanceTimeBy(5_000L)
        testScheduler.runCurrent()

        val state = assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals("response-1", state.detail?.turns?.single()?.responseId)
        assertEquals(detailCallsAfterOpen + 1, repository.detailCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `detail opens and closes without losing the list`() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.refresh()
        testScheduler.runCurrent()
        viewModel.openSession("session-1")
        testScheduler.runCurrent()

        assertEquals("response-1", assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).detail?.turns?.single()?.responseId)
        viewModel.closeDetail()
        assertEquals(null, assertIs<CodexCliSessionsUiState.Success>(viewModel.uiState.value).detail)
        viewModel.onDestroy()
    }

    @Test
    fun `repository read failure reaches error state`() = runTest {
        val repository = FakeRepository(readFailure = IllegalStateException("read failed"))
        val viewModel = viewModel(repository, StandardTestDispatcher(testScheduler))

        viewModel.refresh()
        testScheduler.runCurrent()

        assertEquals("read failed", assertIs<CodexCliSessionsUiState.Error>(viewModel.uiState.value).message)
        viewModel.onDestroy()
    }

    private fun viewModel(
        repository: FakeRepository,
        dispatcher: CoroutineDispatcher,
        clock: Clock = Clock.System,
        liveIntervalMillis: Long? = null
    ): CodexCliSessionsViewModel {
        return CodexCliSessionsViewModel(
            getSessions = GetCodexCliSessionsUseCase(repository),
            getDetail = GetCodexCliSessionDetailUseCase(repository),
            dispatcher = dispatcher,
            clock = clock,
            liveIntervalMillis = liveIntervalMillis,
            autoLoad = false
        )
    }

    private class FakeRepository(
        private val indexFailure: Throwable? = null,
        private val readFailure: Throwable? = null
    ) : CodexCliSessionRepository {
        var lastSince: Long? = Long.MIN_VALUE
        var syncCalls: Int = 0
        var detailCalls: Int = 0

        override suspend fun syncIndex(): Result<CodexCliSessionIndexReport> {
            syncCalls++
            return indexFailure?.let { failure -> Result.failure(failure) }
                ?: Result.success(CodexCliSessionIndexReport(scannedFiles = 1))
        }

        override suspend fun getSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> {
            lastSince = sinceEpochMillis
            return readFailure?.let { failure -> Result.failure(failure) }
                ?: Result.success(listOf(summary()))
        }

        override suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?> {
            detailCalls++
            return Result.success(if (sessionId == "session-1") detail() else null)
        }

        private fun summary(): CodexCliSessionSummary {
            return CodexCliSessionSummary(
                sessionId = "session-1",
                filePath = "rollout.jsonl",
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
