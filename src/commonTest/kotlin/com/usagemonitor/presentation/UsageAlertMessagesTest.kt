package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.usageAlertMessage
import kotlin.time.Instant
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

    /** #398, Y2: o reinício responde ao limiar — mesmo título, e diz quando é o próximo. */
    @Test
    fun `the reset message keeps the threshold title and names the next reset`() {
        val reset = UsageAlert.QuotaReset(
            target = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID),
            targetLabel = "Anthropic — Padrão",
            quotaLabel = "Sessão 5h",
            nextResetAt = NOW
        )
        val pt = usageAlertMessage(reset, AppLanguage.PT)
        val en = usageAlertMessage(reset, AppLanguage.EN)

        assertEquals("Anthropic — Padrão · Sessão 5h", pt.title)
        assertTrue(pt.body.startsWith("A cota reiniciou"), pt.body)
        assertTrue(pt.body.contains("Próximo reinício:") && pt.body.endsWith("BRT."), pt.body)
        assertTrue(en.body.startsWith("The quota reset"), en.body)
    }

    /** O separador decimal vem do idioma, não do `Locale` da máquina que roda. */
    @Test
    fun `threshold message says exhausted at 100 and names the reset only when known`() {
        val periodEnd = Instant.parse("2026-10-30T18:30:00Z")
        fun threshold(threshold: Int, actual: Int, knownReset: Boolean) = UsageAlert.QuotaThreshold(
            target = UsageTargetKey.forSource(ApiSource.ANTHROPIC),
            targetLabel = "Claude",
            quotaLabel = "Claude 5h",
            thresholdPercent = threshold,
            actualPercent = actual,
            periodEndAt = periodEnd,
            hasKnownResetAt = knownReset
        )

        val exhaustedEn = usageAlertMessage(threshold(100, 100, knownReset = false), AppLanguage.EN)
        val aboveEn = usageAlertMessage(threshold(75, 81, knownReset = true), AppLanguage.EN)
        val exhaustedPt = usageAlertMessage(threshold(100, 100, knownReset = false), AppLanguage.PT)

        assertEquals("Quota exhausted (100%).", exhaustedEn.body)
        assertEquals("Cota esgotada (100%).", exhaustedPt.body)
        assertTrue(aboveEn.body.startsWith("Usage at 81%, above the 75% threshold. Reset: "), aboveEn.body)
        assertTrue(aboveEn.body.endsWith(" BRT."), aboveEn.body)
    }

    @Test
    fun `reset reminder says how long is left and when work can resume`() {
        val reminder = UsageAlert.ResetSoon(
            target = UsageTargetKey.forSource(ApiSource.CODEX),
            targetLabel = "Codex",
            quotaLabel = "Codex 5h",
            actualPercent = 92,
            resetAt = Instant.parse("2026-10-27T18:00:00Z"),
            minutesLeft = 15
        )

        val en = usageAlertMessage(reminder, AppLanguage.EN)
        val pt = usageAlertMessage(reminder, AppLanguage.PT)

        assertTrue(en.title.endsWith(" resets in 15 min"), en.title)
        assertTrue(en.body.startsWith("At 92%. You can resume at "), en.body)
        assertTrue(pt.title.endsWith(" reinicia em 15 min"), pt.title)
        assertTrue(pt.body.startsWith("Está em 92%. Dá para retomar em "), pt.body)
    }

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
