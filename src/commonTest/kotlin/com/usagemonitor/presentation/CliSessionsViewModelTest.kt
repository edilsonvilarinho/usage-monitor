package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.SetCliSessionHiddenUseCase
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CliSessionsViewModelTest {

    @Test
    fun `starts in Loading before the first load completes`() = runTest {
        val repository = FakeCliSessionRepository()
        val viewModel = buildViewModel(repository, autoLoad = false)

        assertIs<CliSessionsUiState.Loading>(viewModel.uiState.value)
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to Success with the indexed sessions`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a"), summary("b")))
        val viewModel = buildViewModel(repository)

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("a", "b"), state.sessions.map { session -> session.sessionId })
        assertEquals(1, repository.syncCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to Error when the index cannot be read`() = runTest {
        val repository = FakeCliSessionRepository()
        repository.sessionsResult = Result.failure(IllegalStateException("banco indisponível"))
        val viewModel = buildViewModel(repository)

        val state = assertIs<CliSessionsUiState.Error>(viewModel.uiState.value)
        assertEquals("banco indisponível", state.message)
        viewModel.onDestroy()
    }

    @Test
    fun `index failure becomes a warning when the stored index still reads`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.syncResult = Result.failure(IllegalStateException("transcript ilegível"))
        val viewModel = buildViewModel(repository)

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals("transcript ilegível", state.indexWarning)
        assertEquals(1, state.sessions.size)
        viewModel.onDestroy()
    }

    @Test
    fun `total cost sums the listed sessions`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(summary("a", costMicros = 1_500_000L), summary("b", costMicros = 500_000L))
        )
        val viewModel = buildViewModel(repository)

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(2_000_000L, state.totalCostMicros)
        assertTrue(state.isTotalCostComplete)
        viewModel.onDestroy()
    }

    @Test
    fun `total cost is flagged incomplete when a session has unpriced turns`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(summary("a"), summary("b", unpricedTurnCount = 2))
        )
        val viewModel = buildViewModel(repository)

        assertFalse(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).isTotalCostComplete)
        viewModel.onDestroy()
    }

    @Test
    fun `opening a session loads the detail with analytics`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.turnsBySession["a"] = listOf(turn(seq = 1, cacheReadTokens = 20_000L))
        val viewModel = buildViewModel(repository)

        viewModel.openSession("a")

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        val detail = assertIs<CliSessionDetailUiState.Ready>(state.detail)
        assertEquals("a", detail.sessionId)
        assertEquals(1, detail.result.detail.turns.size)
        assertEquals(20_000L, detail.result.analytics.liveContextTokens)
        viewModel.onDestroy()
    }

    @Test
    fun `opening a session missing from the index surfaces a detail error`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.detailOverride = Result.success(null)
        val viewModel = buildViewModel(repository)

        viewModel.openSession("a")

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertIs<CliSessionDetailUiState.Error>(state.detail)
        viewModel.onDestroy()
    }

    @Test
    fun `detail failure keeps the list visible`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.detailOverride = Result.failure(IllegalStateException("io"))
        val viewModel = buildViewModel(repository)

        viewModel.openSession("a")

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals("io", assertIs<CliSessionDetailUiState.Error>(state.detail).message)
        assertEquals(1, state.sessions.size)
        viewModel.onDestroy()
    }

    @Test
    fun `closing the detail returns to the list`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.openSession("a")
        viewModel.closeDetail()

        assertNull(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).detail)
        viewModel.onDestroy()
    }

    @Test
    fun `toggling hidden sessions reloads including them`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        assertEquals(false, repository.lastIncludeHidden)

        viewModel.toggleShowHidden()

        assertEquals(true, repository.lastIncludeHidden)
        assertTrue(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).showHidden)
        viewModel.onDestroy()
    }

    @Test
    fun `hiding a session records the flag and reloads the list`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a"), summary("b")))
        val viewModel = buildViewModel(repository)

        repository.sessions = listOf(summary("b"))
        viewModel.setSessionHidden("a", hidden = true)

        assertEquals(listOf("a" to true), repository.hiddenCalls)
        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("b"), state.sessions.map { session -> session.sessionId })
        viewModel.onDestroy()
    }

    @Test
    fun `hiding the open session drops the detail on reload`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a"), summary("b")))
        val viewModel = buildViewModel(repository)

        viewModel.openSession("a")
        repository.sessions = listOf(summary("b"))
        viewModel.setSessionHidden("a", hidden = true)

        assertNull(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).detail)
        viewModel.onDestroy()
    }

    @Test
    fun `failing to hide a session surfaces an error`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.hiddenResult = Result.failure(IllegalStateException("banco travado"))
        val viewModel = buildViewModel(repository)

        viewModel.setSessionHidden("a", hidden = true)

        assertEquals("banco travado", assertIs<CliSessionsUiState.Error>(viewModel.uiState.value).message)
        viewModel.onDestroy()
    }

    @Test
    fun `opening for a profile filters the list to that account`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        viewModel.openForProfile("conta2", "INFORMATA2")

        assertEquals("conta2", repository.lastProfileId)
        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals("INFORMATA2", state.profileLabel)
        viewModel.onDestroy()
    }

    @Test
    fun `the selected account survives refresh and hidden toggle`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        viewModel.openForProfile("conta3", "INFORMATA")
        viewModel.toggleShowHidden()

        assertEquals("conta3", repository.lastProfileId)
        assertEquals(true, repository.lastIncludeHidden)
        viewModel.onDestroy()
    }

    @Test
    fun `switching accounts closes the open detail`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.openSession("a")
        assertIs<CliSessionDetailUiState.Ready>(
            assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).detail
        )

        viewModel.openForProfile("conta2", "INFORMATA2")

        assertNull(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).detail)
        viewModel.onDestroy()
    }

    @Test
    fun `refresh reindexes and reloads`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.refresh()

        assertEquals(2, repository.syncCalls)
        viewModel.onDestroy()
    }

    private fun kotlinx.coroutines.test.TestScope.buildViewModel(
        repository: FakeCliSessionRepository,
        autoLoad: Boolean = true
    ): CliSessionsViewModel {
        return CliSessionsViewModel(
            getCliSessions = GetCliSessionsUseCase(repository),
            getCliSessionDetail = GetCliSessionDetailUseCase(repository),
            setCliSessionHidden = SetCliSessionHiddenUseCase(repository),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            autoLoad = autoLoad
        )
    }

    private fun summary(
        sessionId: String,
        costMicros: Long = 0L,
        unpricedTurnCount: Int = 0
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/tmp/$sessionId.jsonl",
            firstTs = Instant.fromEpochMilliseconds(0L),
            lastTs = Instant.fromEpochMilliseconds(1_000L),
            primaryModel = "claude-opus-5",
            costMicros = costMicros,
            unpricedTurnCount = unpricedTurnCount
        )
    }

    private fun turn(seq: Int, cacheReadTokens: Long): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "a",
            seq = seq,
            messageId = "msg-$seq",
            ts = Instant.fromEpochMilliseconds(seq.toLong() * 1_000L),
            model = "claude-opus-5",
            cacheReadTokens = cacheReadTokens
        )
    }
}

