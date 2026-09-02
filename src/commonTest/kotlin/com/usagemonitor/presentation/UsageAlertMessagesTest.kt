package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.usageAlertMessage
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-09-01T12:00:00Z")

class UsageAlertMessagesTest {

    @Test
    fun `the stalled message names the project and the elapsed time`() {
        val message = usageAlertMessage(stalled(3.hours.inWholeMilliseconds), AppLanguage.PT)

        assertEquals("Sessão CLI sem resposta", message.title)
        assertTrue(message.body.contains("usage-monitor"))
        assertTrue(message.body.contains("3h00"))
    }

    /**
     * A evidência é ausência de resposta no transcript. Afirmar que o processo
     * travou seria afirmar o que o app não mede — ele não olha o sistema
     * operacional.
     */
    @Test
    fun `the stalled message never claims the process is hung`() {
        val pt = usageAlertMessage(stalled(3.hours.inWholeMilliseconds), AppLanguage.PT).body
        val en = usageAlertMessage(stalled(3.hours.inWholeMilliseconds), AppLanguage.EN).body

        assertFalse(pt.contains("travou"))
        assertFalse(pt.contains("travada"))
        assertFalse(en.contains("hung"))
        assertFalse(en.contains("crashed"))
    }

    @Test
    fun `a session without a project keeps the sentence readable`() {
        val message = usageAlertMessage(stalled(90.minutes.inWholeMilliseconds, projectName = null), AppLanguage.PT)

        assertTrue(message.body.startsWith("Uma sessão"))
        assertTrue(message.body.contains("1h30"))
    }

    @Test
    fun `the spike message names the target, the factor and the baseline`() {
        val pt = usageAlertMessage(spike(), AppLanguage.PT)
        val en = usageAlertMessage(spike(), AppLanguage.EN)

        assertEquals("Consumo acima do habitual", pt.title)
        assertEquals("Usage above the usual", en.title)
        assertTrue(pt.body.contains("Anthropic — Padrão · Sessão 5h"))
        assertTrue(pt.body.contains("4,0×"))
        assertTrue(pt.body.contains("3 dias"))
        assertTrue(en.body.contains("4.0×"))
        assertTrue(en.body.contains("3 days"))
    }

    /**
     * O título de `QuotaThreshold` é `alvo · cota`. Se este repetisse a fórmula, os
     * dois avisos chegariam à bandeja com a mesma primeira linha dizendo coisas
     * diferentes — um mede distância até o teto, o outro até o hábito.
     */
    @Test
    fun `the spike title does not collide with the quota threshold title`() {
        val threshold = usageAlertMessage(
            UsageAlert.QuotaThreshold(
                target = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID),
                targetLabel = "Anthropic — Padrão",
                quotaLabel = "Sessão 5h",
                thresholdPercent = 90,
                actualPercent = 92,
                periodEndAt = NOW,
                hasKnownResetAt = true
            ),
            AppLanguage.PT
        )

        assertEquals("Anthropic — Padrão · Sessão 5h", threshold.title)
        assertTrue(usageAlertMessage(spike(), AppLanguage.PT).title != threshold.title)
    }

    /** O separador decimal vem do idioma, não do `Locale` da máquina que roda. */
    @Test
    fun `the factor is formatted without depending on the jvm locale`() {
        assertTrue(usageAlertMessage(spike(factor = 2.55), AppLanguage.PT).body.contains("2,6×"))
        assertTrue(usageAlertMessage(spike(factor = 2.55), AppLanguage.EN).body.contains("2.6×"))
        assertTrue(usageAlertMessage(spike(factor = 10.0), AppLanguage.PT).body.contains("10,0×"))
    }

    private fun spike(factor: Double = 4.0): UsageAlert.SpendSpike {
        return UsageAlert.SpendSpike(
            target = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID),
            targetLabel = "Anthropic — Padrão",
            quotaLabel = "Sessão 5h",
            factor = factor,
            todayDelta = 400L,
            baselineDelta = 100L,
            baselineDays = 3,
            quotaTotal = 100L,
            unit = UsageUnit.PERCENTAGE
        )
    }

    private fun stalled(
        pendingMillis: Long,
        projectName: String? = "usage-monitor"
    ): UsageAlert.SessionStalled {
        return UsageAlert.SessionStalled(
            sessionId = "s1",
            projectName = projectName,
            pendingSince = NOW,
            pendingMillis = pendingMillis
        )
    }
}
