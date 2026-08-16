package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.viewmodel.UsageAlertViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val NOW = Instant.parse("2026-08-13T12:00:00Z")
private val TARGET = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID)
private val QUOTA_KEY = QuotaSeriesKey("Sessão 5h", PeriodType.INTERVAL)

@OptIn(ExperimentalCoroutinesApi::class)
class UsageAlertViewModelTest {

    @Test
    fun `an emission that crosses a threshold produces an alert`() = runTest {
        val dashboard = MutableStateFlow<UiState>(UiState.Loading)
        val viewModel = buildViewModel(dashboard = dashboard, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val received = mutableListOf<UsageAlert>()
        val collector = launch { viewModel.alerts.collect { alert -> received += alert } }
        runCurrent()

        dashboard.value = UiState.Success(data = listOf(stats(usedPercent = 92)))
        runCurrent()

        assertEquals(
            listOf(75, 90),
            received.filterIsInstance<UsageAlert.QuotaThreshold>().map { alert -> alert.thresholdPercent }
        )

        collector.cancel()
        viewModel.onDestroy()
    }

    /** O dashboard reemite o mesmo valor a cada coleta; só o primeiro alerta. */
    @Test
    fun `an identical re-emission does not alert again`() = runTest {
        val dashboard = MutableStateFlow<UiState>(UiState.Loading)
        val viewModel = buildViewModel(dashboard = dashboard, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val received = mutableListOf<UsageAlert>()
        val collector = launch { viewModel.alerts.collect { alert -> received += alert } }
        runCurrent()

        dashboard.value = UiState.Success(data = listOf(stats(usedPercent = 92)))
        runCurrent()
        val afterFirst = received.size

        dashboard.value = UiState.Success(data = listOf(stats(usedPercent = 93)))
        runCurrent()

        assertEquals(afterFirst, received.size)

        collector.cancel()
        viewModel.onDestroy()
    }

    @Test
    fun `a saturated session produces an alert`() = runTest {
        val pulses = MutableStateFlow<Map<UsageTargetKey, SessionPulse>>(emptyMap())
        val viewModel = buildViewModel(pulses = pulses, dispatcher = UnconfinedTestDispatcher(testScheduler))

        val received = mutableListOf<UsageAlert>()
        val collector = launch { viewModel.alerts.collect { alert -> received += alert } }
        runCurrent()

        pulses.value = mapOf(
            TARGET to SessionPulse(
                listOf(
                    ActiveSessionAlert(
                        sessionId = "s1",
                        health = CliSessionHealth.SATURATED,
                        lastActivityAt = NOW,
                        projectName = "usage-monitor"
                    )
                )
            )
        )
        runCurrent()

        val alert = received.filterIsInstance<UsageAlert.SessionSaturated>().single()
        assertEquals("s1", alert.sessionId)
        assertEquals("usage-monitor", alert.projectName)

        collector.cancel()
        viewModel.onDestroy()
    }

    /** Verde permanente vira decoração: o ponto da bandeja só acende em risco. */
    @Test
    fun `the worst risk ignores the on-track level`() = runTest {
        val dashboard = MutableStateFlow<UiState>(UiState.Loading)
        val viewModel = buildViewModel(dashboard = dashboard, dispatcher = UnconfinedTestDispatcher(testScheduler))

        dashboard.value = UiState.Success(
            data = listOf(stats(usedPercent = 10)),
            riskSummaries = mapOf(
                TARGET to mapOf(QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.ON_TRACK, null))
            )
        )
        runCurrent()
        assertNull(viewModel.worstRisk.value)

        dashboard.value = UiState.Success(
            data = listOf(stats(usedPercent = 10)),
            riskSummaries = mapOf(
                TARGET to mapOf(QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 1.hours))
            )
        )
        runCurrent()
        assertEquals(UsageRiskLevel.WILL_EXCEED, viewModel.worstRisk.value)

        viewModel.onDestroy()
    }

    @Test
    fun `disabling the alerts stops the emission`() = runTest {
        val dashboard = MutableStateFlow<UiState>(UiState.Loading)
        val alertSettings = MutableStateFlow(
            UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false, sessionAlertsEnabled = false)
        )
        val viewModel = buildViewModel(
            dashboard = dashboard,
            alertSettings = alertSettings,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )

        val received = mutableListOf<UsageAlert>()
        val collector = launch { viewModel.alerts.collect { alert -> received += alert } }
        runCurrent()

        dashboard.value = UiState.Success(data = listOf(stats(usedPercent = 99)))
        runCurrent()

        assertTrue(received.isEmpty())

        collector.cancel()
        viewModel.onDestroy()
    }

    private fun buildViewModel(
        dashboard: MutableStateFlow<UiState> = MutableStateFlow(UiState.Loading),
        pulses: MutableStateFlow<Map<UsageTargetKey, SessionPulse>> = MutableStateFlow(emptyMap()),
        alertSettings: MutableStateFlow<UsageAlertSettings> = MutableStateFlow(UsageAlertSettings.DEFAULT),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): UsageAlertViewModel {
        return UsageAlertViewModel(
            dashboardState = dashboard,
            cliPulses = pulses,
            alertSettings = alertSettings,
            dispatcher = dispatcher,
            clock = object : Clock {
                override fun now(): Instant = NOW
            },
            timeZone = TimeZone.UTC
        )
    }
}

private fun stats(usedPercent: Int): ApiUsageStats {
    return ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = TARGET,
        apiName = "Anthropic",
        quotas = listOf(
            QuotaInfo(
                label = "Sessão 5h",
                used = usedPercent.toLong(),
                total = 100L,
                periodEndAt = NOW + 2.hours,
                periodType = PeriodType.INTERVAL,
                unit = UsageUnit.PERCENTAGE
            )
        )
    )
}
