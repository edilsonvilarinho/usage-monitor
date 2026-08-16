package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.toUsageBreakdown
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.MICROS_PER_USD
import com.usagemonitor.domain.entity.startOfMonthMillis
import com.usagemonitor.domain.usecase.GetCliUsageBreakdownUseCase
import com.usagemonitor.domain.usecase.GetMonthlyBudgetStatusUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsView
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel
import com.usagemonitor.presentation.viewmodel.UsageExportWriter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
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

    /**
     * Issue #35: com o reset vencido o corte voltava a `now - 5h` e a lista
     * continuava mostrando turnos da janela de quota anterior.
     */
    @Test
    fun `an expired quota reset cuts at the reset`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)
        val expiredResetAt = FIXED_NOW - 30.minutes

        viewModel.openForProfile(
            profileId = "conta2",
            profileLabel = "INFORMATA2",
            quotaWindows = CliQuotaWindows(fiveHourEndsAt = expiredResetAt)
        )

        assertEquals(expiredResetAt.toEpochMilliseconds(), repository.lastSinceEpochMillis)
        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertNull(state.rangeEndsAt)
        assertTrue(state.rangeAnchored)
        viewModel.onDestroy()
    }

    /**
     * `setQuotaWindows` só recarrega quando o valor muda, e o `fiveHourEndsAt`
     * não muda ao vencer: quem vira a chave é o tique do laço ao vivo.
     */
    @Test
    fun `the live loop re-anchors the window when the reset expires`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val resetsAt = FIXED_NOW + 2.seconds
        val viewModel = buildViewModel(
            repository = repository,
            autoLoad = false,
            useCaseClock = SchedulerClock(FIXED_NOW, testScheduler)
        )

        try {
            viewModel.openForProfile(
                profileId = "conta2",
                profileLabel = "INFORMATA2",
                quotaWindows = CliQuotaWindows(fiveHourEndsAt = resetsAt)
            )
            assertEquals(
                resetsAt.toEpochMilliseconds() - FIVE_HOURS_MILLIS,
                repository.lastSinceEpochMillis
            )

            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            assertEquals(resetsAt.toEpochMilliseconds(), repository.lastSinceEpochMillis)
            val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
            assertNull(state.rangeEndsAt)
            assertTrue(state.rangeAnchored)
        } finally {
            viewModel.onDestroy()
        }
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
    fun `toggling the advanced block flips the flag`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        try {
            assertFalse(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).advancedExpanded)

            viewModel.toggleAdvanced()
            assertTrue(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).advancedExpanded)

            viewModel.toggleAdvanced()
            assertFalse(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).advancedExpanded)
        } finally {
            viewModel.onDestroy()
        }
    }

    /**
     * `loadSessions` reconstrói o `Success` do zero, e o laço ao vivo o chama de
     * cinco em cinco segundos. Sem carregar a flag do estado anterior o bloco
     * Avançado fecharia sozinho na cara de quem o abriu.
     */
    @Test
    fun `the advanced block survives a tick of the live loop`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            viewModel.toggleAdvanced()

            // Muda o conteúdo: um tique sem novidade não reemite o estado e o
            // teste passaria por acidente.
            repository.sessions = listOf(summary("a"), summary("b"))
            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
            assertEquals(listOf("a", "b"), state.sessions.map { session -> session.sessionId })
            assertTrue(state.advancedExpanded)
        } finally {
            viewModel.onDestroy()
        }
    }

    @Test
    fun `the glossary panel survives a tick of the live loop`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository, autoLoad = false)

        try {
            viewModel.openForProfile("conta2", "INFORMATA2")
            viewModel.toggleGlossary()

            repository.sessions = listOf(summary("a"), summary("b"))
            advanceTimeBy(LIVE_INTERVAL_MILLIS)
            runCurrent()

            val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
            assertEquals(listOf("a", "b"), state.sessions.map { session -> session.sessionId })
            assertTrue(state.glossaryExpanded)
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

    @Test
    fun `the breakdown is only read when its tab opens`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        assertEquals(0, repository.breakdownCalls)

        viewModel.setView(CliSessionsView.BREAKDOWN)
        runCurrent()

        assertEquals(1, repository.breakdownCalls)
        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(CliSessionsView.BREAKDOWN, state.view)
        viewModel.onDestroy()
    }

    /** O resumo descreve a mesma janela da lista; ficar para trás mostraria dois recortes. */
    @Test
    fun `changing the range reloads the open breakdown`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.setView(CliSessionsView.BREAKDOWN)
        runCurrent()
        assertEquals(
            FIXED_NOW.toEpochMilliseconds() - FIVE_HOURS_MILLIS,
            repository.lastBreakdownSinceEpochMillis
        )

        viewModel.setRange(CliSessionRange.ALL)
        runCurrent()

        assertEquals(2, repository.breakdownCalls)
        // `ALL` não corta nada, e o repositório abrange tudo a partir de zero.
        assertEquals(0L, repository.lastBreakdownSinceEpochMillis)
        viewModel.onDestroy()
    }

    /** Apagar os números por causa de uma leitura ruim tiraria da tela o que está sendo lido. */
    @Test
    fun `a failed breakdown reading keeps the previous numbers`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        repository.breakdownResult = Result.success(
            listOf(
                CliUsageGroupRow(sessionId = "a", cwd = "/home/dev/alpha", model = "claude-opus-5", turnCount = 1)
            ).toUsageBreakdown()
        )
        val viewModel = buildViewModel(repository)

        viewModel.setView(CliSessionsView.BREAKDOWN)
        runCurrent()
        val loaded = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).breakdown
        assertEquals(1, loaded?.byProject?.size)

        repository.breakdownResult = Result.failure(IllegalStateException("banco travado"))
        viewModel.setRange(CliSessionRange.ALL)
        runCurrent()

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(loaded, state.breakdown)
        assertEquals("banco travado", state.breakdownError)
        viewModel.onDestroy()
    }

    /** Sem carregar a aba do estado anterior, a tela voltaria para a lista sozinha. */
    @Test
    fun `the live loop keeps the open tab`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)
        viewModel.openForProfile(profileId = "default", profileLabel = null)
        runCurrent()

        viewModel.setView(CliSessionsView.BREAKDOWN)
        runCurrent()
        val callsBeforeTick = repository.breakdownCalls

        advanceTimeBy(LIVE_INTERVAL_MILLIS + 1)
        runCurrent()

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(CliSessionsView.BREAKDOWN, state.view)
        assertTrue(repository.breakdownCalls > callsBeforeTick)
        viewModel.onDestroy()
    }

    /** Exportar um recorte diferente do que está na tela seria surpresa. */
    @Test
    fun `exporting follows the open tab and the chosen window`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val writer = RecordingExportWriter()
        val viewModel = buildViewModel(repository, exportWriter = writer)

        viewModel.exportCurrentView(UsageExportFormat.CSV)
        runCurrent()

        val request = writer.requests.single()
        assertTrue(request.suggestedFileName.startsWith("usage-monitor-sessions-5h-"))
        assertTrue(request.suggestedFileName.endsWith(".csv"))
        assertTrue(request.content.contains("session_id"))
        assertTrue(request.content.contains("a,"))

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(CliExportOutcome.Saved("/tmp/export.csv"), state.exportOutcome)
        viewModel.onDestroy()
    }

    /** Cancelar não é falha: não publica resultado nenhum. */
    @Test
    fun `cancelling the dialog publishes no outcome`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val writer = RecordingExportWriter(savedPath = null)
        val viewModel = buildViewModel(repository, exportWriter = writer)

        viewModel.exportCurrentView(UsageExportFormat.JSON)
        runCurrent()

        assertNull(assertIs<CliSessionsUiState.Success>(viewModel.uiState.value).exportOutcome)
        viewModel.onDestroy()
    }

    @Test
    fun `a failed write is reported without losing the list`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val writer = RecordingExportWriter(failure = IllegalStateException("disco cheio"))
        val viewModel = buildViewModel(repository, exportWriter = writer)

        viewModel.exportCurrentView(UsageExportFormat.CSV)
        runCurrent()

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(CliExportOutcome.Failed("disco cheio"), state.exportOutcome)
        assertEquals(1, state.sessions.size)
        viewModel.onDestroy()
    }

    /** Orçamento é mensal: o chip de janela não pode mexer nele. */
    @Test
    fun `the budget reads the current month regardless of the window`() = runTest {
        val repository = FakeCliSessionRepository(sessions = listOf(summary("a")))
        val viewModel = buildViewModel(repository)

        viewModel.setBudgetLimitMicros(200L * MICROS_PER_USD)
        runCurrent()

        val startOfMonth = startOfMonthMillis(FIXED_NOW, TimeZone.of("America/Sao_Paulo"))
        assertEquals(startOfMonth, repository.lastBreakdownSinceEpochMillis)

        val state = assertIs<CliSessionsUiState.Success>(viewModel.uiState.value)
        assertEquals(200L * MICROS_PER_USD, state.budget?.limitMicros)
        viewModel.onDestroy()
    }

    private fun kotlinx.coroutines.test.TestScope.buildViewModel(
        repository: FakeCliSessionRepository,
        exportWriter: UsageExportWriter? = null,
        autoLoad: Boolean = true,
        backgroundIndexIntervalMillis: Long? = null,
        clock: Clock = FixedClock(FIXED_NOW),
        // O corte da janela é resolvido dentro do caso de uso; separá-lo do
        // relógio do carimbo permite mover o tempo só onde interessa.
        useCaseClock: Clock = FixedClock(FIXED_NOW)
    ): CliSessionsViewModel {
        return CliSessionsViewModel(
            getCliSessions = GetCliSessionsUseCase(repository, useCaseClock),
            getCliSessionDetail = GetCliSessionDetailUseCase(repository),
            syncCliSessionIndex = SyncCliSessionIndexUseCase(repository),
            getCliUsageBreakdown = GetCliUsageBreakdownUseCase(repository, useCaseClock),
            exportWriter = exportWriter,
            getMonthlyBudgetStatus = GetMonthlyBudgetStatusUseCase(repository, useCaseClock),
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

/** Anda junto com o tempo virtual: avançar o laço e avançar o relógio viram um gesto só. */
private class SchedulerClock(
    private val origin: Instant,
    private val scheduler: kotlinx.coroutines.test.TestCoroutineScheduler
) : Clock {
    override fun now(): Instant = origin + scheduler.currentTime.milliseconds
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

    var breakdownResult: Result<CliUsageBreakdown> = Result.success(CliUsageBreakdown())
    var hourlyResult: Result<List<CliHourlyUsageRow>> = Result.success(emptyList())
    var toolResult: Result<List<CliToolUsage>> = Result.success(emptyList())
    var breakdownCalls: Int = 0
    var lastBreakdownSinceEpochMillis: Long? = null

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

    override suspend fun getUsageBreakdown(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<CliUsageBreakdown> {
        breakdownCalls++
        lastBreakdownSinceEpochMillis = sinceEpochMillis
        return breakdownResult
    }

    override suspend fun getHourlyUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliHourlyUsageRow>> {
        return hourlyResult
    }

    override suspend fun getToolUsage(
        profileId: String?,
        sinceEpochMillis: Long
    ): Result<List<CliToolUsage>> {
        return toolResult
    }
}

/** Escreve na memória: o teste não pode abrir diálogo nem tocar no disco. */
private class RecordingExportWriter(
    private val savedPath: String? = "/tmp/export.csv",
    private val failure: Throwable? = null
) : UsageExportWriter {

    val requests = mutableListOf<UsageExportRequest>()

    override suspend fun write(request: UsageExportRequest): String? {
        requests += request
        if (failure != null) {
            throw failure
        }
        return savedPath
    }
}
