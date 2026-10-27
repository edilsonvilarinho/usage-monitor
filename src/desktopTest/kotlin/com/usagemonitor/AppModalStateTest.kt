package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.ui.help.HelpTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A geração de cada janela sobe a cada abertura — é ela que reativa uma janela
 * já visível —, e o assunto é gravado por quem abre.
 */
class AppModalStateTest {

    @Test
    fun `the bug report opens at start only when a crash is pending`() {
        assertTrue(AppModalState(bugReportOpenAtStart = true).isBugReportOpen)
        val modal = AppModalState(bugReportOpenAtStart = false)
        assertFalse(modal.isBugReportOpen)
        assertFalse(modal.isSettingsOpen)
        assertFalse(modal.isTeamKeysOpen)
        assertNull(modal.historySource)
    }

    @Test
    fun `opening again bumps the generation even when the window is already open`() {
        val modal = AppModalState(bugReportOpenAtStart = false)

        modal.openSettings()
        modal.openSettings()
        modal.openComparison()
        modal.openCodexCliSessions()
        modal.openAnalysis()
        modal.openAnalysis()

        assertTrue(modal.isSettingsOpen)
        assertEquals(2, modal.settingsOpenGeneration)
        assertTrue(modal.isComparisonOpen)
        assertEquals(1, modal.comparisonOpenGeneration)
        assertTrue(modal.isCodexCliSessionsOpen)
        assertEquals(1, modal.codexCliSessionsOpenGeneration)
        assertTrue(modal.isAnalysisOpen)
        assertEquals(2, modal.analysisOpenGeneration)
    }

    @Test
    fun `help keeps the requested topic and falls back to the first one`() {
        val modal = AppModalState(bugReportOpenAtStart = false)
        val topic = HelpTopic.entries.last()

        modal.openHelp(topic)
        assertEquals(topic, modal.helpTopic)
        modal.openHelp()

        assertNull(modal.helpTopic)
        assertTrue(modal.isHelpOpen)
        assertEquals(2, modal.helpOpenGeneration)
    }

    @Test
    fun `history and cli sessions record what they were opened about`() {
        val modal = AppModalState(bugReportOpenAtStart = false)

        modal.openHistory(ApiSource.CODEX)
        modal.openCliSessions(profileId = "work", profileLabel = "Trabalho")

        assertEquals(ApiSource.CODEX, modal.historySource)
        assertEquals(1, modal.historyOpenGeneration)
        assertTrue(modal.isCliSessionsOpen)
        assertEquals("work", modal.cliSessionsProfileId)
        assertEquals("Trabalho", modal.cliSessionsProfileLabel)
        assertEquals(1, modal.cliSessionsOpenGeneration)
    }

    @Test
    fun `team windows switch between one account and the admin overview`() {
        val modal = AppModalState(bugReportOpenAtStart = false)

        modal.openTeamUsage(accountLabel = "Conta", profileId = "default", isAdminOverview = false)
        assertEquals("Conta", modal.teamUsageAccountLabel)
        assertEquals("default", modal.teamUsageProfileId)
        assertFalse(modal.teamUsageIsAdminOverview)
        modal.openTeamUsage(accountLabel = null, profileId = null, isAdminOverview = true)
        assertNull(modal.teamUsageAccountLabel)
        assertNull(modal.teamUsageProfileId)
        assertTrue(modal.teamUsageIsAdminOverview)
        assertTrue(modal.isTeamUsageOpen)
        assertEquals(2, modal.teamUsageOpenGeneration)

        modal.openTeamPresence(accountLabel = "Conta", isAdminOverview = false)
        assertEquals("Conta", modal.teamPresenceAccountLabel)
        assertFalse(modal.teamPresenceIsAdminOverview)
        modal.openTeamPresence(accountLabel = null, isAdminOverview = true)
        assertNull(modal.teamPresenceAccountLabel)
        assertTrue(modal.teamPresenceIsAdminOverview)
        assertTrue(modal.isTeamPresenceOpen)
        assertEquals(2, modal.teamPresenceOpenGeneration)
    }
}
