package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
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
class DashboardViewModelTest : DashboardViewModelTestSupport() {

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
    fun `risk summaries are populated after successful fetch when history use case is provided`() = runTest {
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
        val expectedRisk = QuotaRiskSummary(level = UsageRiskLevel.WILL_EXCEED, estimatedExhaustionAt = fixedInstant)
        val report = ApiUsageHistoryReport(
            source = ApiSource.ANTHROPIC,
            range = HistoryRange.LAST_7_DAYS,
            lastUpdatedAt = fixedInstant,
            series = listOf(
                UsageHistorySeries(
                    quotaLabel = "Tokens",
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE,
                    points = emptyList(),
                    currentDisplayUsed = 50000L,
                    currentDisplayTotal = 200000L,
                    deltaDisplayUsed = 50000L,
                    averageDisplayConsumptionPerHour = 100.0,
                    currentPeriodEndAt = fixedInstant,
                    forecast = UsageForecast.InsufficientData,
                    riskSummary = expectedRisk
                )
            )
        )

        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            historyUseCase(mutableListOf()),
            getUsageHistory = getUsageHistoryUseCase(reportsBySource = mapOf(ApiSource.ANTHROPIC to report)),
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        val target = UsageTargetKey.forSource(ApiSource.ANTHROPIC)
        val riskForTarget = state.riskSummaries[target]
        assertEquals(expectedRisk, riskForTarget?.get(QuotaSeriesKey("Tokens", PeriodType.INTERVAL)))
        viewModel.onDestroy()
    }

    @Test
    fun `risk summaries stay empty when history use case is not provided`() = runTest {
        val viewModel = successViewModel(mutableListOf())
        viewModel.refresh()
        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertTrue(state.riskSummaries.isEmpty())
        viewModel.onDestroy()
    }

    @Test
    fun `history read failure does not propagate to UiState`() = runTest {
        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(object : AnthropicRepository {
                override suspend fun getUsage() = Result.success(sampleAnthropicStats)
            }),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            GetCodexUsageUseCase(object : CodexRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            historyUseCase(mutableListOf()),
            getUsageHistory = failingUsageHistoryUseCase(),
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        viewModel.refresh()

        val state = awaitSettledState(viewModel)
        assertIs<UiState.Success>(state)
        assertTrue(state.riskSummaries.isEmpty())
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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        awaitCondition { anthropicCalls >= 1 && minimaxCalls >= 1 }
        awaitSettledState(viewModel)
        val anthropicCallsAfterGlobalRefresh = anthropicCalls
        val minimaxCallsAfterGlobalRefresh = minimaxCalls

        anthropicResult = Result.success(updatedAnthropicStats)
        viewModel.refresh(ApiSource.ANTHROPIC)

        awaitCondition { anthropicCalls >= anthropicCallsAfterGlobalRefresh + 1 }
        awaitCondition {
            val currentState = viewModel.uiState.value as? UiState.Success ?: return@awaitCondition false
            currentState.data
                .firstOrNull { it.source == ApiSource.ANTHROPIC }
                ?.quotas
                ?.firstOrNull()
                ?.used == 75000L
        }
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
    fun `successful account switch updates card identity and failed refresh preserves last account`() = runTest {
        var calls = 0
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val accountB = sampleAnthropicStats.accountContext!!.copy(
            key = sampleAnthropicStats.accountContext!!.key.copy(
                providerAccountId = "anthropic-user-b",
                workspaceId = "anthropic-org-b"
            ),
            email = "account-b@example.com",
            workspaceName = "Org B"
        )
        val statsB = sampleAnthropicStats.copy(accountContext = accountB)
        var anthropicResult: Result<ApiUsageStats> = Result.success(sampleAnthropicStats)
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                calls += 1
                return anthropicResult
            }
        }
        val disabledRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(disabledRepo),
            getCodexUsage = GetCodexUsageUseCase(
                object : CodexRepository {
                    override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
                }
            ),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(
                object : DeepSeekRepository {
                    override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
                }
            ),
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            recordUsageSnapshot = historyUseCase(recordedSnapshots),
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        awaitCondition { calls == 1 && recordedSnapshots.size == 1 }
        anthropicResult = Result.success(statsB)
        viewModel.refresh(ApiSource.ANTHROPIC)
        awaitCondition { calls == 2 && recordedSnapshots.size == 2 }
        awaitCondition {
            val current = viewModel.uiState.value as? UiState.Success
            current?.data?.singleOrNull()?.accountContext?.email == "account-b@example.com"
        }
        var state = viewModel.uiState.value as UiState.Success
        assertEquals("account-b@example.com", state.data.single().accountContext?.email)

        anthropicResult = Result.failure(IllegalStateException("login ainda em andamento"))
        viewModel.refresh(ApiSource.ANTHROPIC)
        awaitCondition { calls == 3 }
        awaitCondition { viewModel.refreshingSources.value.isEmpty() }
        state = viewModel.uiState.value as UiState.Success

        assertEquals("account-b@example.com", state.data.single().accountContext?.email)
        assertEquals(2, recordedSnapshots.size)
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
            clock = Clock.System,
            config = manualRefreshConfig()
        )
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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

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
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        val state = awaitSettledState(viewModel)

