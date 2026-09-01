package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageAlert
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
