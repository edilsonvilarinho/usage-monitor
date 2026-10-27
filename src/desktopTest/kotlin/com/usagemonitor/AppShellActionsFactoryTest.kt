package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.viewmodel.UsageExportWriter
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AppShellActionsFactoryTest {

    private class RecordingBreadcrumbs : BreadcrumbRecorder {
        val steps = mutableListOf<Pair<BreadcrumbCategory, String>>()
        override fun record(category: BreadcrumbCategory, message: String) {
            steps += category to message
        }
        override fun read(limit: Int): List<Breadcrumb> = emptyList()
    }

    private class RecordingTargets : ShellActionTargets {
        val calls = mutableListOf<String>()
        override fun refreshAll() { calls += "refreshAll" }
        override fun openComparison() { calls += "comparison" }
        override fun openHistory(source: ApiSource, accountKey: UsageAccountKey?) { calls += "history ${source.name} ${accountKey?.providerAccountId}" }
        override fun openCliSessions(profileId: String, profileLabel: String?, quotaWindows: CliQuotaWindows) {
            calls += "cli $profileId $profileLabel ${quotaWindows.fiveHourEndsAt}"
        }
        override fun openCodexCliSessions() { calls += "codex" }
        override fun openTeamUsageForAccount(accountKey: String, accountLabel: String, quotaWindows: CliQuotaWindows) {
            calls += "teamUsage $accountKey $accountLabel ${quotaWindows.fiveHourEndsAt}"
        }
        override fun openTeamUsageForAllAccounts() { calls += "teamUsage all" }
        override fun openTeamPresenceForAccount(accountKey: String, accountLabel: String) { calls += "presence $accountKey $accountLabel" }
        override fun openTeamPresenceForAllAccounts() { calls += "presence all" }
        override fun openAnalysis(targetKey: String, accountLabel: String) { calls += "analysis $targetKey $accountLabel" }
        override fun requestTeamSync() { calls += "sync" }
    }

    private val breadcrumbs = RecordingBreadcrumbs()
    private val targets = RecordingTargets()
    private val modal = AppModalState(bugReportOpenAtStart = false)
    private val written = mutableListOf<UsageExportRequest>()
    private val writer = UsageExportWriter { request ->
        written += request
        "C:/out/${request.suggestedFileName}"
    }
    private val fiveHourEnd = Instant.parse("2026-10-27T17:00:00Z")
    private val work = UsageTargetKey(ApiSource.ANTHROPIC, "work")
    private val workContext = UsageAccountContext(UsageAccountKey(ApiSource.ANTHROPIC, "uuid-1"), "me@example.com", "Acme")
    private val workStats = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = work,
        apiName = "Anthropic",
        quotas = listOf(QuotaInfo("5h", used = 10, total = 100, periodEndAt = fiveHourEnd, unit = UsageUnit.PERCENTAGE)),
        accountContext = workContext,
        profileLabel = "Trabalho"
    )
    private val records = listOf(
        AnthropicProfileRecord(id = "work", label = "Trabalho", configDirectory = "/x", enabled = true, origin = AnthropicProfileOrigin.MANUAL)
    )
    private val resolution = AnthropicProfileResolution(
        enabledProfiles = emptyList(),
        inspections = mapOf(
            "work" to AnthropicProfileInspection(AnthropicProfileInspectionStatus.READY, accountContext = workContext),
            "nouuid" to AnthropicProfileInspection(AnthropicProfileInspectionStatus.INCOMPLETE)
        ),
        duplicateProfileIds = emptySet()
    )
    private val adminTeam = TeamIntegrationSettings(enabled = true, serverUrl = "https://team.example", adminToken = "t")

    private fun actions(
        state: UiState = UiState.Success(listOf(workStats)),
        team: TeamIntegrationSettings = TeamIntegrationSettings()
    ) = buildShellActions(
        breadcrumbs, targets, writer, modal, records, resolution, state, team,
        clock = object : Clock { override fun now() = Instant.parse("2026-10-27T02:30:00Z") }
    )

    private val screens get() = breadcrumbs.steps.filter { step -> step.first == BreadcrumbCategory.NAVIGATION }.map { step -> step.second }

    @Test
    fun `the footer actions open their window and name the screen in the trail`() {
        val shell = actions()

        shell.refreshAll()
        shell.openSettings()
        shell.openHelp()
        shell.openComparison()

        assertEquals(listOf("refreshAll", "comparison"), targets.calls)
        assertTrue(modal.isSettingsOpen)
        assertTrue(modal.isHelpOpen)
        assertTrue(modal.isComparisonOpen)
        assertEquals(listOf("abriu Configurações", "abriu Ajuda", "abriu Comparar modelos e APIs"), screens)
    }

    @Test
    fun `admin overviews exist only in admin mode`() {
        val member = actions()
        assertNull(member.openAdminOverview)
        assertNull(member.openTeamPresenceOverview)

        val admin = actions(team = adminTeam)
        assertNotNull(admin.openAdminOverview).invoke()
        assertNotNull(admin.openTeamPresenceOverview).invoke()

        assertEquals(listOf("teamUsage all", "presence all"), targets.calls)
        assertTrue(modal.teamUsageIsAdminOverview)
        assertTrue(modal.teamPresenceIsAdminOverview)
        assertEquals(listOf("abriu visão global do time (admin)", "abriu presença global do time (admin)"), screens)
    }

    @Test
    fun `cli sessions open for the profile with its label and five hour window`() {
        actions().openCliSessions(work)

        assertEquals(listOf("cli work Trabalho $fiveHourEnd"), targets.calls)
        assertEquals("work", modal.cliSessionsProfileId)
        assertEquals("Trabalho", modal.cliSessionsProfileLabel)
        assertEquals(listOf("abriu sessões CLI da máquina"), screens)
    }

    @Test
    fun `team usage and presence open for the account and anticipate the sync`() {
        val shell = actions()

        shell.openTeamUsage(work)
        shell.openTeamPresence(work)

        val label = "me@example.com — Acme"
        assertEquals(listOf("teamUsage uuid-1 $label $fiveHourEnd", "sync", "presence uuid-1 $label", "sync"), targets.calls)
        assertEquals(label, modal.teamUsageAccountLabel)
        assertEquals("work", modal.teamUsageProfileId)
        assertEquals(label, modal.teamPresenceAccountLabel)
        assertEquals(listOf("abriu uso do time", "abriu presença do time"), screens)
        assertTrue(screens.none { screen -> screen.contains("me@example.com") }, "a trilha nunca leva a conta")
    }

    @Test
    fun `an account without uuid opens nothing`() {
        val shell = actions()

        shell.openTeamUsage(UsageTargetKey(ApiSource.ANTHROPIC, "nouuid"))
        shell.openTeamPresence(UsageTargetKey(ApiSource.ANTHROPIC, "unknown"))

        assertTrue(targets.calls.isEmpty())
        assertEquals(false, modal.isTeamUsageOpen)
        assertEquals(false, modal.isTeamPresenceOpen)
        assertTrue(breadcrumbs.steps.isEmpty())
    }

    @Test
    fun `history, codex sessions and analysis open with the snapshot label`() {
        val shell = actions()

        shell.openHistory(ApiSource.ANTHROPIC, workContext.key)
        shell.openCodexCliSessions(UsageTargetKey(ApiSource.CODEX))
        shell.openAnalysis(work)
        shell.openAnalysis(UsageTargetKey(ApiSource.CODEX))

        assertEquals(
            listOf("history ANTHROPIC uuid-1", "codex", "analysis ANTHROPIC:work Anthropic · Trabalho", "analysis CODEX Codex"),
            targets.calls
        )
        assertEquals(ApiSource.ANTHROPIC, modal.historySource)
        assertTrue(modal.isCodexCliSessionsOpen)
        assertTrue(modal.isAnalysisOpen)
        assertEquals(
            listOf(
                "abriu histórico de ANTHROPIC",
                "abriu sessões Codex CLI",
                "abriu análise da sessão de ANTHROPIC",
                "abriu análise da sessão de CODEX"
            ),
            screens
        )
    }

    @Test
    fun `without a reading the cli window has no five hour end`() {
        actions(state = UiState.Loading).openCliSessions(work)

        assertEquals(listOf("cli work Trabalho null"), targets.calls)
    }

    @Test
    fun `the dashboard export is written with the day in BRT and failures go to the trail`() {
        val shell = actions()

        val path = runBlocking { shell.exportSnapshot(listOf(workStats)) }
        shell.onExportFailure(IllegalStateException("disco cheio"))

        // 02:30 UTC ainda é dia 26 em São Paulo.
        assertEquals("C:/out/usage-monitor-dashboard-2026-10-26.csv", path)
        assertEquals("usage-monitor-dashboard-2026-10-26.csv", written.single().suggestedFileName)
        assertTrue(breadcrumbs.steps.any { step -> step.second.contains("exportar retrato do dashboard") })
    }
}
