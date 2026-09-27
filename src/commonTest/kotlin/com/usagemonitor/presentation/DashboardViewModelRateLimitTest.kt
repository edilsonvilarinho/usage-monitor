package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.RateLimitedException
import com.usagemonitor.domain.entity.UsageTargetKey
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Backoff por 429 e última leitura mantida (issue #269, Fase 1). */
class DashboardViewModelRateLimitTest : DashboardViewModelTestSupport() {

    private val anthropicTarget = UsageTargetKey(ApiSource.ANTHROPIC, AnthropicProfileRef.DEFAULT.id)

    private class MutableClock(@Volatile var current: Instant) : Clock {
        override fun now(): Instant = current
    }

    @Test
    fun `a target in backoff does not go to the network`() = runTest {
        val calls = AtomicInteger()
        val viewModel = anthropicOnlyViewModel(clock = MutableClock(fixedInstant)) {
            calls.incrementAndGet()
            Result.failure(RateLimitedException("Anthropic HTTP 429: {}", retryAfter = 0.seconds))
        }

        viewModel.refresh()
        awaitConditionRealTime { calls.get() == 1 && viewModel.refreshingTargets.value.isEmpty() }
        viewModel.refresh()
        viewModel.refresh(ApiSource.ANTHROPIC)
        settleBackgroundWork()

        assertEquals(1, calls.get())
        viewModel.onDestroy()
    }

    @Test
    fun `clicking a target in backoff says until when and does not fetch`() = runTest {
        val calls = AtomicInteger()
        val viewModel = anthropicOnlyViewModel(clock = MutableClock(fixedInstant)) {
            calls.incrementAndGet()
            Result.failure(RateLimitedException("Anthropic HTTP 429: {}", retryAfter = 300.seconds))
        }
        viewModel.refresh()
        awaitConditionRealTime { calls.get() == 1 && viewModel.refreshingTargets.value.isEmpty() }
        viewModel.clearToast()

        viewModel.refresh(anthropicTarget)
        settleBackgroundWork()

        val toast = assertIs<DashboardToast.RateLimit>(viewModel.toastMessage.value)
        // Retry-After de 300 s vence o piso de 60 s; mais a folga de 1 s.
        assertEquals(fixedInstant + 301.seconds, toast.retryAt)
        assertEquals(1, calls.get())
        viewModel.onDestroy()
    }

    @Test
    fun `the backoff is published and survives a new view model`() = runTest {
        val published = mutableListOf<Map<UsageTargetKey, Instant>>()
        val first = anthropicOnlyViewModel(
            clock = MutableClock(fixedInstant),
            onBackoffChanged = { backoffs -> synchronized(published) { published += backoffs } }
        ) { Result.failure(RateLimitedException("Anthropic HTTP 429: {}")) }
        first.refresh()
        awaitConditionRealTime { synchronized(published) { published.isNotEmpty() } }
        first.onDestroy()
        val persisted = synchronized(published) { published.last() }
        assertEquals(fixedInstant + 61.seconds, persisted[anthropicTarget])

        val calls = AtomicInteger()
        val second = anthropicOnlyViewModel(clock = MutableClock(fixedInstant + 30.seconds), persisted = persisted) {
            calls.incrementAndGet()
            Result.success(sampleAnthropicStats)
        }
        second.refresh()
        settleBackgroundWork()

        assertEquals(0, calls.get())
        second.onDestroy()
    }

    @Test
    fun `a server failure keeps the last reading marked as unstable`() = runTest {
        val clock = MutableClock(fixedInstant)
        val responses = ArrayDeque(
            listOf(
                Result.success(sampleAnthropicStats),
                Result.failure<ApiUsageStats>(IllegalStateException("Anthropic HTTP 500: upstream"))
            )
        )
        val viewModel = anthropicOnlyViewModel(clock = clock) { responses.removeFirst() }

        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.data?.isNotEmpty() == true }
        clock.current = fixedInstant + 10.minutes
        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.errors?.isNotEmpty() == true }

        val state = assertIs<UiState.Success>(viewModel.uiState.value)
        val kept = state.data.single()
        assertTrue(ApiUsageNotice.SOURCE_UNSTABLE in kept.notices)
        assertEquals(fixedInstant, kept.fetchedAt)
        viewModel.onDestroy()
    }

    @Test
    fun `a refused credential drops the last reading`() = runTest {
        val responses = ArrayDeque(
            listOf(
                Result.success(sampleAnthropicStats),
                Result.failure<ApiUsageStats>(IllegalStateException("Anthropic HTTP 401: invalid token"))
            )
        )
        val viewModel = anthropicOnlyViewModel(clock = MutableClock(fixedInstant)) { responses.removeFirst() }

        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.data?.isNotEmpty() == true }
        viewModel.refresh()
        awaitConditionRealTime { viewModel.uiState.value is UiState.Error }

        assertIs<UiState.Error>(viewModel.uiState.value)
        viewModel.onDestroy()
    }

    @Test
    fun `a reading older than seven days is dropped instead of kept`() = runTest {
        val clock = MutableClock(fixedInstant)
        val responses = ArrayDeque(
            listOf(
                Result.success(sampleAnthropicStats),
                Result.failure<ApiUsageStats>(IllegalStateException("Anthropic HTTP 500: upstream"))
            )
        )
        val viewModel = anthropicOnlyViewModel(clock = clock) { responses.removeFirst() }

        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.data?.isNotEmpty() == true }
        clock.current = fixedInstant + 7.days + 1.minutes
        viewModel.refresh()
        awaitConditionRealTime { viewModel.uiState.value is UiState.Error }

        val state = assertIs<UiState.Error>(viewModel.uiState.value)
        assertNull(state.errors.single().retryAt)
        viewModel.onDestroy()
    }

    @Test
    fun `a rate limit keeps the reading and carries the retry instant to the banner`() = runTest {
        val responses = ArrayDeque(
            listOf(
                Result.success(sampleAnthropicStats),
                Result.failure<ApiUsageStats>(RateLimitedException("Anthropic HTTP 429: {}"))
            )
        )
        val viewModel = anthropicOnlyViewModel(clock = MutableClock(fixedInstant)) { responses.removeFirst() }

        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.data?.isNotEmpty() == true }
        viewModel.refresh()
        awaitConditionRealTime { (viewModel.uiState.value as? UiState.Success)?.errors?.isNotEmpty() == true }

        val state = assertIs<UiState.Success>(viewModel.uiState.value)
        assertTrue(ApiUsageNotice.SOURCE_UNSTABLE in state.data.single().notices)
        assertNotNull(state.errors.single().retryAt)
        viewModel.onDestroy()
    }

    private fun anthropicOnlyViewModel(
        clock: Clock,
        persisted: Map<UsageTargetKey, Instant> = emptyMap(),
        onBackoffChanged: (Map<UsageTargetKey, Instant>) -> Unit = {},
        anthropic: () -> Result<ApiUsageStats>
    ): DashboardViewModel {
        val anthropicRepo = object : AnthropicRepository {
            override suspend fun getUsage() = anthropic()
        }
        val unused = Result.failure<ApiUsageStats>(Exception("Não deve ser chamado"))
        return DashboardViewModel(
            GetAnthropicUsageUseCase(anthropicRepo),
            GetMiniMaxUsageUseCase(object : MiniMaxRepository { override suspend fun getUsage() = unused }),
            GetCodexUsageUseCase(object : CodexRepository { override suspend fun getUsage() = unused }),
            GetDeepSeekUsageUseCase(object : DeepSeekRepository { override suspend fun getUsage() = unused }),
            MutableStateFlow(setOf(ApiSource.ANTHROPIC)),
            historyUseCase(mutableListOf()),
            clock = clock,
            config = manualRefreshConfig(),
            persistedRateLimitBackoffs = persisted,
            onRateLimitBackoffChanged = onBackoffChanged
        )
    }
}