private class FakeCliSessionRepository(
    var sessions: List<CliSessionSummary> = emptyList()
) : CliSessionRepository {

    val turnsBySession = mutableMapOf<String, List<CliSessionTurn>>()
    val hiddenCalls = mutableListOf<Pair<String, Boolean>>()

    var syncResult: Result<CliSessionIndexReport> = Result.success(CliSessionIndexReport())
    var sessionsResult: Result<List<CliSessionSummary>>? = null
    var detailOverride: Result<CliSessionDetail?>? = null
    var hiddenResult: Result<Unit> = Result.success(Unit)

    var syncCalls: Int = 0
    var lastIncludeHidden: Boolean? = null
    var lastProfileId: String? = null

    override suspend fun syncIndex(): Result<CliSessionIndexReport> {
        syncCalls++
        return syncResult
    }

    override suspend fun getSessions(
        profileId: String?,
        includeHidden: Boolean
    ): Result<List<CliSessionSummary>> {
        lastIncludeHidden = includeHidden
        lastProfileId = profileId
        return sessionsResult ?: Result.success(sessions)
    }

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> {
        detailOverride?.let { override -> return override }

        val summary = sessions.firstOrNull { session -> session.sessionId == sessionId }
            ?: return Result.success(null)
        return Result.success(
            CliSessionDetail(summary = summary, turns = turnsBySession[sessionId].orEmpty())
        )
    }

    override suspend fun setSessionHidden(sessionId: String, hidden: Boolean): Result<Unit> {
        hiddenCalls.add(sessionId to hidden)
        return hiddenResult
    }
}
