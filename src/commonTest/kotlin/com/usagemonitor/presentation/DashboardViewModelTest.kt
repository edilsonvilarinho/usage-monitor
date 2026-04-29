package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val fixedInstant = Instant.parse("2025-01-01T12:00:00Z")

    private val sampleAnthropicStats = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        apiName = "Anthropic",
        quotas = listOf(
            QuotaInfo(
                label = "Tokens",
                used = 50000L,
                total = 200000L,
                periodEndAt = fixedInstant,
                unit = UsageUnit.TOKENS,
                rawUsed = 50000L,
                rawTotal = 200000L
            )
        )
    )

    private val sampleMiniMaxStats = ApiUsageStats(
        source = ApiSource.MINIMAX,
        apiName = "MiniMax",
        quotas = listOf(
            QuotaInfo(
                label = "MiniMax-M*",
                used = 2223L,
                total = 4500L,
                periodEndAt = fixedInstant,
                unit = UsageUnit.REQUESTS,
                rawUsed = 2223L,
                rawTotal = 4500L
            )
        )
    )

    private fun defaultEnabledApis(): MutableStateFlow<Set<ApiSource>> {
        return MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))
    }

    private fun historyUseCase(recordedSnapshots: MutableList<ApiUsageStats>): RecordUsageSnapshotUseCase {
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
                recordedSnapshots += stats
            }

            override suspend fun getHistoryReport(source: ApiSource, range: com.usagemonitor.domain.entity.HistoryRange, now: Instant) =
                throw UnsupportedOperationException("Não utilizado neste teste")
        }
        return RecordUsageSnapshotUseCase(historyRepository)
    }

    private fun successViewModel(recordedSnapshots: MutableList<ApiUsageStats>): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            Clock.System
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    private fun failureViewModel(recordedSnapshots: MutableList<ApiUsageStats>): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                Exception("Token inválido")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                Exception("API Key não configurada")
            )
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                Exception("Sessão do Codex inválida")
            )
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            Clock.System
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    private fun partialSuccessViewModel(recordedSnapshots: MutableList<ApiUsageStats>): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                Exception("Credenciais expiradas")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            Clock.System
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    private suspend fun awaitSettledState(viewModel: DashboardViewModel): UiState {
        repeat(50) {
            val state = viewModel.uiState.value
            if (state !is UiState.Loading) {
                return state
            }
            delay(20)
        }
        return viewModel.uiState.value
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        repeat(80) {
            if (predicate()) {
                return
            }
            delay(20)
        }
    }

    @Test
    fun `initial state is Loading`() {
        val viewModel = successViewModel(mutableListOf())
        assertIs<UiState.Loading>(viewModel.uiState.value)
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to Success when both APIs succeed`() = runTest {
        val viewModel = successViewModel(mutableListOf())
        viewModel.refresh()
        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(2, state.data.size)
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to Error when all APIs fail`() = runTest {
        val viewModel = failureViewModel(mutableListOf())
        viewModel.refresh()
        val state = awaitSettledState(viewModel)
        assertIs<UiState.Error>(state)
        assert(state.message.contains("Anthropic"))
        assert(state.message.contains("MiniMax"))
        viewModel.onDestroy()
    }

    @Test
    fun `shows partial Success when only one API fails`() = runTest {
        val viewModel = partialSuccessViewModel(mutableListOf())
        viewModel.refresh()
        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(1, state.data.size)
        assertEquals("MiniMax", state.data[0].apiName)
        viewModel.onDestroy()
    }

    @Test
    fun `Success state contains correct API data`() = runTest {
        val viewModel = successViewModel(mutableListOf())
        viewModel.refresh()
        val state = awaitSettledState(viewModel) as UiState.Success
        val anthropicData = state.data.first { it.apiName == "Anthropic" }
        val minimaxData = state.data.first { it.apiName == "MiniMax" }
        assertEquals(50000L, anthropicData.quotas[0].used)
        assertEquals(2223L, minimaxData.quotas[0].used)
        viewModel.onDestroy()
    }

    @Test
    fun `refreshing one source updates only that card and resets countdown`() = runTest {
        var anthropicCalls = 0
        var minimaxCalls = 0
        val recordedSnapshots = mutableListOf<ApiUsageStats>()

        val updatedAnthropicStats = sampleAnthropicStats.copy(
            quotas = listOf(
                sampleAnthropicStats.quotas[0].copy(
                    used = 75000L,
                    rawUsed = 75000L
                )
            )
        )

        var anthropicResult = Result.success(sampleAnthropicStats)

        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                anthropicCalls += 1
                return anthropicResult
            }
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                minimaxCalls += 1
                return Result.success(sampleMiniMaxStats)
            }
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()
        awaitCondition { anthropicCalls >= 1 && minimaxCalls >= 1 }
        awaitSettledState(viewModel)
        val anthropicCallsAfterGlobalRefresh = anthropicCalls
        val minimaxCallsAfterGlobalRefresh = minimaxCalls

        anthropicResult = Result.success(updatedAnthropicStats)
        viewModel.refresh(ApiSource.ANTHROPIC)

        awaitCondition { anthropicCalls >= anthropicCallsAfterGlobalRefresh + 1 }
        assertEquals(600, viewModel.secondsUntilRefresh.value)

        val state = awaitSettledState(viewModel) as UiState.Success
        val anthropicData = state.data.first { it.source == ApiSource.ANTHROPIC }
        val minimaxData = state.data.first { it.source == ApiSource.MINIMAX }

        assertEquals(75000L, anthropicData.quotas[0].used)
        assertEquals(2223L, minimaxData.quotas[0].used)
        viewModel.onDestroy()
    }

    @Test
    fun `records snapshots only for successful sources`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val viewModel = partialSuccessViewModel(recordedSnapshots)
        viewModel.refresh()

        awaitCondition { recordedSnapshots.isNotEmpty() }

        assertTrue(recordedSnapshots.isNotEmpty())
        assertEquals(ApiSource.MINIMAX, recordedSnapshots.first().source)
        assertTrue(recordedSnapshots.none { it.source == ApiSource.ANTHROPIC })
        viewModel.onDestroy()
    }
}
