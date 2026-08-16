package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageAlertState
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.evaluateUsageAlerts
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-08-13T12:00:00Z")
private val RESET_AT = NOW + 2.hours

private val TARGET = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID)

class UsageAlertTest {

    @Test
    fun `crossing a threshold emits one alert`() {
        val result = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val alerts = result.alerts.filterIsInstance<UsageAlert.QuotaThreshold>()
        assertEquals(listOf(75, 90), alerts.map { alert -> alert.thresholdPercent })
        assertEquals(92, alerts.first().actualPercent)
    }

    @Test
    fun `the same threshold does not repeat in the same window`() {
        val first = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val second = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 95)),
            sessionPulse = SessionPulse.EMPTY,
            previous = first.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 10.minutes
        )

        assertTrue(second.alerts.isEmpty())
    }

    @Test
    fun `a new threshold in the same window still fires`() {
        val first = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 80)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val second = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 100)),
            sessionPulse = SessionPulse.EMPTY,
            previous = first.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 10.minutes
        )

        val alerts = second.alerts.filterIsInstance<UsageAlert.QuotaThreshold>()
        assertEquals(listOf(90, 100), alerts.map { alert -> alert.thresholdPercent })
    }

    /** Jitter de ~1s no `resets_at` não é reset: rearmar ali repetiria o alerta. */
    @Test
    fun `reset jitter inside the tolerance does not rearm the alert`() {
        val first = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val second = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 93, resetAt = RESET_AT + 1.minutes)),
            sessionPulse = SessionPulse.EMPTY,
            previous = first.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 10.minutes
        )

        assertTrue(second.alerts.isEmpty())
    }

    @Test
    fun `a real window change rearms the alert`() {
        val first = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val laterNow = NOW + 3.hours
        val second = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92, resetAt = laterNow + 2.hours)),
            sessionPulse = SessionPulse.EMPTY,
            previous = first.state,
            settings = UsageAlertSettings.DEFAULT,
            now = laterNow
        )

        val alerts = second.alerts.filterIsInstance<UsageAlert.QuotaThreshold>()
        assertEquals(listOf(75, 90), alerts.map { alert -> alert.thresholdPercent })
    }

    /** A janela vencida descreve um período que já não existe. */
    @Test
    fun `an expired quota does not alert`() {
        val result = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 99, resetAt = NOW - 1.minutes)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `the threshold is a floor and does not round up`() {
        val result = evaluateUsageAlerts(
            stats = listOf(stats(used = 899, total = 1_000)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val alerts = result.alerts.filterIsInstance<UsageAlert.QuotaThreshold>()
        assertEquals(listOf(75), alerts.map { alert -> alert.thresholdPercent })
    }

    /** Silêncio adia; não consome o alerta. */
    @Test
    fun `quiet hours suppress the alert and it fires afterwards`() {
        val settings = UsageAlertSettings.DEFAULT.copy(quietHours = QuietHours(22, 8))

        val silenced = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = settings,
            now = NOW,
            currentLocalHour = 23
        )
        assertTrue(silenced.alerts.isEmpty())

        val awake = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = silenced.state,
            settings = settings,
            now = NOW + 10.minutes,
            currentLocalHour = 9
        )

        val alerts = awake.alerts.filterIsInstance<UsageAlert.QuotaThreshold>()
        assertEquals(listOf(75, 90), alerts.map { alert -> alert.thresholdPercent })
    }

    @Test
    fun `disabling quota alerts clears the fired state`() {
        val fired = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val disabled = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = fired.state,
            settings = UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false),
            now = NOW + 10.minutes
        )
        assertTrue(disabled.alerts.isEmpty())

        val reenabled = evaluateUsageAlerts(
            stats = listOf(stats(usedPercent = 92)),
            sessionPulse = SessionPulse.EMPTY,
            previous = disabled.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 20.minutes
        )

        assertEquals(2, reenabled.alerts.filterIsInstance<UsageAlert.QuotaThreshold>().size)
    }

    @Test
    fun `a saturated session alerts once while it stays in the pulse`() {
        val pulse = SessionPulse(listOf(sessionAlert("s1", CliSessionHealth.SATURATED)))

        val first = evaluateUsageAlerts(
            stats = emptyList(),
            sessionPulse = pulse,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )
        assertEquals(
            listOf("s1"),
            first.alerts.filterIsInstance<UsageAlert.SessionSaturated>().map { alert -> alert.sessionId }
        )

        val second = evaluateUsageAlerts(
            stats = emptyList(),
            sessionPulse = pulse,
            previous = first.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 30.minutes
        )
        assertTrue(second.alerts.isEmpty())
    }

    /** Sessão em atenção é rotina demais para virar notificação. */
    @Test
    fun `a session in attention does not alert`() {
        val result = evaluateUsageAlerts(
            stats = emptyList(),
            sessionPulse = SessionPulse(listOf(sessionAlert("s1", CliSessionHealth.ATTENTION))),
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        assertTrue(result.alerts.isEmpty())
    }

    @Test
    fun `a session that left the pulse and came back alerts again`() {
        val pulse = SessionPulse(listOf(sessionAlert("s1", CliSessionHealth.SATURATED)))

        val first = evaluateUsageAlerts(
            stats = emptyList(),
            sessionPulse = pulse,
            previous = UsageAlertState.EMPTY,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW
        )

        val gone = evaluateUsageAlerts(
            stats = emptyList(),
            sessionPulse = SessionPulse.EMPTY,
            previous = first.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 10.minutes
        )
        assertTrue(gone.alerts.isEmpty())

        val back = evaluateUsageAlerts(
            stats = emptyList(),
            sessionPulse = pulse,
            previous = gone.state,
            settings = UsageAlertSettings.DEFAULT,
            now = NOW + 20.minutes
        )

        assertEquals(1, back.alerts.filterIsInstance<UsageAlert.SessionSaturated>().size)
    }

    @Test
    fun `quiet hours crossing midnight cover both sides`() {
        val quiet = QuietHours(22, 8)

        assertTrue(quiet.contains(23))
        assertTrue(quiet.contains(0))
        assertTrue(quiet.contains(7))
        assertEquals(false, quiet.contains(8))
        assertEquals(false, quiet.contains(21))
    }

    @Test
    fun `invalid thresholds are dropped and the list is normalized`() {
        val settings = UsageAlertSettings.DEFAULT.copy(quotaPercents = listOf(90, 0, 75, 90, 150, 100))

        assertEquals(listOf(75, 90, 100), settings.effectiveQuotaPercents)
    }
}

private fun stats(
    usedPercent: Int? = null,
    used: Long = 0L,
    total: Long = 100L,
    resetAt: Instant = RESET_AT
): ApiUsageStats {
    val resolvedUsed = usedPercent?.toLong() ?: used
    val resolvedTotal = if (usedPercent != null) 100L else total

    return ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = TARGET,
        apiName = "Anthropic",
        quotas = listOf(
            QuotaInfo(
                label = "Sessão 5h",
                used = resolvedUsed,
                total = resolvedTotal,
                periodEndAt = resetAt,
                periodType = PeriodType.INTERVAL,
                unit = UsageUnit.PERCENTAGE
            )
        )
    )
}

private fun sessionAlert(sessionId: String, health: CliSessionHealth): ActiveSessionAlert {
    return ActiveSessionAlert(
        sessionId = sessionId,
        health = health,
        lastActivityAt = NOW,
        projectName = "usage-monitor"
    )
}
