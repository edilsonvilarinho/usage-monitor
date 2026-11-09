package com.usagemonitor

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Um repositório e um relógio por time, sempre lidos do agregado corrente. */
class TeamRepositoryRegistryTest {

    private val requestedHosts = mutableListOf<String>()

    private fun remote(): RemoteTeamDataSource = RemoteTeamDataSource(
        HttpClient(
            MockEngine { request ->
                requestedHosts += request.url.host
                respond("", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        )
    )

    private fun team(id: String, serverUrl: String) = TeamIntegrationSettings(
        id = id,
        enabled = true,
        serverUrl = serverUrl,
        apiKey = "k-$id",
        alias = "Ana",
        deviceId = "device-1"
    )

    private fun integrationsOf(teams: List<TeamIntegrationSettings>) =
        TeamIntegrations(deviceId = "device-1", enabled = true, teams = teams)

    @Test
    fun `same team id returns the same usage repository`() {
        val registry = TeamRepositoryRegistry(remote()) { integrationsOf(listOf(team("a", "https://a.example"))) }

        assertSame(registry.usageRepository("a"), registry.usageRepository("a"))
    }

    @Test
    fun `same team id returns the same server clock offset and different ids do not share one`() {
        val registry = TeamRepositoryRegistry(remote()) {
            integrationsOf(listOf(team("a", "https://a.example"), team("b", "https://b.example")))
        }

        assertSame(registry.serverClockOffset("a"), registry.serverClockOffset("a"))
        assertNotSame(registry.serverClockOffset("a"), registry.serverClockOffset("b"))
    }

    @Test
    fun `usage repository follows the current settings of its team`() = runTest {
        var integrations = integrationsOf(listOf(team("a", "https://a.example")))
        val registry = TeamRepositoryRegistry(remote()) { integrations }
        val repository = registry.usageRepository("a")

        assertTrue(repository.checkConnection().isSuccess)
        assertTrue(requestedHosts.last() == "a.example")

        integrations = integrationsOf(listOf(team("a", "https://b.example")))
        assertTrue(repository.checkConnection().isSuccess)
        assertTrue(requestedHosts.last() == "b.example", "o repositório deve ler o servidor corrente do time")
    }

    @Test
    fun `unknown team id is unconfigured and makes no request`() = runTest {
        val registry = TeamRepositoryRegistry(remote()) { integrationsOf(listOf(team("a", "https://a.example"))) }

        assertFalse(registry.usageRepository("missing").checkConnection().isSuccess)
        assertTrue(requestedHosts.isEmpty())
    }
}
