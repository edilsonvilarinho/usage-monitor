package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.USAGE_DETECTED_WINDOW_MILLIS
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.viewmodel.QuotaActivityTracker
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

private val START = Instant.parse("2026-10-06T12:00:00Z")
private val GO = UsageTargetKey.forSource(ApiSource.OPENCODE_GO)

private class StepClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

private fun goStats(used: Long) = ApiUsageStats(
    source = ApiSource.OPENCODE_GO,
    apiName = "OpenCode Go",
    quotas = listOf(
        QuotaInfo(
            label = "Go 5h",
            used = used,
            total = 100L,
            periodEndAt = Instant.parse("2026-10-06T16:00:00Z"),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE
        )
    )
)

class QuotaActivityTrackerTest {

    private fun tracker(clock: Clock) = QuotaActivityTracker(
        dashboardState = MutableStateFlow(UiState.Loading),
        clock = clock,
        autoStart = false
    )

    @Test
    fun `first reading alone detects nothing`() = runTest {
        val tracker = tracker(StepClock(START))

        tracker.onState(UiState.Success(listOf(goStats(used = 40))))

        assertEquals(emptySet(), tracker.detectedTargets.value)
    }

    @Test
    fun `growing usage between readings marks the target until the window ends`() = runTest {
        val clock = StepClock(START)
        val tracker = tracker(clock)

        tracker.onState(UiState.Success(listOf(goStats(used = 40))))
        clock.current = Instant.fromEpochMilliseconds(START.toEpochMilliseconds() + 60_000L)
        tracker.onState(UiState.Success(listOf(goStats(used = 42))))

        assertEquals(setOf(GO), tracker.detectedTargets.value)

        tracker.publish(Instant.fromEpochMilliseconds(clock.current.toEpochMilliseconds() + USAGE_DETECTED_WINDOW_MILLIS))
        assertEquals(emptySet(), tracker.detectedTargets.value)
    }

    @Test
    fun `retained reading after a failure is not progress`() = runTest {
        val tracker = tracker(StepClock(START))
        val reading = UiState.Success(listOf(goStats(used = 40)))

        tracker.onState(reading)
        tracker.onState(reading)
        tracker.onState(UiState.Error(emptyList()))

        assertEquals(emptySet(), tracker.detectedTargets.value)
    }
}