        // Falha ao persistir histórico não deve corromper a UI: dados de uso seguem visíveis.
        assertIs<UiState.Success>(state)
        assertEquals(2, state.data.size)
        viewModel.onDestroy()
    }

    @Test
    fun `collects and refreshes multiple Anthropic profiles independently`() = runTest {
        val profileA = AnthropicProfileRef("profile-a", "Pessoal")
        val profileB = AnthropicProfileRef("profile-b", "Empresa")
        val calls = mutableListOf<String>()
        val snapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(IllegalStateException("Perfil obrigatório"))
            }

            override suspend fun getUsage(profile: AnthropicProfileRef): Result<ApiUsageStats> {
                calls += profile.id
                val account = sampleAnthropicStats.accountContext!!.copy(
                    key = sampleAnthropicStats.accountContext!!.key.copy(
                        providerAccountId = "account-${profile.id}",
                        workspaceId = "workspace-${profile.id}"
                    ),
                    email = "${profile.id}@example.com"
                )
                return Result.success(
                    sampleAnthropicStats.copy(
                        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, profile.id),
                        profileLabel = profile.label,
                        accountContext = account
                    )
                )
            }
        }
        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(object : MiniMaxRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            getCodexUsage = GetCodexUsageUseCase(object : CodexRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(object : DeepSeekRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            recordUsageSnapshot = historyUseCase(snapshots),
            anthropicProfiles = MutableStateFlow(listOf(profileA, profileB)),
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        awaitCondition { snapshots.size == 2 }
        val state = viewModel.uiState.value as UiState.Success
        assertEquals(setOf("profile-a", "profile-b"), state.data.map { it.targetKey.profileId }.toSet())
        assertEquals(setOf("profile-a", "profile-b"), calls.toSet())

        viewModel.refresh(UsageTargetKey(ApiSource.ANTHROPIC, profileA.id))
        awaitCondition { calls.count { it == profileA.id } == 2 }
        assertEquals(1, calls.count { it == profileB.id })
        viewModel.onDestroy()
    }

    @Test
    fun `boots from cache instead of Loading when a scheduled refresh is still pending`() = runTest {
        var anthropicCalled = false
        var minimaxCalled = false
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                anthropicCalled = true
                return Result.failure(Exception("Não deve ser chamado: timer ainda pendente"))
            }
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                minimaxCalled = true
                return Result.failure(Exception("Não deve ser chamado: timer ainda pendente"))
            }
        }
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val deepSeekRepo = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val fixedClock = object : Clock {
            override fun now(): Instant = fixedInstant
        }
        val cachedStats = listOf(sampleAnthropicStats, sampleMiniMaxStats)
        val cacheUseCase = com.usagemonitor.domain.usecase.GetCachedDashboardStatsUseCase(
            object : com.usagemonitor.domain.repository.DashboardCacheRepository {
                override suspend fun saveSnapshot(stats: List<ApiUsageStats>, capturedAt: Instant) = Unit
                override suspend fun loadSnapshot(): List<ApiUsageStats> = cachedStats
            }
        )

        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis = defaultEnabledApis(),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getCachedDashboardStats = cacheUseCase,
            clock = fixedClock,
            config = manualRefreshConfig(),
            persistedNextRefreshAt = fixedInstant + with(kotlin.time.Duration.Companion) { 5.minutes }
        )

        awaitCondition { viewModel.uiState.value is UiState.Success }
        val state = viewModel.uiState.value as UiState.Success
        assertEquals(2, state.data.size)
        assertTrue(!anthropicCalled)
        assertTrue(!minimaxCalled)
        viewModel.onDestroy()
    }

}
