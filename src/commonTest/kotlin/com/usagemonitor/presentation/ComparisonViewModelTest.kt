package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.repository.CliSessionRepository
import com.usagemonitor.domain.repository.CodexCliSessionRepository
import com.usagemonitor.domain.usecase.BuildModelComparisonUseCase
import com.usagemonitor.presentation.viewmodel.ComparisonUiState
import com.usagemonitor.presentation.viewmodel.ComparisonViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ComparisonViewModelTest {

    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val repository = BreakdownRepository()
    private var viewModel: ComparisonViewModel? = null

    @AfterTest
    fun tearDown() {
        viewModel?.onDestroy()
    }

    private fun TestScope.create(): ComparisonViewModel {
        val created = ComparisonViewModel(
            buildComparison = BuildModelComparisonUseCase(repository, EmptyCodexRepository),
            dashboardState = MutableStateFlow<UiState>(UiState.Loading),
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = object : Clock { override fun now() = this@ComparisonViewModelTest.now }
        )
        viewModel = created
        return created
    }

    @Test
    fun `abrir le os ultimos 7 dias e publica as linhas`() = runTest {
        val model = create()

        model.openWindow()
        advanceUntilIdle()

        val state = assertIs<ComparisonUiState.Success>(model.uiState.value)
        assertEquals(CliSessionRange.LAST_7D, state.range)
        assertEquals(now, state.readAt)
        assertEquals(listOf("claude-opus"), state.rows.map { row -> row.label })
        assertEquals(now.toEpochMilliseconds() - CliSessionRange.LAST_7D.durationMillis!!, repository.lastSince)
    }

    /** Regra do app: leitura que falha mantém os números anteriores. */
    @Test
    fun `falha depois de uma leitura mantem as linhas`() = runTest {
        val model = create()
        model.openWindow()
        advanceUntilIdle()

        repository.failure = IllegalStateException("índice travado")
        model.refresh()
        advanceUntilIdle()

        val state = assertIs<ComparisonUiState.Success>(model.uiState.value)
        assertEquals(listOf("claude-opus"), state.rows.map { row -> row.label })
        assertEquals(false, state.isRefreshing)
    }

    @Test
    fun `falha sem leitura anterior vira erro com a mensagem`() = runTest {
        repository.failure = IllegalStateException("índice travado")
        val model = create()

        model.openWindow()
        advanceUntilIdle()

        assertEquals(ComparisonUiState.Error("índice travado"), model.uiState.value)
    }

    @Test
    fun `escolher o recorte atual nao rele`() = runTest {
        val model = create()
        model.openWindow()
        advanceUntilIdle()
        val reads = repository.calls

        model.setRange(CliSessionRange.LAST_7D)
        advanceUntilIdle()
        assertEquals(reads, repository.calls)

        model.setRange(CliSessionRange.LAST_30D)
        advanceUntilIdle()
        assertEquals(reads + 1, repository.calls)
        assertEquals(CliSessionRange.LAST_30D, assertIs<ComparisonUiState.Success>(model.uiState.value).range)
    }
}

private class BreakdownRepository : CliSessionRepository {
    var failure: Throwable? = null
    var calls = 0
    var lastSince: Long? = null

    override suspend fun getUsageBreakdown(profileId: String?, sinceEpochMillis: Long): Result<CliUsageBreakdown> {
        calls++
        lastSince = sinceEpochMillis
        val error = failure
        if (error != null) return Result.failure(error)
        return Result.success(CliUsageBreakdown(byModel = listOf(CliUsageBucket(label = "claude-opus", turnCount = 3, inputTokens = 1_000L))))
    }

    override suspend fun syncIndex(): Result<CliSessionIndexReport> = Result.success(CliSessionIndexReport())

    override suspend fun getSessions(profileId: String?, sinceEpochMillis: Long?): Result<List<CliSessionSummary>> =
        Result.success(emptyList())

    override suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?> = Result.success(null)

    override suspend fun getHourlyUsage(profileId: String?, sinceEpochMillis: Long): Result<List<CliHourlyUsageRow>> =
        Result.success(emptyList())

    override suspend fun getToolUsage(profileId: String?, sinceEpochMillis: Long): Result<List<CliToolUsage>> =
        Result.success(emptyList())

    override suspend fun getSessionTails(sessionIds: Collection<String>): Result<List<CliSessionTail>> =
        Result.success(emptyList())
}

private object EmptyCodexRepository : CodexCliSessionRepository {
    override suspend fun syncIndex(): Result<CodexCliSessionIndexReport> = Result.success(CodexCliSessionIndexReport())

    override suspend fun getSessions(sinceEpochMillis: Long?): Result<List<CodexCliSessionSummary>> = Result.success(emptyList())

    override suspend fun getSessionDetail(sessionId: String): Result<CodexCliSessionDetail?> = Result.success(null)
}
