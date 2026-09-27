package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnthropicProfileRef
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Cadência adaptativa por alvo (issue #269, Fase 2), em tempo virtual. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelCadenceTest : DashboardViewModelTestSupport() {

    private class VirtualClock(private val origin: Instant, private val scheduler: TestCoroutineScheduler) : Clock {
        override fun now(): Instant = origin + scheduler.currentTime.milliseconds
    }

    private val startedAt = Instant.parse("2026-09-27T12:00:00Z")

    private fun cadenceViewModel(
        scheduler: TestCoroutineScheduler,
        fetchTimes: MutableList<Long>,
        busy: MutableStateFlow<Boolean>,
        profiles: List<AnthropicProfileRef> = listOf(AnthropicProfileRef.DEFAULT)
    ): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                fetchTimes += scheduler.currentTime
                return Result.success(sampleAnthropicStats)
            }
        }
        val unused = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        return DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository { override suspend fun getUsage() = unused }),
            GetCodexUsageUseCase(object : CodexRepository { override suspend fun getUsage() = unused }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository { override suspend fun getUsage() = unused }),
            MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            historyUseCase(mutableListOf()),
            clock = VirtualClock(startedAt, scheduler),
            isBusy = busy,
            anthropicProfiles = MutableStateFlow(profiles),
            config = DashboardViewModelConfig(
                workerDispatcher = StandardTestDispatcher(scheduler),
                autoStartInitialFetch = true,
                autoStartCountdown = true,
                autoStartUpdateChecks = false
            )
        )
    }

    @Test
    fun `idle targets are collected every five minutes`() = runTest {
        val fetchTimes = Collections.synchronizedList(mutableListOf<Long>())
        val viewModel = cadenceViewModel(testScheduler, fetchTimes, MutableStateFlow(false))

        runCurrent()
        assertEquals(1, fetchTimes.size, "A coleta inicial não aconteceu")
        advanceTimeBy(4.minutes)
        runCurrent()
        assertEquals(1, fetchTimes.size, "Coletou antes dos 5 min sem sessão ativa")
        advanceTimeBy(1.minutes + 1.seconds)
        runCurrent()
        assertEquals(2, fetchTimes.size, "Não coletou aos 5 min")
        assertEquals(5.minutes, viewModel.currentPollInterval.value)

        viewModel.onDestroy()
    }

    @Test
    fun `an active CLI session drops the cadence to one minute`() = runTest {
        val fetchTimes = Collections.synchronizedList(mutableListOf<Long>())
        val viewModel = cadenceViewModel(testScheduler, fetchTimes, MutableStateFlow(true))

        runCurrent()
        advanceTimeBy(3.minutes + 1.seconds)
        runCurrent()

        assertEquals(4, fetchTimes.size, "Esperava a inicial e uma por minuto: $fetchTimes")
        assertEquals(1.minutes, viewModel.currentPollInterval.value)
        viewModel.onDestroy()
    }

    @Test
    fun `a session starting after a quiet stretch collects right away`() = runTest {
        val fetchTimes = Collections.synchronizedList(mutableListOf<Long>())
        val busy = MutableStateFlow(false)
        val viewModel = cadenceViewModel(testScheduler, fetchTimes, busy)

        runCurrent()
        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(1, fetchTimes.size)

        // Dois minutos ociosos já passam da cadência ativa: a sessão que começa
        // não espera os três minutos que faltavam para os 5.
        busy.value = true
        runCurrent()

        assertEquals(2, fetchTimes.size, "A sessão começando não antecipou a coleta")
        viewModel.onDestroy()
    }

    @Test
    fun `Anthropic accounts due together go out spaced`() = runTest {
        val fetchTimes = Collections.synchronizedList(mutableListOf<Long>())
        val profiles = listOf(
            AnthropicProfileRef.DEFAULT,
            AnthropicProfileRef("second", "Segunda")
        )
        val viewModel = cadenceViewModel(testScheduler, fetchTimes, MutableStateFlow(false), profiles)

        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertEquals(2, fetchTimes.size)
        val gap = fetchTimes[1] - fetchTimes[0]
        assertTrue(gap >= 800, "Intervalo entre contas: ${gap}ms")
        viewModel.onDestroy()
    }
}
