package com.usagemonitor.domain

import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamIntegrationsTest {

    /** Time com `deviceId` vazio: quem o preenche é o agregado, como em produção. */
    private fun team(
        id: String,
        profiles: Set<String> = emptySet(),
        enabled: Boolean = true,
        apiKey: String = "chave-$id",
        adminToken: String = ""
    ) = TeamIntegrationSettings(
        enabled = enabled,
        serverUrl = "https://time.example",
        apiKey = apiKey,
        alias = "ana",
        id = id,
        name = "Time $id",
        participatingProfileIds = profiles,
        adminToken = adminToken
    )

    private fun aggregate(vararg teams: TeamIntegrationSettings, enabled: Boolean = true) =
        TeamIntegrations(deviceId = "dev-1", enabled = enabled, teams = teams.toList())

    @Test
    fun `id e nome do time tem default vazio e aparecem no toString`() {
        assertEquals("", TeamIntegrationSettings().id)
        assertEquals("", TeamIntegrationSettings().name)

        val printed = team("a").toString()

        assertTrue(printed.contains("id=a"), printed)
        assertTrue(printed.contains("name=Time a"), printed)
    }

    @Test
    fun `toString do time continua sem chave nem token de admin`() {
        val printed = team("a", apiKey = "segredo-chave-123", adminToken = "segredo-admin-456").toString()

        assertFalse(printed.contains("segredo-chave-123"))
        assertFalse(printed.contains("segredo-admin-456"))
    }

    @Test
    fun `cada time exposto carrega o deviceId do agregado`() {
        val integrations = TeamIntegrations(deviceId = "dev-1", enabled = true, teams = listOf(team("a"), team("b")))

        assertEquals(listOf("dev-1", "dev-1"), integrations.teams.map { it.deviceId })
        assertEquals("dev-1", integrations.deviceId)
    }

    @Test
    fun `cada time exposto carrega o enabled do agregado sem pausa por time`() {
        val enabledAggregate = TeamIntegrations(deviceId = "dev-1", enabled = true, teams = listOf(team("a", enabled = false)))
        val disabledAggregate = TeamIntegrations(deviceId = "dev-1", enabled = false, teams = listOf(team("a", enabled = true)))

        assertEquals(listOf(true), enabledAggregate.teams.map { it.enabled })
        assertEquals(listOf(false), disabledAggregate.teams.map { it.enabled })
    }

    @Test
    fun `agregado desligado nao tem time ativo, nem conta em time, nem admin`() {
        val integrations = aggregate(
            team("a", profiles = setOf("conta-1"), adminToken = "admin-a"),
            enabled = false
        )

        assertEquals(emptyList(), integrations.activeTeams.map { it.id })
        assertNull(integrations.teamFor("conta-1"))
        assertEquals(emptyList(), integrations.adminTeams.map { it.id })
    }

    @Test
    fun `teams mantem a ordem de insercao`() {
        val integrations = aggregate(team("b"), team("a"), team("c"))

        assertEquals(listOf("b", "a", "c"), integrations.teams.map { it.id })
    }

    @Test
    fun `team devolve o time pelo id ou null`() {
        val integrations = aggregate(team("a"), team("b"))

        assertEquals("Time b", integrations.team("b")?.name)
        assertNull(integrations.team("inexistente"))
    }

    @Test
    fun `teamFor acha o time em que a conta participa`() {
        val integrations = aggregate(team("a", profiles = setOf("conta-1")), team("b", profiles = setOf("conta-2")))

        assertEquals("a", integrations.teamFor("conta-1")?.id)
        assertEquals("b", integrations.teamFor("conta-2")?.id)
        assertNull(integrations.teamFor("conta-3"))
        assertNull(integrations.teamFor(null))
    }

    @Test
    fun `upsert substitui pelo id mantendo a posicao`() {
        val integrations = aggregate(team("a"), team("b"), team("c"))

        val updated = integrations.upsert(team("b").copy(name = "Renomeado"))

        assertEquals(listOf("a", "b", "c"), updated.teams.map { it.id })
        assertEquals("Renomeado", updated.team("b")?.name)
    }

    @Test
    fun `upsert acrescenta no fim quando o id e novo`() {
        val integrations = aggregate(team("a"))

        val appended = integrations.upsert(team("z"))

        assertEquals(listOf("a", "z"), appended.teams.map { it.id })
    }

    @Test
    fun `upsert regrava o deviceId do agregado no time recebido`() {
        val integrations = aggregate(team("a"))

        val updated = integrations.upsert(team("a").copy(deviceId = "outro-dispositivo"))

        assertEquals(listOf("dev-1"), updated.teams.map { it.deviceId })
    }

    @Test
    fun `remove tira o time pelo id e mantem os demais na ordem`() {
        val integrations = aggregate(team("a"), team("b"), team("c"))

        val removed = integrations.remove("b")

        assertEquals(listOf("a", "c"), removed.teams.map { it.id })
    }

    @Test
    fun `conta movida do time A para o B some de A`() {
        val integrations = aggregate(team("a", profiles = setOf("conta-1")), team("b"))

        val moved = integrations.withParticipation(teamId = "b", profileId = "conta-1", participating = true)

        assertFalse(moved.team("a")!!.participatingProfileIds.contains("conta-1"))
        assertTrue(moved.team("b")!!.participatingProfileIds.contains("conta-1"))
        assertEquals("b", moved.teamFor("conta-1")?.id)
    }

    @Test
    fun `participating false tira a conta so do time indicado`() {
        val integrations = aggregate(team("a", profiles = setOf("conta-1", "conta-2")), team("b", profiles = setOf("conta-1")))

        val left = integrations.withParticipation(teamId = "a", profileId = "conta-1", participating = false)

        assertEquals(setOf("conta-2"), left.team("a")!!.participatingProfileIds)
        assertEquals(setOf("conta-1"), left.team("b")!!.participatingProfileIds)
    }

    @Test
    fun `withParticipation em time inexistente deixa o agregado inalterado`() {
        val integrations = aggregate(team("a", profiles = setOf("conta-1")))

        val result = integrations.withParticipation(teamId = "fantasma", profileId = "conta-1", participating = true)

        assertEquals(integrations, result)
        assertEquals(setOf("conta-1"), result.team("a")!!.participatingProfileIds)
    }

    @Test
    fun `activeTeams e adminTeams filtram na ordem de teams`() {
        val integrations = aggregate(
            team("a"),
            team("b", apiKey = ""),
            team("c", adminToken = "admin-c"),
            team("d")
        )

        assertEquals(listOf("a", "c", "d"), integrations.activeTeams.map { it.id })
        assertEquals(listOf("c"), integrations.adminTeams.map { it.id })
    }

    @Test
    fun `toString do agregado nao expoe chave nem token de admin`() {
        val integrations = aggregate(team("a", apiKey = "segredo-chave-123", adminToken = "segredo-admin-456"))

        val printed = integrations.toString()

        assertFalse(printed.contains("segredo-chave-123"))
        assertFalse(printed.contains("segredo-admin-456"))
        assertTrue(printed.contains("dev-1"), printed)
    }
}
