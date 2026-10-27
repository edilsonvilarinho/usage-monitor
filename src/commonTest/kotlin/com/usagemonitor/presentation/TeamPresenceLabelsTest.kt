package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.TeamPresenceLabels
import com.usagemonitor.presentation.ui.teamPresenceTitle
import com.usagemonitor.presentation.ui.teamPresenceWindowTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val PT = AppLanguage.PT
private val EN = AppLanguage.EN

class TeamPresenceLabelsTest {

    @Test
    fun `title marks the all-accounts view`() {
        assertEquals("Conectados agora — todas as contas", teamPresenceTitle(PT, isAdminOverview = true))
        assertEquals("Connected now — all accounts", teamPresenceTitle(EN, isAdminOverview = true))
        assertEquals("Connected now — a@b.com", teamPresenceWindowTitle(EN, "a@b.com"))
        assertEquals("Conectados agora", teamPresenceWindowTitle(PT, null))
    }

    @Test
    fun `signal and turn say when there is none`() {
        assertEquals("never reported", TeamPresenceLabels.lastSignal(null, EN))
        assertEquals("nunca reportou", TeamPresenceLabels.lastSignal(null, PT))
        assertEquals("último sinal 12:04", TeamPresenceLabels.lastSignal("12:04", PT))
        assertEquals("last signal 12:04", TeamPresenceLabels.lastSignal("12:04", EN))
        assertEquals("sem turno recente", TeamPresenceLabels.lastTurn(null, PT))
        assertEquals("no recent turn", TeamPresenceLabels.lastTurn(null, EN))
        assertEquals("último turno 12:04", TeamPresenceLabels.lastTurn("12:04", PT))
        assertEquals("last turn 12:04", TeamPresenceLabels.lastTurn("12:04", EN))
        assertEquals("Nobody matching \"ana\" in alias or machine.", TeamPresenceLabels.emptyQuery("ana", EN))
    }

    @Test
    fun `summaries agree in number`() {
        assertEquals("1 working", TeamPresenceLabels.workingSummary(1, EN))
        assertEquals("2 working", TeamPresenceLabels.workingSummary(2, EN))
        assertEquals("1 connected", TeamPresenceLabels.onlineSummary(1, EN))
        assertEquals("2 connected", TeamPresenceLabels.onlineSummary(2, EN))
        assertEquals("1 known", TeamPresenceLabels.knownSummary(1, EN))
        assertEquals("4 known", TeamPresenceLabels.knownSummary(4, EN))
        assertEquals("1 conhecido", TeamPresenceLabels.knownSummary(1, PT))
        assertEquals("2 of 5 connected · 1 working", TeamPresenceLabels.accountBandSummary(2, 5, 1, EN))
        assertEquals("2 de 5 conectados · 3 trabalhando", TeamPresenceLabels.accountBandSummary(2, 5, 3, PT))
    }

    @Test
    fun `empty, error and removal texts in english`() {
        assertEquals("Nobody has shown up yet. Each machine reports when the app opens.", TeamPresenceLabels.empty(EN))
        assertEquals("Nobody connected right now. Turn off the filter to see the whole team.", TeamPresenceLabels.emptyFiltered(EN))
        assertEquals("Could not read team presence: x", TeamPresenceLabels.error("x", EN))
        assertTrue(TeamPresenceLabels.deleteAccountWarning("conta-1", 3, EN).startsWith("Account conta-1, its 3 member(s)"))
        assertTrue(TeamPresenceLabels.deleteAccountWarning("conta-1", 3, PT).startsWith("A conta conta-1, os 3 integrante(s)"))
        assertEquals("this machine reports for this account", TeamPresenceLabels.cannotDeleteOwnAccount(EN))
        assertEquals("esta máquina participa desta conta", TeamPresenceLabels.cannotDeleteOwnAccount(PT))
        assertEquals("Could not complete the removal: x", TeamPresenceLabels.actionError("x", EN))
        assertEquals(
            "This machine's clock and the server's differ by about 7 min. The State column may be wrong.",
            TeamPresenceLabels.clockSkewNotice(7, EN)
        )
        assertEquals(
            "Os relógios desta máquina e do servidor divergem em cerca de 7 min. A coluna Estado pode estar errada.",
            TeamPresenceLabels.clockSkewNotice(7, PT)
        )
    }
}
