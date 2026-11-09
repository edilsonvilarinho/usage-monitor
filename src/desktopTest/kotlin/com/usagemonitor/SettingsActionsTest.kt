package com.usagemonitor

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiState
import com.usagemonitor.presentation.ui.components.ProxyConnectionUiStatus
import com.usagemonitor.presentation.ui.components.SettingsField
import com.usagemonitor.presentation.ui.components.SettingsToast
import com.usagemonitor.presentation.ui.components.TeamConnectionUiState
import com.usagemonitor.presentation.ui.components.TeamConnectionUiStatus
import com.usagemonitor.presentation.ui.theme.AccountAccent
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * O que cada controle das Configurações grava, aplica e avisa — sobre o mesmo
 * `AppSettingsStore` do app, apontado para uma pasta e um nó temporários.
 */
class SettingsActionsTest {

    private var dataRootIsDefault = true
    private var anthropicChoice: File? = null
    private var codexChoice: File? = null
    private val harness = SettingsTestHarness { home -> SettingsTestHarness.writeDefaultAccount(home) }
    private val autoStartHome = File(harness.dir, "autostart-home")
    private var autoStartExecutable: String? = "/opt/usage-monitor/bin/usage-monitor"
    private val actions = SettingsActions(
        store = harness.store,
        breadcrumbs = harness.breadcrumbs,
        targets = harness.targets,
        shell = harness.shell,
        modal = harness.modal,
        feedback = harness.feedback,
        handOffScope = harness.scope,
        autoStartHost = {
            AutoStartHost(
                platform = AutoStartManager.Platform.LINUX,
                homeDirectory = autoStartHome,
                xdgConfigHome = null,
                runCommand = { AutoStartCommandResult(0, "") },
                executablePath = { autoStartExecutable },
                stableLauncherPath = { null },
                installRoot = { null }
            )
        },
        isDefaultDataRoot = { dataRootIsDefault },
        chooseAnthropicDirectory = { anthropicChoice },
        chooseCodexDirectory = { codexChoice }
    )

    private val store get() = harness.store
    private val lastToast get() = harness.feedback.toastEvent?.toast

    /** Uma pasta **não vazia** no lugar do arquivo: a troca atômica do arquivo de segredo falha. */
    private fun blockWrites(name: String) {
        // O `store` já pode ter gravado o arquivo (o time grava o `deviceId` na leitura).
        File(harness.dir, name).delete()
        File(harness.dir, "$name/keep").apply { parentFile.mkdirs() }.writeText("x")
    }

    @AfterTest
    fun cleanUp() {
        harness.close()
    }

    @Test
    fun `theme and language are applied and confirmed`() {
        actions.changeTheme(AppThemePreset.entries.last())
        assertEquals(SettingsToast.Saved(SettingsField.THEME), lastToast)
        actions.changeLanguage(AppLanguage.EN)

        assertEquals(AppThemePreset.entries.last(), harness.shell.themePreset)
        assertEquals(AppLanguage.EN, harness.shell.language)
        assertEquals(SettingsToast.Saved(SettingsField.LANGUAGE), lastToast)
    }

    @Test
    fun `sliders and switches change the shell without a toast`() {
        actions.changeUiScale(125)
        actions.changeWindowOpacity(80)
        actions.changeReducedMotion(true)
        actions.changeTrayUsageRing(true)

        assertEquals(125, harness.shell.uiScalePercent)
        assertEquals(80, harness.shell.windowOpacityPercent)
        assertTrue(harness.shell.reducedMotion)
        assertTrue(harness.shell.trayUsageRing)
        assertNull(harness.feedback.toastEvent)
    }

