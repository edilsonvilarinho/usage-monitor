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
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.DashboardViewModelConfig
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/** Publicação incremental e prazo por fonte (issue #269, Fase 3). */
class DashboardViewModelIncrementalTest : DashboardViewModelTestSupport() {

    @Test
    fun `a slow source does not hold the others off the screen`() = runTest {
        val anthropicGate = CompletableDeferred<Result<ApiUsageStats>>()
        val unused = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        val viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(object : AnthropicRepository {
                override suspend fun getUsage() = anthropicGate.await()
            }),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository {
                override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
            }),
            GetCodexUsageUseCase(object : CodexRepository { override suspend fun getUsage() = unused }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository { override suspend fun getUsage() = unused }),
            defaultEnabledApis(),
            historyUseCase(mutableListOf()),
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.data?.isNotEmpty() == true }

        val partial = assertIs<UiState.Success>(viewModel.uiState.value)
        assertEquals(listOf(ApiSource.MINIMAX), partial.data.map { it.source })

        anthropicGate.complete(Result.success(sampleAnthropicStats))
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.data?.size == 2 }
        viewModel.onDestroy()
    }

    /**
     * `awaitSettledState` espera a coleta inteira, não o primeiro `Success`. O
     * MiniMax só falha depois de a Anthropic estar na tela, e o estado parcial,
     * sem o erro, é exatamente o que o helper devolvia — o CI da PR #321 pegou
     * isso em `classifies MiniMax inactive plan for persistent warning without toast`.
     */
    @Test
    fun `settled state waits for the slower source instead of the first publication`() = runTest {
        val unused = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        lateinit var viewModel: DashboardViewModel
        viewModel = DashboardViewModel(
            GetAnthropicUsageUseCase(object : AnthropicRepository {
                override suspend fun getUsage() = Result.success(sampleAnthropicStats)
            }),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository {
                override suspend fun getUsage(): Result<ApiUsageStats> {
                    while ((viewModel.uiState.value as? UiState.Success)?.data.isNullOrEmpty()) delay(10)
                    return Result.failure(IllegalStateException("Chave da API MiniMax não configurada."))
                }
            }),
            GetCodexUsageUseCase(object : CodexRepository { override suspend fun getUsage() = unused }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository { override suspend fun getUsage() = unused }),
            defaultEnabledApis(),
            historyUseCase(mutableListOf()),
            clock = Clock.System,
            config = manualRefreshConfig()
        )

        viewModel.refresh()
        val state = assertIs<UiState.Success>(awaitSettledState(viewModel))

        assertEquals(listOf(ApiSource.ANTHROPIC), state.data.map { it.source })
        assertEquals(listOf(ApiSource.MINIMAX), state.errors.map { it.source })
        viewModel.onDestroy()
    }

    @Test
    fun `Anthropic and Antigravity get their own time budget`() {
        val config = DashboardViewModelConfig()

        assertEquals(45.seconds, config.timeoutFor(ApiSource.ANTHROPIC))
        assertEquals(50.seconds, config.timeoutFor(ApiSource.ANTIGRAVITY))
        assertEquals(config.perSourceTimeout, config.timeoutFor(ApiSource.MINIMAX))
    }
}
