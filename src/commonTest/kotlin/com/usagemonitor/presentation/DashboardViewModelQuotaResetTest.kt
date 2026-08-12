package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val STARTED_AT = Instant.parse("2026-08-11T20:00:00Z")

/** Sentinela que o mapper da Anthropic usa quando `resets_at` vem nulo. */
private val UNKNOWN_RESET_AT = Instant.parse("2100-01-01T00:00:00Z")

/**
 * Issue #36: o reset só era percebido quando a API era chamada de novo.
 *
 * Tudo aqui roda em tempo virtual: o relógio do ViewModel é derivado do
 * `TestCoroutineScheduler`, então avançar a espera do laço e avançar o relógio
 * que decide o vencimento são o mesmo gesto — sem isso o teste esperaria os dez
 * minutos de verdade.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelQuotaResetTest : DashboardViewModelTestSupport() {

    private class VirtualClock(
        private val origin: Instant,
        private val scheduler: TestCoroutineScheduler
    ) : Clock {
        override fun now(): Instant = origin + scheduler.currentTime.milliseconds
    }

    private fun anthropicStatsWithReset(
        resetsAt: Instant,
        hasKnownResetAt: Boolean = true
    ): ApiUsageStats {
        return ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            apiName = "Anthropic",
            quotas = listOf(
                QuotaInfo(
                    label = "Claude 5h",
                    used = 100L,
                    total = 100L,
                    periodEndAt = resetsAt,
                    hasKnownResetAt = hasKnownResetAt,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )
    }

    private fun viewModelWatchingResets(
        scheduler: TestCoroutineScheduler,
        fetchCount: AtomicInteger,
        statsProvider: () -> ApiUsageStats,
        isAppVisible: MutableStateFlow<Boolean> = MutableStateFlow(true),
        pollInterval: Duration = 1.days
    ): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                fetchCount.incrementAndGet()
                return Result.success(statsProvider())
            }
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
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(minimaxRepo),
            GetCodexUsageUseCase(codexRepo),
            GetDeepSeekUsageUseCase(deepSeekRepo),
            MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            historyUseCase(mutableListOf()),
            clock = VirtualClock(STARTED_AT, scheduler),
            isAppVisible = isAppVisible,
            config = DashboardViewModelConfig(
                workerDispatcher = StandardTestDispatcher(scheduler),
                pollInterval = pollInterval,
                quotaResetGrace = Duration.ZERO,
                autoStartInitialFetch = true,
                autoStartCountdown = true,
                autoStartUpdateChecks = false
            )
        )
    }

    @Test
    fun `crossing the quota reset collects before the poll interval`() = runTest {
        val fetchCount = AtomicInteger(0)
        val resetsAt = STARTED_AT + 2.minutes
        // A fonte segue devolvendo o mesmo reset já vencido: é o cenário do
        // print da issue, e serve de guarda contra o laço bater sem parar.
        val viewModel = viewModelWatchingResets(
            scheduler = testScheduler,
            fetchCount = fetchCount,
            statsProvider = { anthropicStatsWithReset(resetsAt) }
        )

        runCurrent()
        assertEquals(1, fetchCount.get(), "A coleta inicial não aconteceu")

        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(2, fetchCount.get(), "O reset não disparou a coleta")

        // Um reset já vencido não pode virar alvo de novo: o poll é de um dia.
        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(2, fetchCount.get(), "O reset vencido virou alvo repetido")

        viewModel.onDestroy()
    }

    @Test
    fun `the reset wake up runs with the window minimized`() = runTest {
        val fetchCount = AtomicInteger(0)
        val resetsAt = STARTED_AT + 2.minutes
        // Minimizada desde o início: é justamente com a janela escondida que o
        // card ficava congelado no valor da janela anterior.
        val viewModel = viewModelWatchingResets(
            scheduler = testScheduler,
            fetchCount = fetchCount,
            statsProvider = { anthropicStatsWithReset(resetsAt) },
            isAppVisible = MutableStateFlow(false)
        )

        runCurrent()
        assertEquals(1, fetchCount.get())

        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(2, fetchCount.get(), "O reset não coletou com a janela minimizada")

        viewModel.onDestroy()
    }

    @Test
    fun `the poll cycle still waits for the window to become visible`() = runTest {
        val fetchCount = AtomicInteger(0)
        val isAppVisible = MutableStateFlow(false)
        // Sem reset conhecido não há despertar antecipado: só o ciclo normal.
        val viewModel = viewModelWatchingResets(
            scheduler = testScheduler,
            fetchCount = fetchCount,
            statsProvider = { anthropicStatsWithReset(UNKNOWN_RESET_AT, hasKnownResetAt = false) },
            isAppVisible = isAppVisible,
            pollInterval = 2.minutes
        )

        runCurrent()
        assertEquals(1, fetchCount.get())

        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(1, fetchCount.get(), "O ciclo de poll coletou com a janela minimizada")

        isAppVisible.value = true
        runCurrent()
        assertEquals(2, fetchCount.get(), "O ciclo de poll não retomou ao reabrir a janela")

        viewModel.onDestroy()
    }

    @Test
    fun `a quota without a known reset never becomes a wake up target`() = runTest {
        val fetchCount = AtomicInteger(0)
        // Reset desconhecido, mas com `periodEndAt` logo ali: sem a guarda do
        // `hasKnownResetAt` o sentinela do mapper viraria alvo de agendamento.
        val viewModel = viewModelWatchingResets(
            scheduler = testScheduler,
            fetchCount = fetchCount,
            statsProvider = {
                anthropicStatsWithReset(STARTED_AT + 2.minutes, hasKnownResetAt = false)
            }
        )

        runCurrent()
        assertEquals(1, fetchCount.get())

        advanceTimeBy(1.hours)
        runCurrent()
        assertEquals(1, fetchCount.get(), "Uma cota sem reset conhecido virou alvo de agendamento")

        viewModel.onDestroy()
    }
}
