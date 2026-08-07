package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModelConfig
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelRefreshPersistenceTest : DashboardViewModelTestSupport() {

    private fun noAutoStartConfig() = DashboardViewModelConfig(
        autoStartInitialFetch = true,
        autoStartCountdown = false,
        autoStartUpdateChecks = false
    )

    private fun refuseAllRepos(): DashboardViewModel {
        val refusingAnthropic = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val refusingMiniMax = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val refusingCodex = object : CodexRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        val refusingDeepSeek = object : DeepSeekRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        }
        return DashboardViewModel(
            GetAnthropicUsageUseCase(refusingAnthropic),
            GetMiniMaxUsageUseCase(refusingMiniMax),
            GetCodexUsageUseCase(refusingCodex),
            GetDeepSeekUsageUseCase(refusingDeepSeek),
            defaultEnabledApis(),
            historyUseCase(mutableListOf()),
            clock = Clock.System,
            config = noAutoStartConfig(),
            persistedNextRefreshAt = Clock.System.now() + 10.minutes
        )
    }

    @Test
    fun `skips initial fetch when persisted next refresh is still in the future`() = runTest {
        val viewModel = refuseAllRepos()

        awaitCondition { true }
        assertIs<UiState.Loading>(viewModel.uiState.value)
        viewModel.onDestroy()
    }

    @Test
    fun `honors persisted next refresh as the countdown target`() = runTest {
        val persisted = Clock.System.now() + 7.minutes
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
            defaultEnabledApis(),
            historyUseCase(mutableListOf()),
            clock = Clock.System,
            config = noAutoStartConfig(),
            persistedNextRefreshAt = persisted
        )

        assertEquals(persisted, viewModel.nextRefreshAt.value)
        viewModel.onDestroy()
    }

    @Test
    fun `refresh persists the new scheduled time via callback`() = runTest {
        var lastPersistedInstant: kotlinx.datetime.Instant? = null

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
        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            defaultEnabledApis(),
            historyUseCase(mutableListOf()),
            clock = Clock.System,
            config = noAutoStartConfig(),
            onNextRefreshAtChanged = { instant -> lastPersistedInstant = instant }
        )

        viewModel.refresh()
        awaitSettledState(viewModel)

        assertEquals(viewModel.nextRefreshAt.value, lastPersistedInstant)
        assertFalse(lastPersistedInstant == null)
        viewModel.onDestroy()
    }
}
