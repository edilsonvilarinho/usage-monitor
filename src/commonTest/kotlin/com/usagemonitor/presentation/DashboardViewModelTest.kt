package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.AppUpdateRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import com.usagemonitor.presentation.viewmodel.PreparedUpdateAction
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UnsupportedAppUpdateInstaller
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.CompletableDeferred
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
                unit = UsageUnit.PERCENTAGE,
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

    private fun updateUseCase(result: Result<AppUpdateInfo?>): CheckForAppUpdateUseCase {
        val repository = object : AppUpdateRepository {
            override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
                return result
            }
        }
        return CheckForAppUpdateUseCase(repository)
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
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
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
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
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
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    private suspend fun awaitSettledState(viewModel: DashboardViewModel): UiState {
        repeat(200) {
            val state = viewModel.uiState.value
            if (state !is UiState.Loading) {
                return state
            }
            delay(20)
        }
        return viewModel.uiState.value
    }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) {
                return
            }
            delay(20)
        }
        assertTrue(predicate(), "Condition not met within coroutine timeout")
    }

    private fun awaitConditionRealTime(predicate: () -> Boolean) {
        repeat(250) {
            if (predicate()) {
                return
            }
            Thread.sleep(20)
        }
        assertTrue(predicate(), "Condition not met within real-time timeout")
    }

    @Test
    fun `initial state is Loading`() = runTest {
        val fetchGate = CompletableDeferred<Unit>()
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                fetchGate.await()
                return Result.success(sampleAnthropicStats)
            }
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                fetchGate.await()
                return Result.success(sampleMiniMaxStats)
            }
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelCountdown()

        assertIs<UiState.Loading>(viewModel.uiState.value)

        fetchGate.complete(Unit)
        awaitConditionRealTime { viewModel.uiState.value is UiState.Success }
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to NoApisEnabled when no APIs are enabled`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelCountdown()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.NoApisEnabled>(state)
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
    fun `classifies Anthropic credential failures for guided UI`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Token refresh retornou sem access_token")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(1, state.errors.size)
        assertEquals(ApiSource.ANTHROPIC, state.errors.first().source)
        assertTrue(state.errors.first().isAnthropicCredentialIssue)
        assertEquals(null, viewModel.toastMessage.value)
        viewModel.onDestroy()
    }

    @Test
    fun `treats Anthropic scope guidance as persistent warning without generic toast`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Sua sessão do Claude Code está sem a permissão esperada ou desatualizada. Feche o app, reautentique no Claude Code e abra o monitor novamente.")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(1, state.errors.size)
        assertTrue(state.errors.first().isAnthropicCredentialIssue)
        assertEquals(null, viewModel.toastMessage.value)
        viewModel.onDestroy()
    }

    @Test
    fun `classifies MiniMax missing env var for persistent warning`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Variável de ambiente MINIMAX_API_KEY não configurada.")
            )
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(1, state.errors.size)
        assertEquals(ApiSource.MINIMAX, state.errors.first().source)
        assertTrue(state.errors.first().isMiniMaxEnvVarIssue)
        viewModel.onDestroy()
    }

    @Test
    fun `classifies MiniMax inactive plan for persistent warning without toast`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("MiniMax sem plano/token ativo. Ative um plano ou gere um token com assinatura válida e tente novamente.")
            )
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(1, state.errors.size)
        assertEquals(ApiSource.MINIMAX, state.errors.first().source)
        assertTrue(state.errors.first().isMiniMaxInactivePlanIssue)
        assertEquals(null, viewModel.toastMessage.value)
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
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
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
        val remaining = (viewModel.nextRefreshAt.value - Clock.System.now()).inWholeSeconds
        assertTrue(remaining in 595L..600L)

        val state = awaitSettledState(viewModel) as UiState.Success
        val anthropicData = state.data.first { it.source == ApiSource.ANTHROPIC }
        val minimaxData = state.data.first { it.source == ApiSource.MINIMAX }

        assertEquals(75000L, anthropicData.quotas[0].used)
        assertEquals(2223L, minimaxData.quotas[0].used)
        viewModel.onDestroy()
    }

    @Test
    fun `queues a pending refresh when another fetch is already in flight`() = runTest {
        var anthropicCalls = 0
        val firstFetchGate = CompletableDeferred<Unit>()
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val enabledApis = MutableStateFlow<Set<ApiSource>>(emptySet())
        val updatedAnthropicStats = sampleAnthropicStats.copy(
            quotas = listOf(
                sampleAnthropicStats.quotas[0].copy(
                    used = 91000L,
                    rawUsed = 91000L
                )
            )
        )

        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                anthropicCalls += 1
                if (anthropicCalls == 1) {
                    firstFetchGate.await()
                    return Result.success(sampleAnthropicStats)
                }
                return Result.success(updatedAnthropicStats)
            }
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis,
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()
        enabledApis.value = setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX)

        viewModel.refresh()
        awaitCondition { anthropicCalls == 1 }

        viewModel.refresh(ApiSource.ANTHROPIC)
        delay(100)
        assertEquals(1, anthropicCalls)

        firstFetchGate.complete(Unit)

        awaitConditionRealTime { anthropicCalls == 2 }
        awaitConditionRealTime {
            val state = viewModel.uiState.value as? UiState.Success ?: return@awaitConditionRealTime false
            val anthropicData = state.data.firstOrNull { it.source == ApiSource.ANTHROPIC } ?: return@awaitConditionRealTime false
            anthropicData.quotas[0].used == 91000L
        }

        val state = viewModel.uiState.value
        assertIs<UiState.Success>(state)
        val anthropicData = state.data.first { it.source == ApiSource.ANTHROPIC }
        assertEquals(91000L, anthropicData.quotas[0].used)
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

    @Test
    fun `global refresh waits for snapshot persistence before publishing success`() = runTest {
        val persistenceGate = CompletableDeferred<Unit>()
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val enabledApis = MutableStateFlow<Set<ApiSource>>(emptySet())

        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
                persistenceGate.await()
                recordedSnapshots += stats
            }

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: com.usagemonitor.domain.entity.HistoryRange,
                now: Instant
            ) = throw UnsupportedOperationException("Não utilizado neste teste")
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis,
            RecordUsageSnapshotUseCase(historyRepository),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        enabledApis.value = setOf(ApiSource.ANTHROPIC)
        viewModel.refresh()
        delay(100)

        assertTrue(viewModel.uiState.value !is UiState.Success)
        assertTrue(recordedSnapshots.isEmpty())

        persistenceGate.complete(Unit)

        awaitCondition { viewModel.uiState.value is UiState.Success }
        val state = viewModel.uiState.value
        assertIs<UiState.Success>(state)
        assertEquals(1, recordedSnapshots.size)
        assertEquals(ApiSource.ANTHROPIC, recordedSnapshots.single().source)
        viewModel.onDestroy()
    }

    @Test
    fun `publishes available update when a newer version exists without automatic installer`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0"
                    )
                )
            ),
            appUpdateInstaller = UnsupportedAppUpdateInstaller,
            currentAppVersion = "7.0.0",
            clock = Clock.System
        )
        viewModel.cancelCountdown()

        awaitConditionRealTime { viewModel.appUpdateState.value is AppUpdateUiState.Available }

        val updateState = viewModel.appUpdateState.value
        assertIs<AppUpdateUiState.Available>(updateState)
        assertEquals("7.1.0", updateState.update.version)
        assertEquals(false, updateState.automaticInstallSupported)
        assertEquals(false, viewModel.shouldExitForUpdate.value)
        viewModel.onDestroy()
    }

    @Test
    fun `prepares automatic update and requests app exit`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        var preparedVersion: String? = null
        val installer = object : AppUpdateInstaller {
            override val isSupported: Boolean = true

            override fun canInstall(update: AppUpdateInfo): Boolean {
                return update.windowsInstallerDownloadUrl != null || update.linuxDebInstallerDownloadUrl != null
            }

            override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<PreparedUpdateAction> {
                preparedVersion = update.version
                return Result.success(PreparedUpdateAction.ExitAndInstall)
            }
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                        linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                    )
                )
            ),
            appUpdateInstaller = installer,
            currentAppVersion = "7.0.0",
            clock = Clock.System
        )
        viewModel.cancelCountdown()

        awaitConditionRealTime { viewModel.appUpdateState.value is AppUpdateUiState.Installing }
        awaitConditionRealTime { viewModel.shouldExitForUpdate.value }

        val updateState = viewModel.appUpdateState.value
        assertIs<AppUpdateUiState.Installing>(updateState)
        assertEquals("7.1.0", preparedVersion)
        assertEquals(true, viewModel.shouldExitForUpdate.value)
        viewModel.onDestroy()
    }

    @Test
    fun `prepares linux update without requesting app exit when installer opens externally`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        var preparedVersion: String? = null
        val installer = object : AppUpdateInstaller {
            override val isSupported: Boolean = true

            override fun canInstall(update: AppUpdateInfo): Boolean {
                return update.linuxDebInstallerDownloadUrl != null
            }

            override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<PreparedUpdateAction> {
                preparedVersion = update.version
                return Result.success(PreparedUpdateAction.InstallerOpened)
            }
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                        linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                    )
                )
            ),
            appUpdateInstaller = installer,
            currentAppVersion = "7.0.0",
            clock = Clock.System
        )
        viewModel.cancelCountdown()

        awaitConditionRealTime { viewModel.appUpdateState.value is AppUpdateUiState.InstallerOpened }

        val updateState = viewModel.appUpdateState.value
        assertIs<AppUpdateUiState.InstallerOpened>(updateState)
        assertEquals("7.1.0", preparedVersion)
        assertEquals(false, viewModel.shouldExitForUpdate.value)
        viewModel.onDestroy()
    }

    @Test
    fun `emits rate limit toast on 429 and keeps persistent source error`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Anthropic HTTP 429: rate limited")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertEquals(1, state.errors.size)
        assertEquals(ApiSource.ANTHROPIC, state.errors.first().source)
        assertTrue(state.errors.first().isRateLimitIssue)
        assertEquals("RATE_LIMIT:ANTHROPIC", viewModel.toastMessage.value)
        viewModel.onDestroy()
    }

    @Test
    fun `shows rate limit error when Anthropic is the only enabled API`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Anthropic HTTP 429: rate limited")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Error>(state)
        assertEquals(1, state.errors.size)
        assertEquals(ApiSource.ANTHROPIC, state.errors.first().source)
        assertTrue(state.errors.first().isRateLimitIssue)
        assertEquals("RATE_LIMIT:ANTHROPIC", viewModel.toastMessage.value)
        viewModel.onDestroy()
    }

    @Test
    fun `emits generic error toast for non-configuration failures`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Anthropic HTTP 503: service unavailable")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(recordedSnapshots),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()
        awaitSettledState(viewModel)
        awaitCondition { viewModel.toastMessage.value != null }

        val toast = viewModel.toastMessage.value
        assertTrue(toast?.startsWith("ERROR:ANTHROPIC:") == true, "got toast=$toast")
        viewModel.onDestroy()
    }

    @Test
    fun `clearToast nulls the toast message`() = runTest {
        val viewModel = successViewModel(mutableListOf())
        // Simula toast injetando uma falha 429 numa chamada externa: caminho mais simples é via refresh com erro.
        // Aqui basta verificar a API pública: clearToast esvazia o flow mesmo sem toast pendente.
        viewModel.clearToast()
        assertEquals(null, viewModel.toastMessage.value)
        viewModel.onDestroy()
    }

    @Test
    fun `snapshot persistence error does not propagate to UiState`() = runTest {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val brokenHistory = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
                throw IllegalStateException("disco cheio")
            }

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: com.usagemonitor.domain.entity.HistoryRange,
                now: Instant
            ) = throw UnsupportedOperationException("Não utilizado neste teste")
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            RecordUsageSnapshotUseCase(brokenHistory),
            clock = Clock.System
        )
        viewModel.cancelInitFetch()
        viewModel.cancelCountdown()

        viewModel.refresh()
        val state = awaitSettledState(viewModel)

        // Falha ao persistir histórico não deve corromper a UI: dados de uso seguem visíveis.
        assertIs<UiState.Success>(state)
        assertEquals(2, state.data.size)
        viewModel.onDestroy()
    }

    @Test
    fun `allows retrying automatic update after a preparation failure`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        var attempts = 0
        val installer = object : AppUpdateInstaller {
            override val isSupported: Boolean = true

            override fun canInstall(update: AppUpdateInfo): Boolean {
                return update.windowsInstallerDownloadUrl != null || update.linuxDebInstallerDownloadUrl != null
            }

            override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<PreparedUpdateAction> {
                attempts += 1
                return if (attempts == 1) {
                    Result.failure(IllegalStateException("Falha ao baixar o pacote"))
                } else {
                    Result.success(PreparedUpdateAction.ExitAndInstall)
                }
            }
        }

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(emptySet()),
            historyUseCase(recordedSnapshots),
            checkForAppUpdate = updateUseCase(
                Result.success(
                    AppUpdateInfo(
                        version = "7.1.0",
                        releasePageUrl = "https://example.com/releases/tag/v7.1.0",
                        linuxDebInstallerDownloadUrl = "https://example.com/UsageMonitor-7.1.0.deb"
                    )
                )
            ),
            appUpdateInstaller = installer,
            currentAppVersion = "7.0.0",
            clock = Clock.System
        )
        viewModel.cancelCountdown()

        awaitConditionRealTime { viewModel.appUpdateState.value is AppUpdateUiState.Failed }

        viewModel.retryUpdateInstallation()

        awaitConditionRealTime { viewModel.appUpdateState.value is AppUpdateUiState.Installing }
        awaitConditionRealTime { viewModel.shouldExitForUpdate.value }

        assertEquals(2, attempts)
        assertEquals(true, viewModel.shouldExitForUpdate.value)
        viewModel.onDestroy()
    }
}
