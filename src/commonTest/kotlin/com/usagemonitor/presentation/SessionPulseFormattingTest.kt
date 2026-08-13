package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.presentation.ui.components.sessionPulseHint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val NOW = Instant.parse("2026-08-13T12:00:00Z")

class SessionPulseFormattingTest {

    @Test
    fun `a pulse at rest has nothing to explain`() {
        assertNull(sessionPulseHint(SessionPulse.EMPTY, AppLanguage.PT))
    }

    @Test
    fun `a single local session names the project`() {
        val pulse = SessionPulse(listOf(alert("a", CliSessionHealth.SATURATED, projectName = "usage-monitor")))

        assertEquals(
            "1 sessão ativa agora pede atenção:\n• Saturada — usage-monitor",
            sessionPulseHint(pulse, AppLanguage.PT)
        )
        assertEquals(
            "1 active session needs attention:\n• Saturated — usage-monitor",
            sessionPulseHint(pulse, AppLanguage.EN)
        )
    }

    /** Sem `cwd` no índice o projeto é desconhecido; o id curto identifica a sessão. */
    @Test
    fun `without a project name the short session id identifies the session`() {
        val pulse = SessionPulse(listOf(alert("2991339c-aaaa-bbbb", CliSessionHealth.ATTENTION)))

        assertEquals(
            "1 sessão ativa agora pede atenção:\n• Atenção — 2991339c",
            sessionPulseHint(pulse, AppLanguage.PT)
        )
    }

    @Test
    fun `a team session names who and which machine`() {
        val pulse = SessionPulse(
            listOf(
                alert(
                    sessionId = "a",
                    health = CliSessionHealth.SATURATED,
                    projectName = "mdlog-web-compras",
                    memberAlias = "SUETONIO",
                    machineLabel = "devmachine"
                )
            )
        )

        assertEquals(
            "1 sessão ativa agora pede atenção:\n• SUETONIO · devmachine — Saturada (mdlog-web-compras)",
            sessionPulseHint(pulse, AppLanguage.PT)
        )
    }

    /** `machineLabel` cai no apelido quando o host é desconhecido. */
    @Test
    fun `an alias equal to the machine is not repeated`() {
        val pulse = SessionPulse(
            listOf(
                alert(
                    sessionId = "a",
                    health = CliSessionHealth.ATTENTION,
                    projectName = "usage-monitor",
                    memberAlias = "EDILSON",
                    machineLabel = "EDILSON"
                )
            )
        )

        assertEquals(
            "1 sessão ativa agora pede atenção:\n• EDILSON — Atenção (usage-monitor)",
            sessionPulseHint(pulse, AppLanguage.PT)
        )
    }

    @Test
    fun `beyond three sessions the rest is summarized`() {
        val pulse = SessionPulse(
            (1..5).map { index -> alert("s$index", CliSessionHealth.ATTENTION, projectName = "p$index") }
        )

        val hint = sessionPulseHint(pulse, AppLanguage.PT)!!
        assertTrue(hint.startsWith("5 sessões ativas agora pedem atenção:"))
        assertEquals(5, hint.lines().size)
        assertTrue(hint.endsWith("• e mais 2"))

        assertTrue(sessionPulseHint(pulse, AppLanguage.EN)!!.endsWith("• and 2 more"))
    }

    private fun alert(
        sessionId: String,
        health: CliSessionHealth,
        projectName: String? = null,
        memberAlias: String? = null,
        machineLabel: String? = null
    ): ActiveSessionAlert {
        return ActiveSessionAlert(
            sessionId = sessionId,
            health = health,
            lastActivityAt = NOW,
            projectName = projectName,
            machineLabel = machineLabel,
            memberAlias = memberAlias
        )
    }
}
