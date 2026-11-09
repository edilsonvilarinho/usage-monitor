package com.usagemonitor

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations
import com.usagemonitor.domain.entity.TeamMemberIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ACCOUNT_KEY = "account-uuid-aaa"
private const val EMAIL = "ana@empresa.com"

/** Uma requisição que saiu para a rede: host, caminho, corpo e valores de todos os cabeçalhos. */
private class SentRequest(val host: String, val path: String, val body: String, val headerValues: List<String>)

/** Perfil e e-mail escolhidos chegam ao time selecionado, com a chave certa. */
class RoutedTeamRepositoriesProfileTest {

    private val sent = mutableListOf<SentRequest>()

    private fun remote(): RemoteTeamDataSource = RemoteTeamDataSource(
        HttpClient(
            MockEngine { request ->
                sent += SentRequest(
                    host = request.url.host,
                    path = request.url.encodedPath,
                    body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString(),
                    headerValues = request.headers.entries().flatMap { it.value }
                )
                respond("{}", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
    )

    private fun team(id: String, accountApiKeys: Map<String, String> = emptyMap()) = TeamIntegrationSettings(
        id = id,
        enabled = true,
        serverUrl = "https://$id.example",
        apiKey = "k-$id",
        alias = "Ana",
        deviceId = "device-1",
        participatingProfileIds = setOf("p1"),
        accountApiKeys = accountApiKeys
    )

    private fun registryOf(vararg teams: TeamIntegrationSettings) =
        TeamRepositoryRegistry(remote()) {
            TeamIntegrations(deviceId = "device-1", enabled = true, teams = teams.toList())
        }

    private val member = TeamMemberIdentity(deviceId = "device-1", alias = "Ana", hostName = "DESKTOP-A1")

    @Test
    fun `presenca roteada leva o e-mail e a chave da conta ao time escolhido`() = runTest {
        val routed = RoutedTeamUsageRepository(registryOf(team("a", mapOf("p1" to "chave-p1")), team("b"))) { "a" }

        routed.touchPresence(ACCOUNT_KEY, EMAIL, member, "p1").getOrThrow()

        val request = sent.single()
        assertEquals("a.example", request.host)
        assertEquals("/api/v1/presence", request.path)
        assertTrue(request.body.contains("\"accountEmail\":\"$EMAIL\""))
        assertTrue(request.headerValues.any { it.contains("chave-p1") })
        assertFalse(request.headerValues.any { it.contains("k-a") })
    }

    @Test
    fun `presenca roteada com perfil sem chave propria cai na chave do time selecionado`() = runTest {
        val routed = RoutedTeamUsageRepository(registryOf(team("a", mapOf("p1" to "chave-p1")), team("b"))) { "b" }

        routed.touchPresence(ACCOUNT_KEY, member, "p1").getOrThrow()

        val request = sent.single()
        assertEquals("b.example", request.host)
        assertTrue(request.headerValues.any { it.contains("k-b") })
        assertFalse(request.headerValues.any { it.contains("chave-p1") })
    }

    @Test
    fun `presenca roteada sem perfil nao usa a chave da conta`() = runTest {
        val routed = RoutedTeamUsageRepository(registryOf(team("a", mapOf("p1" to "chave-p1")))) { "a" }

        routed.touchPresence(ACCOUNT_KEY, member).getOrThrow()

        val request = sent.single()
        assertTrue(request.headerValues.any { it.contains("k-a") })
        assertFalse(request.headerValues.any { it.contains("chave-p1") })
    }

    @Test
    fun `claim roteada repassa o perfil e o e-mail ao time escolhido`() = runTest {
        val routed = RoutedTeamAdminRepository(registryOf(team("a", mapOf("p1" to "chave-p1")), team("b"))) { "a" }

        routed.claimKeyForAccount(ACCOUNT_KEY, EMAIL, "p1")

        val request = sent.single()
        assertEquals("a.example", request.host)
        assertEquals("/api/v1/claim", request.path)
        assertTrue(request.body.contains("\"accountEmail\":\"$EMAIL\""))
        assertTrue(request.headerValues.any { it.contains("chave-p1") })
        assertFalse(request.headerValues.any { it.contains("k-a") })
    }

    @Test
    fun `claim roteada sem perfil usa a chave do time`() = runTest {
        val routed = RoutedTeamAdminRepository(registryOf(team("a", mapOf("p1" to "chave-p1")))) { "a" }

        routed.claimKeyForAccount(ACCOUNT_KEY, EMAIL)

        val request = sent.single()
        assertTrue(request.headerValues.any { it.contains("k-a") })
        assertFalse(request.headerValues.any { it.contains("chave-p1") })
    }
}
