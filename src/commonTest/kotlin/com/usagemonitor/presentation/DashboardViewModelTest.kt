package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
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
import com.usagemonitor.presentation.viewmodel.DashboardViewModelConfig
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
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
    fun `classifies MiniMax missing API key for persistent warning`() = runTest {
        val recordedSnapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                IllegalStateException("Chave da API MiniMax não configurada.")
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
        assertTrue(state.errors.first().isMiniMaxApiKeyIssue)
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
        // Os dois perfis são coletados em paralelo: um `MutableList` aqui é
        // gravado por duas coroutines e percorrido pela thread do teste ao mesmo
        // tempo, o que perdia entradas ou estourava `ConcurrentModificationException`.
        // O `StateFlow` publica uma lista imutável a cada `update` atômico, então
        // ler e escrever ao mesmo tempo deixa de ser um problema.
        val calls = MutableStateFlow<List<String>>(emptyList())
        val snapshots = mutableListOf<ApiUsageStats>()
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(IllegalStateException("Perfil obrigatório"))
            }

            override suspend fun getUsage(profile: AnthropicProfileRef): Result<ApiUsageStats> {
                calls.update { previous -> previous + profile.id }
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
        assertEquals(setOf("profile-a", "profile-b"), calls.value.toSet())

        viewModel.refresh(UsageTargetKey(ApiSource.ANTHROPIC, profileA.id))
        awaitCondition { calls.value.count { it == profileA.id } == 2 }
        assertEquals(1, calls.value.count { it == profileB.id })
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

    @Test
    fun `restores Codex five hour and weekly quotas from cache before network`() = runTest {
        var codexCalls = 0
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                codexCalls += 1
                return Result.failure(Exception("Não deveria consultar a rede"))
            }
        }
        val fixedClock = object : Clock {
            override fun now(): Instant = fixedInstant
        }
        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(failingAnthropicRepository()),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(failingMiniMaxRepository()),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(failingDeepSeekRepository()),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX)),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getCachedDashboardStats = cachedStatsUseCase(listOf(sampleCodexStats)),
            clock = fixedClock,
            config = manualRefreshConfig(),
            persistedNextRefreshAt = fixedInstant + with(kotlin.time.Duration.Companion) { 5.minutes }
        )

        awaitCondition { viewModel.uiState.value is UiState.Success }
        val state = viewModel.uiState.value as UiState.Success
        val codex = state.data.single()
        assertEquals(ApiSource.CODEX, codex.source)
        assertEquals(listOf("Codex 5h", "Codex 7d"), codex.quotas.map { it.label })
        assertEquals(listOf(23L, 11L), codex.quotas.map { it.used })
        assertEquals("codex-user-a", codex.accountContext?.key?.providerAccountId)
        assertEquals(0, codexCalls)
        viewModel.onDestroy()
    }

    @Test
    fun `new Codex response replaces the cached five hour and weekly quotas`() = runTest {
        val freshCodexStats = sampleCodexStats.copy(
            quotas = listOf(
                sampleCodexStats.quotas[0].copy(used = 44L),
                sampleCodexStats.quotas[1].copy(used = 55L)
            )
        )
        var codexCalls = 0
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                codexCalls += 1
                return Result.success(freshCodexStats)
            }
        }
        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(failingAnthropicRepository()),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(failingMiniMaxRepository()),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(failingDeepSeekRepository()),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX)),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getCachedDashboardStats = cachedStatsUseCase(listOf(sampleCodexStats)),
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        awaitCondition { viewModel.uiState.value is UiState.Success }
        viewModel.refresh()
        awaitCondition {
            codexCalls == 1 &&
                (viewModel.uiState.value as? UiState.Success)
                    ?.data
                    ?.singleOrNull()
                    ?.quotas
                    ?.map { it.used } == listOf(44L, 55L)
        }

        val state = viewModel.uiState.value as UiState.Success
        val codex = state.data.single()
        assertEquals(listOf(PeriodType.INTERVAL, PeriodType.WEEKLY), codex.quotas.map { it.periodType })
        assertEquals("codex-user-a", codex.accountContext?.key?.providerAccountId)
        viewModel.onDestroy()
    }

    @Test
    fun `failed Codex target refresh preserves the cached quotas`() = runTest {
        var codexCalls = 0
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                codexCalls += 1
                return Result.failure(Exception("Sessão do Codex indisponível"))
            }
        }
        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(failingAnthropicRepository()),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(failingMiniMaxRepository()),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(failingDeepSeekRepository()),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX)),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getCachedDashboardStats = cachedStatsUseCase(listOf(sampleCodexStats)),
            clock = Clock.System,
            config = manualRefreshConfig(),
            persistedNextRefreshAt = fixedInstant + with(kotlin.time.Duration.Companion) { 5.minutes }
        )

        awaitCondition { viewModel.uiState.value is UiState.Success }
        viewModel.refresh(UsageTargetKey.forSource(ApiSource.CODEX))
        awaitCondition {
            codexCalls == 1 &&
                (viewModel.uiState.value as? UiState.Success)
                    ?.data
                    ?.singleOrNull()
                    ?.notices
                    ?.contains(ApiUsageNotice.SOURCE_UNSTABLE) == true
        }

        val state = viewModel.uiState.value as UiState.Success
        val codex = state.data.single()
        assertEquals(listOf(23L, 11L), codex.quotas.map { it.used })
        assertEquals(listOf("Codex 5h", "Codex 7d"), codex.quotas.map { it.label })
        assertTrue(ApiUsageNotice.SOURCE_UNSTABLE in codex.notices)
        viewModel.onDestroy()
    }

    @Test
    fun `degraded Codex response does not overwrite complete cached quotas`() = runTest {
        var codexCalls = 0
        val degradedStats = sampleCodexStats.copy(
            quotas = listOf(
                sampleCodexStats.quotas.first().copy(
                    label = "Codex atual",
                    periodType = PeriodType.REPORTED
                )
            )
        )
        val codexRepo = object : CodexRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                codexCalls += 1
                return Result.success(degradedStats)
            }
        }
        val viewModel = DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(failingAnthropicRepository()),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(failingMiniMaxRepository()),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(failingDeepSeekRepository()),
            enabledApis = MutableStateFlow(setOf(ApiSource.CODEX)),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getCachedDashboardStats = cachedStatsUseCase(listOf(sampleCodexStats)),
            clock = Clock.System,
            config = manualRefreshConfig(),
            persistedNextRefreshAt = fixedInstant + with(kotlin.time.Duration.Companion) { 5.minutes }
        )

        awaitCondition { viewModel.uiState.value is UiState.Success }
        viewModel.refresh(UsageTargetKey.forSource(ApiSource.CODEX))
        awaitCondition {
            codexCalls == 1 &&
                (viewModel.uiState.value as? UiState.Success)
                    ?.data
                    ?.singleOrNull()
                    ?.notices
                    ?.contains(ApiUsageNotice.SOURCE_UNSTABLE) == true
        }

        val state = viewModel.uiState.value as UiState.Success
        val codex = state.data.single()
        assertEquals(listOf("Codex 5h", "Codex 7d"), codex.quotas.map { it.label })
        assertEquals(listOf(23L, 11L), codex.quotas.map { it.used })
        assertTrue(ApiUsageNotice.SOURCE_UNSTABLE in codex.notices)
        viewModel.onDestroy()
    }

    @Test
    fun `cache restore brings the usage projection back with it`() = runTest {
        val expectedRisk = QuotaRiskSummary(
            level = UsageRiskLevel.WILL_EXCEED,
            estimatedExhaustionAt = fixedInstant
        )
        val viewModel = cacheOnlyViewModel(
            cachedStats = listOf(sampleAnthropicStats),
            getUsageHistory = getUsageHistoryUseCase(
                reportsBySource = mapOf(ApiSource.ANTHROPIC to riskReport(expectedRisk))
            ),
            persistedNextRefreshAt = fixedInstant + with(kotlin.time.Duration.Companion) { 5.minutes }
        )

        awaitCondition {
            val state = viewModel.uiState.value
            state is UiState.Success && state.riskSummaries.isNotEmpty()
        }

        val state = viewModel.uiState.value as UiState.Success
        val risks = state.riskSummaries[anthropicTarget]
        assertEquals(expectedRisk, risks?.get(QuotaSeriesKey("Tokens", PeriodType.INTERVAL)))
        viewModel.onDestroy()
    }

    @Test
    fun `boots from cache even when the scheduled refresh already expired`() = runTest {
        val fetchGate = CompletableDeferred<Unit>()
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
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis = defaultEnabledApis(),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getCachedDashboardStats = cachedStatsUseCase(listOf(sampleAnthropicStats, sampleMiniMaxStats)),
            clock = Clock.System,
            // Coleta inicial ligada e nenhum ciclo pendente: é o caso de quem
            // deixou o app fechado por mais de um poll.
            config = DashboardViewModelConfig(
                autoStartInitialFetch = true,
                autoStartCountdown = false,
                autoStartUpdateChecks = false
            ),
            persistedNextRefreshAt = null
        )

        // A rede ainda está presa no portão: o que pintar aqui só pode ter vindo do disco.
        awaitCondition { viewModel.uiState.value is UiState.Success }
        val cachedState = viewModel.uiState.value as UiState.Success
        assertEquals(2, cachedState.data.size)

        fetchGate.complete(Unit)
        awaitCondition { viewModel.uiState.value is UiState.Success }
        viewModel.onDestroy()
    }

    @Test
    fun `fetched projection wins over the one restored from cache`() = runTest {
        val restoredRisk = QuotaRiskSummary(
            level = UsageRiskLevel.ON_TRACK,
            estimatedExhaustionAt = null
        )
        val fetchedRisk = QuotaRiskSummary(
            level = UsageRiskLevel.WILL_EXCEED,
            estimatedExhaustionAt = fixedInstant
        )
        val historyCalls = MutableStateFlow(0)
        val restoreGate = CompletableDeferred<Unit>()
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ): ApiUsageHistoryReport {
                // A primeira chamada é a do restore: o teste só solta a coleta
                // depois de vê-la registrada, então a ordem é determinística.
                val call = historyCalls.updateAndGet { current -> current + 1 }
                if (call == 1) {
                    restoreGate.await()
                    return riskReport(restoredRisk)
                }
                return riskReport(fetchedRisk)
            }
        }

        val viewModel = cacheOnlyViewModel(
            cachedStats = listOf(sampleAnthropicStats),
            getUsageHistory = com.usagemonitor.domain.usecase.GetUsageHistoryUseCase(historyRepository),
            persistedNextRefreshAt = null,
            anthropicUsage = Result.success(sampleAnthropicStats)
        )

        awaitCondition { historyCalls.value == 1 }

        viewModel.refresh()
        val seriesKey = QuotaSeriesKey("Tokens", PeriodType.INTERVAL)
        awaitCondition {
            val state = viewModel.uiState.value as? UiState.Success
            state?.riskSummaries?.get(anthropicTarget)?.get(seriesKey) == fetchedRisk
        }

        // O restore termina agora, com um cálculo mais velho que o da coleta.
        restoreGate.complete(Unit)
        settleBackgroundWork()

        val state = viewModel.uiState.value as UiState.Success
        assertEquals(fetchedRisk, state.riskSummaries[anthropicTarget]?.get(seriesKey))
        viewModel.onDestroy()
    }

    private val anthropicTarget = UsageTargetKey.forSource(ApiSource.ANTHROPIC)

    private fun failingAnthropicRepository(): AnthropicRepository {
        return object : AnthropicRepository {
            override suspend fun getUsage() =
                Result.failure<ApiUsageStats>(Exception("Anthropic não deveria ser consultado"))
        }
    }

    private fun failingMiniMaxRepository(): MiniMaxRepository {
        return object : MiniMaxRepository {
            override suspend fun getUsage() =
                Result.failure<ApiUsageStats>(Exception("MiniMax não deveria ser consultado"))
        }
    }

    private fun failingDeepSeekRepository(): DeepSeekRepository {
        return object : DeepSeekRepository {
            override suspend fun getUsage() =
                Result.failure<ApiUsageStats>(Exception("DeepSeek não deveria ser consultado"))
        }
    }

    private fun riskReport(risk: QuotaRiskSummary): ApiUsageHistoryReport {
        return ApiUsageHistoryReport(
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
                    riskSummary = risk
                )
            )
        )
    }

    private fun cachedStatsUseCase(
        stats: List<ApiUsageStats>
    ): com.usagemonitor.domain.usecase.GetCachedDashboardStatsUseCase {
        return com.usagemonitor.domain.usecase.GetCachedDashboardStatsUseCase(
            object : com.usagemonitor.domain.repository.DashboardCacheRepository {
                override suspend fun saveSnapshot(stats: List<ApiUsageStats>, capturedAt: Instant) = Unit
                override suspend fun loadSnapshot(): List<ApiUsageStats> = stats
            }
        )
    }

    /** ViewModel sem coleta automática, alimentado só pelo cache de disco. */
    private fun cacheOnlyViewModel(
        cachedStats: List<ApiUsageStats>,
        getUsageHistory: com.usagemonitor.domain.usecase.GetUsageHistoryUseCase,
        persistedNextRefreshAt: Instant?,
        anthropicUsage: Result<ApiUsageStats> = Result.failure(Exception("Não deve ser chamado"))
    ): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> = anthropicUsage
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
        return DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(codexRepo),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(deepSeekRepo),
            enabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            getUsageHistory = getUsageHistory,
            getCachedDashboardStats = cachedStatsUseCase(cachedStats),
            clock = Clock.System,
            config = manualRefreshConfig(),
            persistedNextRefreshAt = persistedNextRefreshAt
        )
    }

}
