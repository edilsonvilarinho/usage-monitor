package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.TeamUsageLabels
import com.usagemonitor.presentation.ui.shortSessionId
import com.usagemonitor.presentation.ui.teamUsageTitle
import com.usagemonitor.presentation.ui.teamUsageWindowTitle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val PT = AppLanguage.PT
private val EN = AppLanguage.EN

class TeamUsageLabelsTest {

    @Test
    fun `window title names the account and the admin overview is plural`() {
        assertEquals("Team sessions", teamUsageTitle(EN))
        assertEquals("All team sessions", teamUsageTitle(EN, isAdminOverview = true))
        assertEquals("Sessões dos times", teamUsageTitle(PT, isAdminOverview = true))
        assertEquals("Sessões do time — a@b.com", teamUsageWindowTitle(PT, "a@b.com"))
        // Rótulo em branco não vira plural: só quem abriu sabe se é a visão global.
        assertEquals("Team sessions", teamUsageWindowTitle(EN, "  "))
    }

    @Test
    fun `counts agree in number`() {
        assertEquals("1 member", TeamUsageLabels.memberCount(1, EN))
        assertEquals("3 members", TeamUsageLabels.memberCount(3, EN))
        assertEquals("1 integrante", TeamUsageLabels.memberCount(1, PT))
        assertEquals("Todas as contas (1)", TeamUsageLabels.allAccounts(1, PT))
        assertEquals("Todas as contas (4)", TeamUsageLabels.allAccounts(4, PT))
        assertEquals("All accounts (1)", TeamUsageLabels.allAccounts(1, EN))
        assertEquals("All accounts (4)", TeamUsageLabels.allAccounts(4, EN))
        assertEquals("2 email groups · 5 UUIDs", TeamUsageLabels.allEmailGroups(2, 5, EN))
        assertEquals("2 grupos por e-mail · 5 UUIDs", TeamUsageLabels.allEmailGroups(2, 5, PT))
        assertEquals("1 UUID", TeamUsageLabels.technicalAccounts(1, PT))
        assertEquals("1 UUID", TeamUsageLabels.technicalAccounts(1, EN))
        assertEquals("3 UUIDs", TeamUsageLabels.technicalAccounts(3, EN))
        assertEquals("3 UUIDs", TeamUsageLabels.technicalAccounts(3, PT))
        assertEquals("Account · 2 members", TeamUsageLabels.accountBandWithMembers(2, EN))
        assertEquals(CliSessionsLabels.sessionCount(2, EN), TeamUsageLabels.sessionCount(2, EN))
    }

    @Test
    fun `trend texts in english`() {
        assertTrue(TeamUsageLabels.trendHint(30, EN).startsWith("How much each member spent per day over the last 30 days"))
        assertTrue(TeamUsageLabels.trendHint(30, PT).contains("últimos 30 dias"))
        assertEquals("No usage recorded in the days the trend covers.", TeamUsageLabels.trendEmpty(EN))
        assertEquals("Nenhum consumo registrado nos dias cobertos pela tendência.", TeamUsageLabels.trendEmpty(PT))
        assertEquals("Trend unavailable: this team server predates the route that serves it.", TeamUsageLabels.trendUnavailable(EN))
        assertTrue(TeamUsageLabels.breakdownHint(EN).startsWith("The same turns from the member list"))
        assertTrue(TeamUsageLabels.trendNotice(EN).startsWith("Estimated cost per day (BRT)"))
        assertTrue(TeamUsageLabels.slidingWindowNotice(EN).startsWith("In this view the 5h range is sliding"))
        assertEquals("Trend", TeamUsageLabels.tabTrend(EN))
        assertEquals("Members", TeamUsageLabels.columnMembers(EN))
        assertEquals("of team", TeamUsageLabels.columnShare(EN))
        assertEquals("do time", TeamUsageLabels.columnShare(PT))
        assertEquals("Account without key", TeamUsageLabels.unlabeledAccount(EN))
    }

    @Test
    fun `shared columns come from the cli session labels`() {
        assertEquals(CliSessionsLabels.machine(EN), TeamUsageLabels.columnMachine(EN))
        assertEquals(CliSessionsLabels.columnTokens(PT), TeamUsageLabels.columnTokens(PT))
        assertEquals(CliSessionsLabels.columnCost(EN), TeamUsageLabels.columnCost(EN))
        assertEquals(CliSessionsLabels.activeTime(PT), TeamUsageLabels.columnActiveTime(PT))
        assertEquals(CliSessionsLabels.live(EN), TeamUsageLabels.live(EN))
        assertEquals(CliSessionsLabels.back(EN), TeamUsageLabels.back(EN))
        assertEquals(
            CliSessionsLabels.healthTally(CliSessionHealthTally(saturated = 1, attention = 2), PT),
            TeamUsageLabels.healthTally(CliSessionHealthTally(saturated = 1, attention = 2), PT)
        )
    }