    @Test
    fun `auto start records what the system really has`() {
        actions.changeAutoStart(true)
        assertTrue(harness.shell.autoStartEnabled)
        assertTrue(File(autoStartHome, ".config/autostart/usage-monitor.desktop").isFile)
        assertEquals(SettingsToast.Saved(SettingsField.AUTO_START), lastToast)

        // A gravação falha: o estado volta ao que o sistema tem (ainda ligado) e o aviso diz que falhou.
        autoStartExecutable = null
        File(autoStartHome, ".config/autostart/usage-monitor.desktop").delete()
        actions.changeAutoStart(true)

        assertFalse(harness.shell.autoStartEnabled)
        assertEquals(SettingsToast.SaveFailed(SettingsField.AUTO_START), lastToast)
        assertTrue(harness.breadcrumbs.messages.any { message -> message.contains("executável do aplicativo não localizado") })
    }

    @Test
    fun `auto start is never written outside the default data root`() {
        dataRootIsDefault = false

        actions.changeAutoStart(true)

        assertFalse(File(autoStartHome, ".config").exists())
        assertNull(harness.feedback.toastEvent)
    }

    @Test
    fun `reporting a bug closes the settings first and opens the report after they fade`() {
        harness.modal.openSettings()

        actions.reportBug()
        assertFalse(harness.modal.isSettingsOpen)
        assertFalse(harness.modal.isBugReportOpen)
        harness.settle()

        assertTrue(harness.modal.isBugReportOpen)
        assertTrue(harness.breadcrumbs.messages.contains("abriu relatório de bug"))
    }

    @Test
    fun `alert settings are published and persisted`() {
        val updated = UsageAlertSettings.DEFAULT.copy(quotaAlertsEnabled = false)

        actions.changeAlertSettings(updated)

        assertEquals(updated, store.alertSettingsFlow.value)
        assertFalse(readPersistedAlertSettings(store.settings).quotaAlertsEnabled)
        assertEquals(SettingsToast.Saved(SettingsField.ALERTS), lastToast)
    }

    @Test
    fun `an invalid budget is ignored and a valid one is saved`() {
        actions.commitMonthlyBudget("abc")
        assertNull(harness.feedback.toastEvent)

        actions.commitMonthlyBudget("12,50")

        assertEquals(12_500_000L, harness.shell.monthlyBudgetMicros)
        assertEquals(12_500_000L, readPersistedBudgetMicros(store.settings))
        assertEquals(SettingsToast.Saved(SettingsField.ALERTS), lastToast)
    }

    @Test
    fun `toggling a source persists it and collects it now`() {
        actions.toggleApi(ApiSource.DEEPSEEK, checked = true)
        assertTrue(ApiSource.DEEPSEEK in store.enabledApis.value)
        actions.toggleApi(ApiSource.DEEPSEEK, checked = false)

        assertFalse(ApiSource.DEEPSEEK in store.enabledApis.value)
        assertEquals(listOf(ApiSource.DEEPSEEK, ApiSource.DEEPSEEK), harness.refreshed)
        assertEquals(SettingsToast.Saved(SettingsField.MONITORED_APIS), lastToast)
    }

    @Test
    fun `an api key is saved trimmed and removing it turns the source off`() {
        actions.toggleApi(ApiSource.DEEPSEEK, checked = true)

        assertTrue(actions.saveApiKey(ApiSource.DEEPSEEK, "  sk-1  "))
        assertEquals("sk-1", store.apiKeySettings.value.forSource(ApiSource.DEEPSEEK))
        assertEquals("sk-1", store.apiKeyDataSource.load().forSource(ApiSource.DEEPSEEK))
        assertEquals(SettingsToast.Saved(SettingsField.API_KEY), lastToast)

        assertTrue(actions.removeApiKey(ApiSource.DEEPSEEK))
        assertNull(store.apiKeySettings.value.forSource(ApiSource.DEEPSEEK))
        assertFalse(ApiSource.DEEPSEEK in store.enabledApis.value)
        assertEquals(ApiSource.DEEPSEEK, harness.refreshed.last())
    }

