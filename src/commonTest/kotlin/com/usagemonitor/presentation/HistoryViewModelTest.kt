package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoryViewModelTest {

    private val now = Instant.parse("2026-04-28T18:00:00Z")

    private suspend fun awaitNonLoading(viewModel: HistoryViewModel): HistoryUiState {
        repeat(200) {
            val state = viewModel.uiState.value
            if (state !is HistoryUiState.Loading) {
                return state
            }
            delay(20)
        }
        return viewModel.uiState.value
    }

    private suspend fun awaitInvocations(repo: FakeRepo, expected: Int) {
        repeat(200) {
            if (repo.invocations >= expected) {
                return
            }
            delay(20)
        }
    }

    @Test
    fun `emits Empty state when enabledApis is empty`() = runTest {
        val repo = FakeRepo()
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(emptySet())
        )

        val state = awaitNonLoading(viewModel)
        assertIs<HistoryUiState.Empty>(state)
        assertEquals(null, state.selectedSource)
        viewModel.onDestroy()
    }

    @Test
    fun `selects first enabled source when none preselected`() = runTest {
        val repo = FakeRepo(report = emptyReport(ApiSource.ANTHROPIC))
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))
        )

        val state = awaitNonLoading(viewModel)
        assertIs<HistoryUiState.Success>(state)
        assertEquals(ApiSource.ANTHROPIC, state.selectedSource)
    }

    @Test
    fun `selectSource changes selection and reloads`() = runTest {
        val repo = FakeRepo(report = emptyReport(ApiSource.ANTHROPIC))
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))
        )
        awaitNonLoading(viewModel)
        repo.report = emptyReport(ApiSource.MINIMAX)
        val callsBefore = repo.invocations

        viewModel.selectSource(ApiSource.MINIMAX)
        awaitInvocations(repo, callsBefore + 1)
        val state = awaitNonLoading(viewModel)

        assertIs<HistoryUiState.Success>(state)
        assertEquals(ApiSource.MINIMAX, state.selectedSource)
        assertTrue(repo.invocations >= 2)
        viewModel.onDestroy()
    }

    @Test
    fun `selectSource is no-op when already selected`() = runTest {
        val repo = FakeRepo(report = emptyReport(ApiSource.ANTHROPIC))
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
        )
        awaitNonLoading(viewModel)
        val callsBefore = repo.invocations

        viewModel.selectSource(ApiSource.ANTHROPIC)
        // Pequena espera: se houvesse refresh, invocations subiria.
        delay(100)

        assertEquals(callsBefore, repo.invocations)
        viewModel.onDestroy()
    }

    @Test
    fun `selectRange triggers reload with new range`() = runTest {
        val repo = FakeRepo(report = emptyReport(ApiSource.ANTHROPIC))
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
        )
        awaitNonLoading(viewModel)

        val callsBefore = repo.invocations
        viewModel.selectRange(HistoryRange.LAST_7_DAYS)
        awaitInvocations(repo, callsBefore + 1)
        val state = awaitNonLoading(viewModel)

        assertIs<HistoryUiState.Success>(state)
        assertEquals(HistoryRange.LAST_7_DAYS, state.selectedRange)
        // HistoryViewModel busca o relatório de totais (parametrizado por range) e o de
        // linhas (sempre TOTAL) em paralelo — ambos devem ter sido requisitados.
        assertTrue(repo.requestedRanges.contains(HistoryRange.LAST_7_DAYS))
        assertTrue(repo.requestedRanges.contains(HistoryRange.TOTAL))
        viewModel.onDestroy()
    }

    @Test
    fun `emits Error state when use case throws`() = runTest {
        val repo = FakeRepo(error = IllegalStateException("falha de leitura"))
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC))
        )

        val state = awaitNonLoading(viewModel)
        assertIs<HistoryUiState.Error>(state)
        assertEquals("falha de leitura", state.message)
        viewModel.onDestroy()
    }

    @Test
    fun `openForSource sets source and reloads`() = runTest {
        val repo = FakeRepo(report = emptyReport(ApiSource.ANTHROPIC))
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.CODEX))
        )
        awaitNonLoading(viewModel)
        repo.report = emptyReport(ApiSource.CODEX)
        val callsBefore = repo.invocations

        viewModel.openForSource(ApiSource.CODEX)
        awaitInvocations(repo, callsBefore + 1)
        val state = awaitNonLoading(viewModel)

        assertIs<HistoryUiState.Success>(state)
        assertEquals(ApiSource.CODEX, state.selectedSource)
        viewModel.onDestroy()
    }

    @Test
    fun `selects most recent account and reloads history when account changes`() = runTest {
        val accountA = account("user-a", "workspace-a", "a@example.com")
        val accountB = account("user-b", "workspace-b", "b@example.com")
        val repo = FakeRepo(
            report = emptyReport(ApiSource.CODEX),
            accounts = listOf(accountA, accountB)
        )
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX))
        )

        val initialState = awaitNonLoading(viewModel)
        assertIs<HistoryUiState.Success>(initialState)
        assertEquals(accountA, initialState.selectedAccount)
        assertEquals(accountA.key, repo.lastAccountKey)
        val callsBefore = repo.invocations

        viewModel.selectAccount(accountB)
        awaitInvocations(repo, callsBefore + 1)
        val updatedState = awaitNonLoading(viewModel)

        assertIs<HistoryUiState.Success>(updatedState)
        assertEquals(accountB, updatedState.selectedAccount)
        assertEquals(accountB.key, repo.lastAccountKey)
        viewModel.onDestroy()
    }

    @Test
    fun `openForSource preselects account requested by dashboard card`() = runTest {
        val accountA = account("user-a", "workspace-a", "a@example.com")
        val accountB = account("user-b", "workspace-b", "b@example.com")
        val repo = FakeRepo(
            report = emptyReport(ApiSource.CODEX),
            accounts = listOf(accountA, accountB)
        )
        val viewModel = HistoryViewModel(
            getUsageHistory = GetUsageHistoryUseCase(repo) { now },
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX))
        )
        awaitNonLoading(viewModel)
        val callsBefore = repo.invocations

        viewModel.openForSource(ApiSource.CODEX, accountB.key)
        awaitInvocations(repo, callsBefore + 1)
        val state = awaitNonLoading(viewModel)

        assertIs<HistoryUiState.Success>(state)
        assertEquals(accountB, state.selectedAccount)
        assertEquals(accountB.key, repo.lastAccountKey)
        viewModel.onDestroy()
    }

    private fun emptyReport(source: ApiSource): ApiUsageHistoryReport {
        return ApiUsageHistoryReport(
            source = source,
            range = HistoryRange.LAST_24_HOURS,
            lastUpdatedAt = null,
            series = emptyList()
        )
    }

    private fun account(userId: String, workspaceId: String, email: String): UsageAccountContext {
        return UsageAccountContext(
            key = UsageAccountKey(
                source = ApiSource.CODEX,
                providerAccountId = userId,
                workspaceId = workspaceId
            ),
            email = email,
            workspaceName = workspaceId
        )
    }

    private class FakeRepo(
        var report: ApiUsageHistoryReport? = null,
        var error: Throwable? = null,
        var accounts: List<UsageAccountContext> = emptyList()
    ) : UsageHistoryRepository {
        // HistoryViewModel busca o relatório de totais e o de linhas em paralelo
        // (coroutines reais em Dispatchers.Default) — estado precisa ser thread-safe.
        private val invocationsCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val requestedRanges: MutableList<HistoryRange> = java.util.Collections.synchronizedList(mutableListOf())
        @Volatile var lastAccountKey: UsageAccountKey? = null

        val invocations: Int get() = invocationsCounter.get()

        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

        override suspend fun getHistoryReport(
            source: ApiSource,
            range: HistoryRange,
            now: Instant
        ): ApiUsageHistoryReport {
            return respond(range)
        }

        override suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> {
            return accounts.filter { account -> account.key.source == source }
        }

        override suspend fun getHistoryReport(
            source: ApiSource,
            accountKey: UsageAccountKey?,
            range: HistoryRange,
            now: Instant
        ): ApiUsageHistoryReport {
            lastAccountKey = accountKey
            return respond(range)
        }

        private fun respond(range: HistoryRange): ApiUsageHistoryReport {
            invocationsCounter.incrementAndGet()
            requestedRanges += range
            error?.let { throw it }
            return report ?: throw IllegalStateException("FakeRepo: nem report nem error configurado")
        }
    }
}

private fun GetUsageHistoryUseCase(
    repo: UsageHistoryRepository,
    nowProvider: () -> Instant
): GetUsageHistoryUseCase {
    return GetUsageHistoryUseCase(repo, clock = object : kotlinx.datetime.Clock {
        override fun now(): Instant = nowProvider()
    })
}
