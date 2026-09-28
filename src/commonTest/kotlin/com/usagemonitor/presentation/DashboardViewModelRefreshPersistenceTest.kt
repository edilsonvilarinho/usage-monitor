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
        val lastPersistedInstant = java.util.concurrent.atomic.AtomicReference<kotlinx.datetime.Instant?>(null)

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
            onNextRefreshAtChanged = { instant -> lastPersistedInstant.set(instant) }
        )

        viewModel.refresh()
        awaitSettledState(viewModel)
        // A contagem é publicada depois da tela (issue #269): é o fim da coleta,
        // com todos os alvos já marcados, que decide o próximo prazo.
        awaitConditionRealTime { lastPersistedInstant.get() != null }

        assertEquals(viewModel.nextRefreshAt.value, lastPersistedInstant.get())
        assertFalse(lastPersistedInstant.get() == null)
        viewModel.onDestroy()
    }

    /**
     * Issue #331: o prazo inicial nunca é gravado, e com o relógio parado entre o
     * construtor e a coleta — no Windows ele avança em passos de até ~15 ms — o
     * prazo da coleta empatava com ele e a gravação era pulada. O relógio
     * congelado reproduz o empate sempre, em vez de só sob carga.
     */
    @Test
    fun `first fetch persists the schedule even when it ties with the initial one`() = runTest {
        val frozen = object : Clock {
            private val instant = Clock.System.now()
            override fun now() = instant
        }
        val persisted = java.util.concurrent.atomic.AtomicReference<kotlinx.datetime.Instant?>(null)
        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(object : AnthropicRepository {
                override suspend fun getUsage() = Result.success(sampleAnthropicStats)
            }),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository {
                override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
            }),
            GetCodexUsageUseCase(object : CodexRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository {
                override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
            }),
            defaultEnabledApis(),
            historyUseCase(mutableListOf()),
            clock = frozen,
            config = noAutoStartConfig(),
            onNextRefreshAtChanged = { instant -> persisted.set(instant) }
        )
        val initial = viewModel.nextRefreshAt.value

        awaitConditionRealTime { persisted.get() != null }

        assertEquals(initial, persisted.get())
        viewModel.onDestroy()
    }
}
