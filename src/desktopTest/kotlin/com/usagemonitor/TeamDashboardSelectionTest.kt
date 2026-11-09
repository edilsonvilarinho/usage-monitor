package com.usagemonitor

import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TeamDashboardSelectionTest {

    @Test
    fun `opcoes do seletor seguem a ordem dos times ativos`() {
        val integrations = integrations(
            team("t1", "Alfa", "p-a"),
            team("t2", "Desligado", "p-d", enabled = false),
            team("t3", "Beta", "p-b")
        )

        val options = teamSelectorOptions(integrations)

        assertEquals(listOf("t1", "t3"), options.map { it.id })
        assertEquals(listOf("Alfa", "Beta"), options.map { it.name })
    }

    @Test
    fun `seletor so aparece com mais de um time ativo`() {
        val um = teamSelectorOptions(integrations(team("t1", "Alfa", "p-a")))
        val dois = teamSelectorOptions(integrations(team("t1", "Alfa", "p-a"), team("t2", "Beta", "p-b")))

        assertFalse(teamSelectorVisible(um))
        assertTrue(teamSelectorVisible(dois))
    }

    @Test
    fun `card abre o time que tem a conta como participante`() {
        val integrations = integrations(team("t1", "Alfa", "p-a"), team("t2", "Beta", "p-b"))

        assertEquals("t2", dashboardTeamIdFor("p-b", integrations))
        assertEquals(null, dashboardTeamIdFor("p-x", integrations))
    }

    @Test
    fun `troca abre a primeira conta do time na ordem dos alvos`() {
        val alfa = team("t1", "Alfa", "p-a", "p-b")
        val targets = listOf(
            TeamSyncTarget(profileId = "p-x", accountKey = "acc-x", accountEmail = "x@x.dev"),
            TeamSyncTarget(profileId = "p-b", accountKey = "acc-b", accountEmail = "b@x.dev"),
            TeamSyncTarget(profileId = "p-a", accountKey = "acc-a", accountEmail = "a@x.dev")
        )

        assertEquals(TeamAccountSwitch.Account("acc-b", "b@x.dev", "p-b"), teamAccountSwitchFor(alfa, targets))
    }

    @Test
    fun `overview admin mantem o time atual se for admin senao o primeiro admin`() {
        val integrations = integrations(
            team("t1", "Alfa", "p-a"),
            team("t2", "Beta", "p-b", admin = true),
            team("t3", "Gama", "p-g", admin = true)
        )

        assertEquals("t2", adminDashboardTeamIdFor("t1", integrations))
        assertEquals("t3", adminDashboardTeamIdFor("t3", integrations))
        assertEquals("t2", adminDashboardTeamIdFor(null, integrations))
    }

    @Test
    fun `admin sem conta propria abre todas as contas`() {
        val admin = team("t1", "Alfa", admin = true)
        val targets = listOf(TeamSyncTarget(profileId = "p-x", accountKey = "acc-x", accountEmail = null))

        assertEquals(TeamAccountSwitch.AllAccounts, teamAccountSwitchFor(admin, targets))
    }

    @Test
    fun `time sem admin e sem conta nao abre nada`() {
        val comum = team("t1", "Alfa")
        val targets = listOf(TeamSyncTarget(profileId = "p-x", accountKey = "acc-x", accountEmail = null))

        assertEquals(TeamAccountSwitch.None, teamAccountSwitchFor(comum, targets))
    }

    @Test
    fun `time removido ou desligado cai para o primeiro ativo`() {
        val semSelecionado = integrations(team("t1", "Alfa", "p-a"), team("t2", "Beta", "p-b"))
        val desligado = integrations(team("t1", "Alfa", "p-a", enabled = false), team("t2", "Beta", "p-b"))

        assertEquals("t1", resolveDashboardTeamId("sumiu", semSelecionado))
        assertEquals("t2", resolveDashboardTeamId("t1", desligado))
    }

    private fun team(
        id: String,
        name: String,
        vararg profiles: String,
        enabled: Boolean = true,
        admin: Boolean = false
    ) = TeamIntegrationSettings(
        enabled = true,
        serverUrl = "https://team.example",
        apiKey = if (enabled) "chave-$id" else "",
        alias = "apelido-$id",
        deviceId = "dispositivo-$id",
        participatingProfileIds = profiles.toSet(),
        adminToken = if (admin) "admin-$id" else "",
        id = id,
        name = name
    )

    private fun integrations(vararg teams: TeamIntegrationSettings) =
        TeamIntegrations(deviceId = "dispositivo", enabled = true, teams = teams.toList())
}
