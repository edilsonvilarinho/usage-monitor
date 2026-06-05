package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.DashboardToast
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelToastTest : DashboardViewModelTestSupport() {

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
        assertEquals(DashboardToast.RateLimit(ApiSource.ANTHROPIC), viewModel.toastMessage.value)
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
        assertEquals(DashboardToast.RateLimit(ApiSource.ANTHROPIC), viewModel.toastMessage.value)
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
        assertTrue(toast is DashboardToast.ApiError, "got toast=$toast")
        assertEquals(ApiSource.ANTHROPIC, toast.source)
        assertTrue(toast.message.contains("Anthropic HTTP 503"), "got toast=$toast")
        viewModel.onDestroy()
    }

    @Test
    fun `clearToast nulls the toast message`() = runTest {
        val viewModel = successViewModel(mutableListOf())
        viewModel.clearToast()
        assertEquals(null, viewModel.toastMessage.value)
        viewModel.onDestroy()
    }
}
