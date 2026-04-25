package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val defaultEnabledApis = MutableStateFlow(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX))

    private val fixedInstant = Instant.parse("2025-01-01T12:00:00Z")

    private val sampleAnthropicStats = ApiUsageStats(
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

    private fun successViewModel(): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            defaultEnabledApis
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    private fun failureViewModel(): DashboardViewModel {
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
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            defaultEnabledApis
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    private fun partialSuccessViewModel(): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(
                Exception("Credenciais expiradas")
            )
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        val vm = DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            defaultEnabledApis
        )
        vm.cancelInitFetch()
        vm.cancelCountdown()
        return vm
    }

    @Test
    fun `initial state is Loading`() {
        val viewModel = successViewModel()
        assertIs<UiState>(viewModel.uiState.value)
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to Success when both APIs succeed`() = runTest {
        val viewModel = successViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertIs<UiState.Success>(state)
        assertEquals(2, state.data.size)
        viewModel.onDestroy()
    }

    @Test
    fun `transitions to Error when all APIs fail`() = runTest {
        val viewModel = failureViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertIs<UiState.Error>(state)
        assert(state.message.contains("Anthropic"))
        assert(state.message.contains("MiniMax"))
        viewModel.onDestroy()
    }

    @Test
    fun `shows partial Success when only one API fails`() = runTest {
        val viewModel = partialSuccessViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertIs<UiState.Success>(state)
        assertEquals(1, state.data.size)
        assertEquals("MiniMax", state.data[0].apiName)
        viewModel.onDestroy()
    }

    @Test
    fun `Success state contains correct API data`() = runTest {
        val viewModel = successViewModel()
        advanceUntilIdle()
        val state = viewModel.uiState.value as UiState.Success
        val anthropicData = state.data.first { it.apiName == "Anthropic" }
        val minimaxData = state.data.first { it.apiName == "MiniMax" }
        assertEquals(50000L, anthropicData.quotas[0].used)
        assertEquals(2223L, minimaxData.quotas[0].used)
        viewModel.onDestroy()
    }
}
