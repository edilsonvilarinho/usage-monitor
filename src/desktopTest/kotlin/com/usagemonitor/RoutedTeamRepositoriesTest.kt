package com.usagemonitor

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Repositórios e seleção de time roteados pelo time escolhido (A06). */
class RoutedTeamRepositoriesTest {

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
    fun `routed usage repository calls the server of the team chosen by the selector`() = runTest {
        val registry = TeamRepositoryRegistry(remote()) {
            integrationsOf(listOf(team("a", "https://a.example"), team("b", "https://b.example")))
        }
        var selected: String? = "a"
        val routed = RoutedTeamUsageRepository(registry) { selected }

        assertTrue(routed.checkConnection().isSuccess)
        assertEquals("a.example", requestedHosts.last())

        selected = "b"
        assertTrue(routed.checkConnection().isSuccess)
        assertEquals("b.example", requestedHosts.last())
    }

    @Test
    fun `selection of a team that was removed falls back to the first active team`() {
        val selection = TeamViewSelection {
            integrationsOf(listOf(team("a", "https://a.example"), team("b", "https://b.example")))
        }

        selection.dashboardTeamId.value = "gone"
        assertEquals("a", selection.dashboardTeam())

        selection.dashboardTeamId.value = null
        assertEquals("a", selection.dashboardTeam())

        selection.settingsTeamId.value = "b"
        assertEquals("b", selection.settingsTeam())
    }

    @Test
    fun `without a resolved team the routed repository fails as not configured and makes no request`() = runTest {
        val registry = TeamRepositoryRegistry(remote()) { integrationsOf(emptyList()) }
        val routed = RoutedTeamUsageRepository(registry) { null }

        assertFalse(routed.checkConnection().isSuccess)
        assertTrue(requestedHosts.isEmpty())
    }

    @Test
    fun `routed clock offset reads the offset of the team chosen by the selector`() {
        val now = 1_000_000L
        val registry = TeamRepositoryRegistry(remote()) {
            integrationsOf(listOf(team("a", "https://a.example"), team("b", "https://b.example")))
        }
        registry.serverClockOffset("a").record(
            Instant.fromEpochMilliseconds(now + 2_000L),
            Instant.fromEpochMilliseconds(now)
        )
        var selected: String? = "b"
        val routed = RoutedTeamServerClockOffset(registry) { selected }

        assertEquals(0L, routed.offsetMillis)

        selected = "a"
        assertEquals(2_000L, routed.offsetMillis)
    }
}
