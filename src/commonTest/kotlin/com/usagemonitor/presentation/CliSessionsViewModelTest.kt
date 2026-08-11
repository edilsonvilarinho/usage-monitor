package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val FIXED_NOW = Instant.parse("2026-08-10T12:00:00Z")
private const val FIVE_HOURS_MILLIS = 5L * 60 * 60 * 1_000
private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1_000
private const val LIVE_INTERVAL_MILLIS = 5_000L

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
    fun `total tokens sums every component of the listed sessions`() = runTest {
        val repository = FakeCliSessionRepository(
            sessions = listOf(
                summary("a", inputTokens = 100L, outputTokens = 200L, cacheReadTokens = 700L),
                summary("b", inputTokens = 1L, cacheWrite5mTokens = 2L, cacheWrite1hTokens = 3L)
            )
        )
        val viewModel = buildViewModel(repository)

        assertEquals(1_006L, assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).totalTokens)
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
    fun `the window defaults to the last five hours`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        assertEquals(
            FIXED_NOW.toEpochMilliseconds() - FIVE_HOURS_MILLIS,
            repository.lastSinceEpochMillis
        )
        assertEquals(
            CliSessionRange.LAST_5H,
            assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).range
        )
        viewModel.onDestroy()
    }

    @Test
    fun `selecting a window reloads with the matching cutoff`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.setRange(CliSessionRange.LAST_7D)

        assertEquals(
            FIXED_NOW.toEpochMilliseconds() - SEVEN_DAYS_MILLIS,
            repository.lastSinceEpochMillis
        )
        assertEquals(
            CliSessionRange.LAST_7D,
            assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).range
        )
        viewModel.onDestroy()
    }

    @Test
    fun `the total window asks the repository for every turn`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.setRange(CliSessionRange.ALL)

        assertNull(repository.lastSinceEpochMillis)
        viewModel.onDestroy()
    }

    @Test
    fun `the 5h window anchors on the account quota reset`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)
        val resetsAt = FIXED_NOW + 30.minutes

        viewModel.openForProfile(
            profileId = "conta2",
            profileLabel = "INFORMATA2",
            quotaWindows = CliQuotaWindows(fiveHourEndsAt = resetsAt)
        )

        assertEquals(
            resetsAt.toEpochMilliseconds() - FIVE_HOURS_MILLIS,
            repository.lastSinceEpochMillis
        )
        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(resetsAt, state.rangeEndsAt)
        assertTrue(state.rangeAnchored)
        viewModel.onDestroy()
    }

    @Test
    fun `without a quota reset the state reports a sliding window`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertNull(state.rangeEndsAt)
        assertFalse(state.rangeAnchored)
        viewModel.onDestroy()
    }

    @Test
    fun `a repeated quota reset does not reload the list`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val windows = CliQuotaWindows(fiveHourEndsAt = FIXED_NOW + 30.minutes)
        val viewModel = buildViewModel(repository, autoLoad = false)

        viewModel.openForProfile("conta2", "INFORMATA2", windows)
        val loadsAfterOpen = repository.syncCalls

        // O dashboard reemite o mesmo `resets_at` a cada coleta.
        viewModel.setQuotaWindows(windows)

        assertEquals(loadsAfterOpen, repository.syncCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `a new quota reset reloads with the new cutoff`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)
        viewModel.openForProfile(
            profileId = "conta2",
            profileLabel = "INFORMATA2",
            quotaWindows = CliQuotaWindows(fiveHourEndsAt = FIXED_NOW + 30.minutes)
        )

        val rolledOver = FIXED_NOW + 5.hours
        viewModel.setQuotaWindows(CliQuotaWindows(fiveHourEndsAt = rolledOver))

        assertEquals(
            rolledOver.toEpochMilliseconds() - FIVE_HOURS_MILLIS,
            repository.lastSinceEpochMillis
        )
        viewModel.onDestroy()
    }

    @Test
    fun `a quota reset does not reload while the total window is selected`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)
        viewModel.setRange(CliSessionRange.ALL)
        val syncCallsBefore = repository.syncCalls

        viewModel.setQuotaWindows(CliQuotaWindows(fiveHourEndsAt = FIXED_NOW + 30.minutes))

        assertEquals(syncCallsBefore, repository.syncCalls)
        assertNull(repository.lastSinceEpochMillis)
        viewModel.onDestroy()
    }

    @Test
    fun `the selected window survives refresh and account switch`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.setRange(CliSessionRange.LAST_30D)
        viewModel.refresh()
        viewModel.openForProfile("conta3", "INFORMATA")

        assertEquals("conta3", repository.lastProfileId)
        assertEquals(
            CliSessionRange.LAST_30D,
            assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).range
        )
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

    @Test
    fun `background indexing runs at boot and again after the interval`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(
            repository = repository,
            autoLoad = false,
            backgroundIndexIntervalMillis = 10 * 60 * 1_000L
        )

        try {
            runCurrent()
            // Índice sincronizado sem a lista carregada: a janela nem foi aberta.
            assertEquals(1, repository.syncCalls)
            assertIs<CliSessionsUiState.Loading>(viewModel.uiState.value)

            advanceTimeBy(10 * 60 * 1_000L)
            runCurrent()

            assertEquals(2, repository.syncCalls)
            assertIs<CliSessionsUiState.Loading>(viewModel.uiState.value)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `background indexing alone never touches the list`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(
            repository = repository,
            backgroundIndexIntervalMillis = 10 * 60 * 1_000L
        )

        try {
            runCurrent()
            repository.sessions = listOf(summary("a"), summary("b"))

            advanceTimeBy(10 * 60 * 1_000L)
            runCurrent()

            // Com a janela fechada basta o índice estar em dia; recarregar a lista
            // é trabalho do laço ao vivo.
            val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
            assertEquals(listOf("a"), state.sessions.map { session -> session.sessionId })
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the live loop reloads the list while the window is open`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            repository.sessions = listOf(summary("a"), summary("b"))

            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
            assertEquals(listOf("a", "b"), state.sessions.map { session -> session.sessionId })
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `closing the window stops the live loop`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            viewModel.closeWindow()
            val syncCallsAtClose = repository.syncCalls

            advanceTimeBy(10 * LIVE_INTERVAL_MILLIS)
            runCurrent()

            assertEquals(syncCallsAtClose, repository.syncCalls)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the live loop refreshes the open detail without going back to Loading`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.turnsBySession["a"] = listOf(turn(seq = 1, cacheReadTokens = 20_000L))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            viewModel.openSession("a")

            val states = mutableListOf<CliSessionDetailUiState?>()
            repository.turnsBySession["a"] = listOf(
                turn(seq = 1, cacheReadTokens = 20_000L),
                turn(seq = 2, cacheReadTokens = 90_000L)
            )

            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()
            states.add((viewModel.uiState.value as? CliSessionsUiState.Success)?.detail)

            val detail = assertIs<CliSessionDetailUiState.Ready>(states.last())
            assertEquals(2, detail.result.detail.turns.size)
            assertEquals(90_000L, detail.result.analytics.liveContextTokens)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `a failed detail reload keeps the last good detail on screen`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.turnsBySession["a"] = listOf(turn(seq = 1, cacheReadTokens = 20_000L))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            viewModel.openSession("a")
            repository.detailOverride = Result.failure(IllegalStateException("io"))

            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            val detail = assertIs<CliSessionDetailUiState.Ready>(
                assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).detail
            )
            assertEquals(20_000L, detail.result.analytics.liveContextTokens)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the open detail survives a session leaving the time window`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            viewModel.openSession("a")

            // A sessão sai do recorte da lista; o detalhe é a sessão inteira.
            repository.sessions = emptyList()
            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
            assertTrue(state.sessions.isEmpty())
            assertIs<CliSessionDetailUiState.Ready>(state.detail)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the change stamp only moves when the content actually changes`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val clock = MovingClock(FIXED_NOW)
        val viewModel = buildViewModel(repository, autoLoad = false, clock = clock)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            val firstStamp = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).lastChangedAt
            val stateAfterOpen = viewModel.uiState.value

            advanceTimeBy(3 * LIVE_INTERVAL_MILLIS)
            runCurrent()

            // Nada mudou: o carimbo fica parado e o estado continua igual, então o
            // StateFlow não reemite e a tela não recompõe.
            val stateAfterIdleTicks = viewModel.uiState.value
            assertEquals(firstStamp, assertIs<CliSessionsUiState.Success>(stateAfterIdleTicks).lastChangedAt)
            assertEquals(stateAfterOpen, stateAfterIdleTicks)

            repository.sessions = listOf(summary("a"), summary("b"))
            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            val movedStamp = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).lastChangedAt
            assertTrue(movedStamp != null && firstStamp != null && movedStamp > firstStamp)
        } finally {
            viewModel.onDestroy()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.buildViewModel(
        repository: FakeCliSessionRepository,
        autoLoad: Boolean = true,
        backgroundIndexIntervalMillis: Long? = null,
        clock: Clock = FixedClock(FIXED_NOW)
    ): CliSessionsViewModel {
        return CliSessionsViewModel(
            getCliSessions = GetCliSessionsUseCase(repository, FixedClock(FIXED_NOW)),
            getCliSessionDetail = GetCliSessionDetailUseCase(repository),
            syncCliSessionIndex = SyncCliSessionIndexUseCase(repository),
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            autoLoad = autoLoad,
            backgroundIndexIntervalMillis = backgroundIndexIntervalMillis,
            liveIntervalMillis = LIVE_INTERVAL_MILLIS,
            clock = clock
        )
    }

    private fun summary(
        sessionId: String,
        costMicros: Long = 0L,
        unpricedTurnCount: Int = 0,
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/tmp/$sessionId.jsonl",
            firstTs = Instant.fromEpochMilliseconds(0L),
            lastTs = Instant.fromEpochMilliseconds(1_000L),
            primaryModel = "claude-opus-5",
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens,
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

/** Relógio parado: o corte da janela precisa ser conferível ao milissegundo. */
private class FixedClock(private val fixedNow: Instant) : Clock {
    override fun now(): Instant = fixedNow
}

/** Avança um segundo por leitura, para distinguir dois carimbos de alteração. */
private class MovingClock(start: Instant) : Clock {
    private var current = start

    override fun now(): Instant {
        current += 1.seconds
        return current
    }
}

private class FakeCliSessionRepository(
    var sessions: List<CliSessionSummary> = emptyList()
) : CliSessionRepository {

    val turnsBySession = mutableMapOf<String, List<CliSessionTurn>>()

    var syncResult: Result<CliSessionIndexReport> = Result.success(CliSessionIndexReport())
    var sessionsResult: Result<List<CliSessionSummary>>? = null
    var detailOverride: Result<CliSessionDetail?>? = null

    var syncCalls: Int = 0
    var lastSinceEpochMillis: Long? = null
    var lastProfileId: String? = null

    override suspend fun syncIndex(): Result<CliSessionIndexReport> {
        syncCalls++
        return syncResult
    }

    override suspend fun getSessions(
        profileId: String?,
        sinceEpochMillis: Long?
    ): Result<List<CliSessionSummary>> {
        lastSinceEpochMillis = sinceEpochMillis
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
}