    @Test
    fun `an api key that cannot be written is reported and not published`() {
        // O arquivo de chaves está bloqueado: gravar e apagar falham.
        blockWrites("api-keys.json")

        assertFalse(actions.saveApiKey(ApiSource.DEEPSEEK, "sk-1"))
        assertNull(store.apiKeySettings.value.forSource(ApiSource.DEEPSEEK))
        assertEquals(SettingsToast.SaveFailed(SettingsField.API_KEY), lastToast)
        assertFalse(actions.removeApiKey(ApiSource.DEEPSEEK))

        assertTrue(harness.breadcrumbs.messages.any { message -> message.contains("salvar chave de API") })
        assertTrue(harness.breadcrumbs.messages.any { message -> message.contains("remover chave de API") })
        assertFalse(harness.breadcrumbs.messages.any { message -> message.contains("sk-1") }, "a chave nunca vai para a trilha")
    }

    @Test
    fun `anthropic accounts are edited in the registry and the enabled list follows`() {
        actions.toggleAnthropicProfile("default", checked = false)
        assertTrue(store.enabledAnthropicProfiles.value.none { profile -> profile.id == "default" })
        actions.toggleAnthropicProfile("default", checked = true)
        assertTrue(store.enabledAnthropicProfiles.value.any { profile -> profile.id == "default" })

        actions.renameAnthropicProfile("default", "Pessoal")
        assertEquals(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILE_LABEL), lastToast)
        actions.changeAnthropicProfileColor("default", AccountAccent.entries.first())
        actions.changeAnthropicProfileEmoji("default", AccountEmoji.entries.first())

