package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.repository.AnthropicRepository
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.CodexRepository
import com.usagemonitor.domain.repository.DeepSeekRepository
import com.usagemonitor.domain.repository.MiniMaxRepository
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Recorder que só guarda o que recebeu; a trilha real tem teste próprio. */
private class RecordingBreadcrumbRecorder : BreadcrumbRecorder {
    val steps = mutableListOf<Pair<BreadcrumbCategory, String>>()

    override fun record(category: BreadcrumbCategory, message: String) {
        steps += category to message
    }

    override fun read(limit: Int): List<Breadcrumb> = emptyList()
}

class DashboardViewModelBreadcrumbTest : DashboardViewModelTestSupport() {

    /**
     * A ação que o usuário vai descrever ("cliquei em atualizar e...") tem de
     * estar na trilha, ou o relatório começa depois do começo.
     */
    @Test
    fun `a user requested refresh becomes a use case step`() = runTest {
        val recorder = RecordingBreadcrumbRecorder()
        val viewModel = failingViewModel(recorder)

        viewModel.refresh()
        awaitCondition { recorder.steps.any { it.first == BreadcrumbCategory.USE_CASE } }

        assertTrue(
            recorder.steps.contains(
                BreadcrumbCategory.USE_CASE to "atualização de todas as fontes pedida"
            ),
            recorder.steps.toString()
        )
        viewModel.onDestroy()
    }

    @Test
    fun `refreshing one source names it`() = runTest {
        val recorder = RecordingBreadcrumbRecorder()
        val viewModel = failingViewModel(recorder)

        viewModel.refresh(ApiSource.MINIMAX)
        awaitCondition { recorder.steps.any { it.first == BreadcrumbCategory.USE_CASE } }

        assertTrue(
            recorder.steps.contains(BreadcrumbCategory.USE_CASE to "atualização de MINIMAX pedida"),
            recorder.steps.toString()
        )
        viewModel.onDestroy()
    }

    /**
     * `handleTargetFailure` é o funil único de toda falha de coleta, e é por isso
     * que um ponto de gravação cobre poll silencioso, atualização pedida e
     * recarga de banner.
     */
    @Test
    fun `every failed source becomes an api call step with the sanitized message`() = runTest {
        val recorder = RecordingBreadcrumbRecorder()
        val viewModel = failingViewModel(recorder)

        viewModel.refresh()
        awaitCondition {
            recorder.steps.count { it.first == BreadcrumbCategory.API_CALL } >= 2
        }

        val failures = recorder.steps.filter { it.first == BreadcrumbCategory.API_CALL }.map { it.second }
        assertTrue(failures.any { it.startsWith("ANTHROPIC: falhou — ") }, failures.toString())
        assertTrue(failures.any { it.startsWith("MINIMAX: falhou — ") }, failures.toString())
        viewModel.onDestroy()
    }

    /**
     * A trilha tem 200 linhas de orçamento. "Coleta ok" repetida a cada 10
     * minutos é exatamente o que expulsaria dela o passo que explica a falha —
     * por isso o sucesso não vira passo.
     */
    @Test
    fun `a successful collection writes no step at all`() = runTest {
        val recorder = RecordingBreadcrumbRecorder()
        val snapshots = mutableListOf<ApiUsageStats>()
        val viewModel = successViewModelWith(recorder, snapshots)

        viewModel.refresh()
        awaitCondition { snapshots.size >= 2 }
        settleBackgroundWork()

        // Só o passo do pedido do usuário; nenhuma linha de sucesso.
        assertEquals(
            listOf(BreadcrumbCategory.USE_CASE to "atualização de todas as fontes pedida"),
            recorder.steps
        )
        viewModel.onDestroy()
    }

    private fun failingViewModel(recorder: BreadcrumbRecorder): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Token inválido"))
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("API Key não configurada"))
        }
        return DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(neverCalledCodex()),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(neverCalledDeepSeek()),
            enabledApis = defaultEnabledApis(),
            recordUsageSnapshot = historyUseCase(mutableListOf()),
            clock = Clock.System,
            config = manualRefreshConfig(),
            breadcrumbs = recorder
        )
    }

    private fun successViewModelWith(
        recorder: BreadcrumbRecorder,
        snapshots: MutableList<ApiUsageStats>
    ): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = Result.success(sampleAnthropicStats)
        }
        val minimaxRepo = object : MiniMaxRepository {
            override suspend fun getUsage() = Result.success(sampleMiniMaxStats)
        }
        return DashboardViewModel(
            getAnthropicUsage = GetAnthropicUsageUseCase(anthropicRepo),
            getMiniMaxUsage = GetMiniMaxUsageUseCase(minimaxRepo),
            getCodexUsage = GetCodexUsageUseCase(neverCalledCodex()),
            getDeepSeekUsage = GetDeepSeekUsageUseCase(neverCalledDeepSeek()),
            enabledApis = defaultEnabledApis(),
            recordUsageSnapshot = historyUseCase(snapshots),
            clock = Clock.System,
            config = manualRefreshConfig(),
            breadcrumbs = recorder
        )
    }

    private fun neverCalledCodex() = object : CodexRepository {
        override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
    }

    private fun neverCalledDeepSeek() = object : DeepSeekRepository {
        override suspend fun getUsage() = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
    }
}
