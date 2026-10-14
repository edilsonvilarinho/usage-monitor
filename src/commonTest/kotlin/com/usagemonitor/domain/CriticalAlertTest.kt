package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.isCriticalAlert
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** #408: dono único da gravidade — cota em 90% ou mais (esgotada incluída); o resto não é crítico. */
class CriticalAlertTest {

    private fun quota(actual: Int) = UsageAlert.QuotaThreshold(
        target = UsageTargetKey(ApiSource.CODEX),
        targetLabel = "Padrão",
        quotaLabel = "Sessão 5h",
        thresholdPercent = 75,
        actualPercent = actual,
        periodEndAt = Instant.parse("2026-10-06T15:00:00Z"),
        hasKnownResetAt = true
    )

    @Test
    fun `quota at ninety percent or more is critical`() {
        assertFalse(isCriticalAlert(quota(89)))
        assertTrue(isCriticalAlert(quota(90)))
        assertTrue(isCriticalAlert(quota(100)))
    }

    @Test
    fun `session, reset and spike alerts are never critical`() {
        assertFalse(isCriticalAlert(UsageAlert.SessionSaturated("s1", "app")))
        assertFalse(
            isCriticalAlert(
                UsageAlert.QuotaReset(UsageTargetKey(ApiSource.CODEX), "Padrão", "Sessão 5h", Instant.parse("2026-10-06T20:00:00Z"))
            )
        )
    }
}