        val record = store.profileRegistry.profiles.value.first { profile -> profile.id == "default" }
        assertEquals("Pessoal", record.label)
        assertEquals(AccountAccent.entries.first().name, record.color)
        assertEquals(AccountEmoji.entries.first().name, record.emoji)
        assertEquals(SettingsToast.Saved(SettingsField.ANTHROPIC_PROFILES), lastToast)
        assertEquals(listOf(ApiSource.ANTHROPIC, ApiSource.ANTHROPIC), harness.refreshed)
    }

    @Test
    fun `a manual anthropic account enters only when a directory is chosen`() {
        actions.addAnthropicProfile()
        val before = store.profileRegistry.profiles.value.size

        anthropicChoice = File(harness.dir, "work").apply { mkdirs() }
        actions.addAnthropicProfile()

        val added = store.profileRegistry.profiles.value
        assertEquals(before + 1, added.size)
        val manual = added.first { profile -> profile.origin == AnthropicProfileOrigin.MANUAL }

        actions.removeAnthropicProfile(manual.id)
        assertTrue(store.profileRegistry.profiles.value.none { profile -> profile.id == manual.id })
        actions.rescanAnthropicProfiles()
        assertEquals(listOf(ApiSource.ANTHROPIC, ApiSource.ANTHROPIC), harness.refreshed)
    }

    @Test
    fun `the expanded account editor toggles`() {
        actions.toggleProfileExpanded("default")
        assertEquals("default", harness.modal.expandedAnthropicProfileId)
        actions.toggleProfileExpanded("other")
        assertEquals("other", harness.modal.expandedAnthropicProfileId)
        actions.toggleProfileExpanded("other")
        assertNull(harness.modal.expandedAnthropicProfileId)
    }

    @Test
    fun `a codex directory without auth is refused with the reason`() {
        actions.addCodexProfile()
        assertNull(harness.modal.codexProfileError)
        assertTrue(harness.refreshed.isEmpty())

        codexChoice = File(harness.dir, "codex-empty").apply { mkdirs() }
        actions.addCodexProfile()

        assertTrue(harness.modal.codexProfileError.orEmpty().contains("auth.json"))
        assertEquals(listOf(ApiSource.CODEX), harness.refreshed)
    }

    @Test
    fun `a codex account is added enabled, toggled and removed`() {
        codexChoice = File(harness.dir, "codex-work").apply { mkdirs() }
        File(codexChoice, "auth.json").writeText("{}")

        actions.addCodexProfile()
        assertNull(harness.modal.codexProfileError)
        val id = store.enabledCodexProfiles.value.single().id
        assertTrue(id.startsWith("codex-"))

        actions.toggleCodexProfile(id, checked = false)
        assertTrue(store.enabledCodexProfiles.value.isEmpty())
        actions.toggleCodexProfile(id, checked = true)
        actions.removeCodexProfile(id)

        assertTrue(store.enabledCodexProfiles.value.isEmpty())
        assertTrue(store.codexProfileRegistry.profiles.value.isEmpty())
        assertEquals(List(4) { ApiSource.CODEX }, harness.refreshed)
    }

    @Test
    fun `changing the server or turning the team on clears a stale check`() {
        harness.feedback.setTeamConnection(TeamIntegrations.LEGACY_TEAM_ID, TeamConnectionUiState(TeamConnectionUiStatus.OK, "velho"))

        actions.changeTeamEnabled(true)
        assertEquals(TeamConnectionUiState(), harness.feedback.teamConnection(TeamIntegrations.LEGACY_TEAM_ID))
        assertEquals(SettingsToast.Saved(SettingsField.TEAM_INTEGRATION), lastToast)

        harness.feedback.setTeamConnection(TeamIntegrations.LEGACY_TEAM_ID, TeamConnectionUiState(TeamConnectionUiStatus.OK, "velho"))
        actions.changeTeamServerUrl(TeamIntegrations.LEGACY_TEAM_ID, "https://team.example")

        assertEquals(TeamConnectionUiState(), harness.feedback.teamConnection(TeamIntegrations.LEGACY_TEAM_ID))
        assertEquals("https://team.example", store.teamSettingsDataSource.load().serverUrl)
        assertEquals(SettingsToast.Saved(SettingsField.TEAM_SERVER), lastToast)
    }

    @Test
    fun `turning the team switch on turns the aggregate on and the team active in memory and on disk`() {
        updateTeamSettings(store, TeamIntegrations.LEGACY_TEAM_ID) { current ->
            current.copy(enabled = false, serverUrl = "https://team.example", apiKey = "key-1", alias = "eu")
        }

        actions.changeTeamEnabled(true)

        assertTrue(store.teamIntegrationsFlow.value.enabled)
        assertTrue(store.teamSettingsDataSource.loadAll().enabled)
        assertTrue(store.teamIntegrationsFlow.value.team(TeamIntegrations.LEGACY_TEAM_ID)?.isActive == true)
    }

    @Test
    fun `the enabled flag of a team transform reaches the aggregate`() {
        updateTeamSettings(store, TeamIntegrations.LEGACY_TEAM_ID) { current ->
            current.copy(enabled = true, serverUrl = "https://team.example", apiKey = "key-1")
        }

        assertTrue(store.teamIntegrationsFlow.value.enabled)
        assertTrue(store.teamSettingsDataSource.loadAll().enabled)
    }

    @Test
    fun `a new team key is checked and the identity is sent at once`() {
        actions.changeTeamApiKey(TeamIntegrations.LEGACY_TEAM_ID, "key-1")
        harness.settle()

        assertEquals("key-1", store.teamSettingsFlow.value.apiKey)
        assertEquals(1, harness.teamSyncs)
        assertEquals(TeamConnectionUiStatus.OK, harness.feedback.teamConnection(TeamIntegrations.LEGACY_TEAM_ID).status)
        assertEquals(SettingsToast.Saved(SettingsField.TEAM_KEY), lastToast)
    }

    @Test
    fun `a team key that cannot be written is not checked`() {
        blockWrites("team.json")
        harness.feedback.setTeamConnection(TeamIntegrations.LEGACY_TEAM_ID, TeamConnectionUiState(TeamConnectionUiStatus.OK, "velho"))

        actions.changeTeamApiKey(TeamIntegrations.LEGACY_TEAM_ID, "key-1")

        assertEquals(TeamConnectionUiState(), harness.feedback.teamConnection(TeamIntegrations.LEGACY_TEAM_ID))
        assertEquals(0, harness.teamSyncs)
        assertEquals(SettingsToast.SaveFailed(SettingsField.TEAM_KEY), lastToast)
        assertTrue(harness.breadcrumbs.messages.any { message -> message.contains("salvar configuração TEAM_KEY") })
    }

    @Test
    fun `a saved alias cannot be erased and a new one is sent at once`() {
        actions.changeTeamAlias(TeamIntegrations.LEGACY_TEAM_ID, "ana")
        assertEquals(1, harness.teamSyncs)
        assertEquals(SettingsToast.Saved(SettingsField.TEAM_ALIAS), lastToast)

        actions.changeTeamAlias(TeamIntegrations.LEGACY_TEAM_ID, "  ")

        assertEquals("ana", store.teamSettingsFlow.value.alias)
        assertEquals(SettingsToast.TeamAliasRequired, lastToast)
        assertEquals(1, harness.teamSyncs)
    }

    @Test
    fun `marking an account checks the key and unmarking does not`() {
        actions.changeTeamProfileParticipation(TeamIntegrations.LEGACY_TEAM_ID, "default", participates = true)
        harness.settle()
        assertEquals(setOf("default"), store.teamSettingsFlow.value.participatingProfileIds)
        assertEquals(TeamConnectionUiStatus.OK, harness.feedback.teamConnection(TeamIntegrations.LEGACY_TEAM_ID).status)
        harness.feedback.setTeamConnection(TeamIntegrations.LEGACY_TEAM_ID, TeamConnectionUiState())

        actions.changeTeamProfileParticipation(TeamIntegrations.LEGACY_TEAM_ID, "default", participates = false)

        assertTrue(store.teamSettingsFlow.value.participatingProfileIds.isEmpty())
        assertEquals(TeamConnectionUiState(), harness.feedback.teamConnection(TeamIntegrations.LEGACY_TEAM_ID))
        assertEquals(SettingsToast.Saved(SettingsField.TEAM_ACCOUNTS), lastToast)
    }

    @Test
    fun `admin mode is entered by token, opens the keys and exits clearing it`() {
        harness.feedback.setTeamAdminConnection(TeamIntegrations.LEGACY_TEAM_ID, TeamConnectionUiState(TeamConnectionUiStatus.OK, "velho"))
        actions.changeTeamAdminToken(TeamIntegrations.LEGACY_TEAM_ID, "adm")
        assertEquals("adm", store.teamSettingsFlow.value.adminToken)
        assertEquals(TeamConnectionUiState(), harness.feedback.teamAdminConnection(TeamIntegrations.LEGACY_TEAM_ID))

        actions.openTeamKeysManager(TeamIntegrations.LEGACY_TEAM_ID)
        assertTrue(harness.modal.isTeamKeysOpen)
        assertEquals(1, harness.teamKeysOpened)
        assertTrue(harness.breadcrumbs.messages.contains("abriu chaves das contas (admin)"))

        actions.exitTeamAdminMode(TeamIntegrations.LEGACY_TEAM_ID)
        assertEquals("", store.teamSettingsDataSource.load().adminToken)
        assertFalse(harness.modal.isTeamKeysOpen)
        assertEquals(SettingsToast.Saved(SettingsField.TEAM_ADMIN_TOKEN), lastToast)
    }

    @Test
    fun `proxy address changes clear the check and credentials do not`() {
        val checked = ProxyConnectionUiState(ProxyConnectionUiStatus.OK, "ok")
        harness.feedback.proxyConnection = checked
        actions.changeProxyUsername("user")
        actions.changeProxyPassword("secret")
        assertEquals(checked, harness.feedback.proxyConnection)

        actions.changeProxyHost("proxy.local")
        assertEquals(ProxyConnectionUiState(), harness.feedback.proxyConnection)
        actions.changeProxyPort("abc")
        assertEquals(0, store.proxySettingsFlow.value.port)
        actions.changeProxyPort("3128")
        actions.changeProxyUseEnvironment(true)

        val saved: ProxySettings = store.proxySettingsDataSource.load()
        assertEquals("proxy.local", saved.host)
        assertEquals(3128, saved.port)
        assertEquals("user", saved.username)
        assertTrue(saved.useEnvironmentProxy)
        assertEquals(SettingsToast.Saved(SettingsField.NETWORK_PROXY), lastToast)
    }

    @Test
    fun `team write updates the aggregate and the derived flow together`() {
        assertEquals(true, updateTeamSettings(store) { current -> current.copy(alias = "ana") })
        assertEquals("ana", store.teamIntegrationsFlow.value.teams.first().alias)
        assertEquals("ana", store.teamSettingsFlow.value.alias)
        assertEquals("ana", store.teamSettingsDataSource.loadAll().teams.first().alias)
    }

    @Test
    fun `write on an empty aggregate creates the legacy team and keeps its usage repository`() {
        val registry = TeamRepositoryRegistry(
            RemoteTeamDataSource(HttpClient(MockEngine { respond("", HttpStatusCode.OK) }))
        ) { store.teamIntegrationsFlow.value }
        fun firstTeamId() = store.teamIntegrationsFlow.value.teams.firstOrNull()?.id ?: "team-legacy"
        val before = registry.usageRepository(firstTeamId())

        assertTrue(updateTeamSettings(store) { current -> current.copy(alias = "ana") })

        assertEquals("team-legacy", store.teamIntegrationsFlow.value.teams.single().id)
        assertSame(before, registry.usageRepository(firstTeamId()))
    }

    @Test
    fun `team and proxy writes report a refused save`() {
        blockWrites("team.json")
        blockWrites("proxy.json")

        assertFalse(updateTeamSettings(store) { current -> current.copy(alias = "x") })
        assertEquals("x", store.teamSettingsFlow.value.alias, "a tela reflete a digitação mesmo sem gravar")
        actions.changeProxyHost("proxy.local")

        assertEquals(SettingsToast.SaveFailed(SettingsField.NETWORK_PROXY), lastToast)
        assertNotNull(harness.breadcrumbs.messages.firstOrNull { message -> message.contains("NETWORK_PROXY") })
    }

    @Test
    fun `moving an account to another team takes it off the previous one`() {
        store.publishTeamIntegrations(
            TeamIntegrations(
                teams = listOf(
                    TeamIntegrationSettings(id = "team-a", participatingProfileIds = setOf("default")),
                    TeamIntegrationSettings(id = "team-b")
                )
            )
        )

        actions.changeTeamProfileParticipation("team-b", "default", participates = true)

        assertEquals(emptySet(), store.teamIntegrationsFlow.value.team("team-a")?.participatingProfileIds)
        assertEquals(setOf("default"), store.teamIntegrationsFlow.value.team("team-b")?.participatingProfileIds)
    }

    @Test
    fun `changing one team leaves the other team untouched`() {
        store.publishTeamIntegrations(
            TeamIntegrations(
                teams = listOf(
                    TeamIntegrationSettings(id = "team-a", serverUrl = "https://a.example"),
                    TeamIntegrationSettings(id = "team-b", serverUrl = "https://b.example")
                )
            )
        )

        actions.changeTeamServerUrl("team-a", "https://new.example")

        assertEquals("https://new.example", store.teamIntegrationsFlow.value.team("team-a")?.serverUrl)
        assertEquals("https://b.example", store.teamIntegrationsFlow.value.team("team-b")?.serverUrl)
    }

    @Test
    fun `adding teams gives unique ids, numbered names and opens the last one`() {
        val first = actions.addTeam()
        val second = actions.addTeam()

        assertTrue(first.startsWith("team-"))
        assertFalse(first == second)
        assertEquals(listOf("Time 1", "Time 2"), store.teamIntegrationsFlow.value.teams.map { it.name })
        assertEquals(second, actions.selectedTeamId)
    }

    @Test
    fun `removing the open team selects the first remaining one`() {
        store.publishTeamIntegrations(
            TeamIntegrations(teams = listOf(TeamIntegrationSettings(id = "team-a"), TeamIntegrationSettings(id = "team-b")))
        )
        actions.selectTeam("team-b")

        actions.removeTeam("team-b")

        assertEquals(listOf("team-a"), store.teamIntegrationsFlow.value.teams.map { it.id })
        assertEquals("team-a", actions.selectedTeamId)
    }
}