    @Test
    fun `detail and estimated total`() {
        assertEquals("Querying the session on the team server…", TeamUsageLabels.detailLoading(EN))
        assertEquals("Consultando a sessão no servidor do time…", TeamUsageLabels.detailLoading(PT))
        assertTrue(TeamUsageLabels.missingTurnsNotice(EN).startsWith("This team server does not return the turns"))
        assertEquals(CliSessionsLabels.estimatedTotal(EN), TeamUsageLabels.estimatedTotalInRange(CliSessionRange.ALL, null, false, EN))
        assertEquals(
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_7D, null, false, PT),
            TeamUsageLabels.estimatedTotalInRange(CliSessionRange.LAST_7D, null, false, PT)
        )
        assertEquals("no usage in range", TeamUsageLabels.noActivityInRange(EN))
    }

    @Test
    fun `empty range says whether nothing was ever sent or the window is too short`() {
        val window = TeamUsageLabels.rangeLabel(CliSessionRange.LAST_5H, EN)
        assertEquals(
            "No team member has reported data yet. Check the server in Settings.",
            TeamUsageLabels.emptyInRange(CliSessionRange.ALL, isAnchored = false, language = EN)
        )
        assertEquals(
            "Nenhum integrante do time enviou dados ainda. Confira o servidor nas Configurações.",
            TeamUsageLabels.emptyInRange(CliSessionRange.ALL, isAnchored = true, language = PT)
        )
        assertEquals(
            "No team usage in the current quota window ($window). Pick a wider range.",
            TeamUsageLabels.emptyInRange(CliSessionRange.LAST_5H, isAnchored = true, language = EN)
        )
        assertEquals(
            "Nenhum uso do time nesta janela de quota (${TeamUsageLabels.rangeLabel(CliSessionRange.LAST_5H, PT)}). Escolha uma janela maior.",
            TeamUsageLabels.emptyInRange(CliSessionRange.LAST_5H, isAnchored = true, language = PT)
        )
        assertEquals(
            "No team usage in the last $window. Pick a wider range.",
            TeamUsageLabels.emptyInRange(CliSessionRange.LAST_5H, isAnchored = false, language = EN)
        )
    }

    @Test
    fun `removal texts state the real cost of the action`() {
        val shortId = shortSessionId("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b")
        assertTrue(
            TeamUsageLabels.removeSessionWarning("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b", "repo", EN)
                .startsWith("Session $shortId (repo) and all usage already reported by it will be deleted")
        )
        assertTrue(
            TeamUsageLabels.removeSessionWarning("0199a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b", null, PT)
                .startsWith("A sessão $shortId e todo o consumo")
        )
        assertTrue(TeamUsageLabels.removeMemberWarning("ana", EN).startsWith("\"ana\" and all usage already reported by that machine"))
        assertTrue(TeamUsageLabels.removeMemberWarning("ana", EN).contains("this cannot be undone"))
        assertEquals("this machine", TeamUsageLabels.cannotRemoveSelf(EN))
        assertEquals("esta máquina", TeamUsageLabels.cannotRemoveSelf(PT))
        assertEquals("Could not remove the member: x", TeamUsageLabels.removalError("x", EN))
        assertEquals("Could not delete the session: x", TeamUsageLabels.sessionRemovalError("x", EN))
        assertEquals("Não foi possível excluir a sessão: x", TeamUsageLabels.sessionRemovalError("x", PT))
        assertEquals("Could not reach the team server: x", TeamUsageLabels.serverError("x", EN))
        assertEquals("Delete session?", TeamUsageLabels.removeSessionTitle(EN))
        assertEquals("Remove member?", TeamUsageLabels.removeMemberTitle(EN))
        assertEquals("Delete", TeamUsageLabels.confirmSessionRemoval(EN))
        assertEquals("Remove", TeamUsageLabels.confirmRemoval(EN))
        assertEquals("Show members", TeamUsageLabels.expandAccount(EN))
        assertEquals("Hide members", TeamUsageLabels.collapseAccount(EN))
    }

    @Test
    fun `last seen says when the machine never reported`() {
        assertEquals("never reported", TeamUsageLabels.lastSeen(null, EN))
        assertEquals("nunca reportou", TeamUsageLabels.lastSeen(null, PT))
        assertEquals("report 27/10 12:04 BRT", TeamUsageLabels.lastSeen("27/10 12:04 BRT", EN))
        assertEquals("envio 27/10 12:04 BRT", TeamUsageLabels.lastSeen("27/10 12:04 BRT", PT))
    }
}
