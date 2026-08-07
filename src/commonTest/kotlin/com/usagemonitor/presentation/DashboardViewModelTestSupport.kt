package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
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
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModelConfig
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

abstract class DashboardViewModelTestSupport {

    protected val fixedInstant = Instant.parse("2025-01-01T12:00:00Z")

    protected val sampleAnthropicStats = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        apiName = "Anthropic",
        accountContext = UsageAccountContext(
            key = UsageAccountKey(
                source = ApiSource.ANTHROPIC,
                providerAccountId = "anthropic-user-a",
                workspaceId = "anthropic-org-a"
            ),
            email = "account-a@example.com",
            workspaceName = "Org A"
        ),
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

    protected val sampleMiniMaxStats = ApiUsageStats(
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

    protected fun defaultEnabledApis(): MutableStateFlow<Set<ApiSource>> {
        return MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))
    }

    protected fun historyUseCase(recordedSnapshots: MutableList<ApiUsageStats>): RecordUsageSnapshotUseCase {
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
                recordedSnapshots += stats
            }

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: com.usagemonitor.domain.entity.HistoryRange,
                now: Instant
            ) = throw UnsupportedOperationException("Não utilizado neste teste")
        }
        return RecordUsageSnapshotUseCase(historyRepository)
    }

    protected fun getUsageHistoryUseCase(
        reportsBySource: Map<ApiSource, ApiUsageHistoryReport> = emptyMap(),
        onRequest: (ApiSource) -> Unit = {}
    ): GetUsageHistoryUseCase {
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ): ApiUsageHistoryReport {
                onRequest(source)
                return reportsBySource[source] ?: ApiUsageHistoryReport(
                    source = source,
                    range = range,
                    lastUpdatedAt = null,
                    series = emptyList()
                )
            }
        }
        return GetUsageHistoryUseCase(historyRepository)
    }

    protected fun failingUsageHistoryUseCase(): GetUsageHistoryUseCase {
        val historyRepository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit

            override suspend fun getHistoryReport(
                source: ApiSource,
                range: HistoryRange,
                now: Instant
            ): ApiUsageHistoryReport {
                throw IllegalStateException("Falha simulada de leitura de histórico")
            }
        }
        return GetUsageHistoryUseCase(historyRepository)
    }

    protected fun updateUseCase(block: suspend () -> Result<AppUpdateInfo?>): CheckForAppUpdateUseCase {
        val repository = object : AppUpdateRepository {
            override suspend fun getLatestAvailableUpdate(currentVersion: String): Result<AppUpdateInfo?> {
                return block()
            }
        }
        return CheckForAppUpdateUseCase(repository)
    }

    protected fun virtualTimeConfig(testScheduler: TestCoroutineScheduler) = DashboardViewModelConfig(
        workerDispatcher = StandardTestDispatcher(testScheduler),
        updateCheckIntervalWhileRunning = 365.days,
        autoStartInitialFetch = false,
        autoStartCountdown = false
    )

    protected fun periodicUpdateConfig(testScheduler: TestCoroutineScheduler) = DashboardViewModelConfig(
        workerDispatcher = StandardTestDispatcher(testScheduler),
        autoStartInitialFetch = false,
        autoStartCountdown = false
    )

    protected fun manualRefreshConfig(
        workerDispatcher: CoroutineDispatcher = Dispatchers.Default
    ) = DashboardViewModelConfig(
        workerDispatcher = workerDispatcher,
        autoStartInitialFetch = false,
        autoStartCountdown = false,
        autoStartUpdateChecks = false
    )

    protected fun successViewModel(recordedSnapshots: MutableList<ApiUsageStats>): DashboardViewModel {
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
            clock = Clock.System,
            config = manualRefreshConfig()
        )
        return vm
    }

    protected fun failureViewModel(recordedSnapshots: MutableList<ApiUsageStats>): DashboardViewModel {
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
            clock = Clock.System,
            config = manualRefreshConfig()
        )
        return vm
    }

    protected fun partialSuccessViewModel(recordedSnapshots: MutableList<ApiUsageStats>): DashboardViewModel {
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
            clock = Clock.System,
            config = manualRefreshConfig()
        )
        return vm
    }

    protected suspend fun awaitSettledState(viewModel: DashboardViewModel): UiState {
        repeat(200) {
            val state = viewModel.uiState.value
            if (state !is UiState.Loading) {
                return state
            }
            pauseForBackgroundWork()
        }
        return viewModel.uiState.value
    }

    protected suspend fun awaitCondition(predicate: () -> Boolean) {
        repeat(200) {
            if (predicate()) {
                return
            }
            pauseForBackgroundWork()
        }
        assertTrue(predicate(), "Condition not met within coroutine timeout")
    }

    protected suspend fun awaitConditionRealTime(predicate: () -> Boolean) {
        repeat(250) {
            if (predicate()) {
                return
            }
            pauseForBackgroundWork()
        }
        assertTrue(predicate(), "Condition not met within real-time timeout")
    }

    private suspend fun pauseForBackgroundWork() {
        yield()
        Thread.sleep(20)
    }
}
