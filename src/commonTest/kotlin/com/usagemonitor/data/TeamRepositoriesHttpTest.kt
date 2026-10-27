package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.repository.TeamAdminRepositoryImpl
import com.usagemonitor.data.repository.TeamUsageRepositoryImpl
import com.usagemonitor.domain.entity.TeamAccountEmailSource
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamMemberIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val MEMBER_SETTINGS = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://team.local/",
    apiKey = "chave-de-time",
    alias = "ana",
    deviceId = "device-1"
)

/** Só o token de administração: lê e administra, mas não envia consumo. */
private val ADMIN_ONLY_SETTINGS = TeamIntegrationSettings(
    enabled = true,
    serverUrl = "http://team.local",
    adminToken = "token-de-admin"
)

private const val KEY_JSON =
    """{"id":"k1","label":"time","key":"tk_cru","keyPrefix":"tk_","maxAccounts":2,
    "accounts":["conta-1"],"accountDetails":[{"accountKey":"conta-1","accountEmail":"a@b.com","authorized":false}],
    "createdAt":1000,"revokedAt":0,"lastUsedAt":null}"""

/** Resposta por rota; rota sem entrada responde 404, como um servidor antigo. */
private class RoutedServer(private val routes: Map<String, Pair<HttpStatusCode, String>>) {
    val requests = mutableListOf<HttpRequestData>()

    val dataSource = RemoteTeamDataSource(
        HttpClient(
            MockEngine { request ->
                requests += request
                val (status, body) = routes["${request.method.value} ${request.url.encodedPath}"]
                    ?: (HttpStatusCode.NotFound to """{"error":"rota inexistente"}""")
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
        }
    )

    fun paths(): List<String> = requests.map { "${it.method.value} ${it.url.encodedPath}" }
}

private fun ok(body: String = "{}") = HttpStatusCode.OK to body

class TeamRepositoriesHttpTest {

    @Test
    fun `valida o token conferindo health e ping`() = runTest {
        val server = RoutedServer(
            mapOf("GET /api/health" to ok(), "GET /api/admin/v1/ping" to ok())
        )
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        assertTrue(repository.validateToken().isSuccess)
        assertEquals(listOf("GET /api/health", "GET /api/admin/v1/ping"), server.paths())
    }

    @Test
    fun `sem token de administracao nada vai para a rede`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamAdminRepositoryImpl(server.dataSource) { MEMBER_SETTINGS }

        assertTrue(repository.validateToken().isFailure)
        assertTrue(repository.listKeys().isFailure)
        assertTrue(repository.fetchBlockedAccounts().isFailure)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `lista chaves com detalhes e datas`() = runTest {
        val server = RoutedServer(mapOf("GET /api/admin/v1/keys" to ok("""{"keys":[$KEY_JSON]}""")))
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        val key = repository.listKeys().getOrThrow().single()

        assertEquals("k1", key.id)
        assertEquals("tk_", key.keyPrefix)
        assertEquals(2, key.maxAccounts)
        assertEquals(listOf("conta-1"), key.accounts)
        assertEquals("a@b.com", key.accountDetails.single().accountEmail)
        assertEquals(false, key.accountDetails.single().authorized)
        assertEquals(Instant.fromEpochMilliseconds(1000), key.createdAt)
        // Zero e ausente são "sem data", não 1970.
        assertNull(key.revokedAt)
        assertNull(key.lastUsedAt)
    }

    @Test
    fun `cria, altera, regera, revoga e desvincula pela rota de cada operacao`() = runTest {
        val server = RoutedServer(
            mapOf(
                "POST /api/admin/v1/keys" to ok(KEY_JSON),
                "PATCH /api/admin/v1/keys/k1" to ok(KEY_JSON),
                "POST /api/admin/v1/keys/k1/regenerate" to ok(KEY_JSON),
                "DELETE /api/admin/v1/keys/k1" to ok(KEY_JSON),
                "DELETE /api/admin/v1/keys/k1/accounts/conta-1" to ok(KEY_JSON)
            )
        )
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        assertEquals("tk_cru", repository.createKey("time", 2).getOrThrow().key)
        assertEquals("k1", repository.updateKey("k1", "novo", null).getOrThrow().id)
        assertEquals("k1", repository.regenerateKey("k1").getOrThrow().id)
        assertEquals("k1", repository.revokeKey("k1").getOrThrow().id)
        assertEquals("k1", repository.unclaimAccount("k1", "conta-1").getOrThrow().id)
        assertEquals(
            listOf(
                "POST /api/admin/v1/keys",
                "PATCH /api/admin/v1/keys/k1",
                "POST /api/admin/v1/keys/k1/regenerate",
                "DELETE /api/admin/v1/keys/k1",
                "DELETE /api/admin/v1/keys/k1/accounts/conta-1"
            ),
            server.paths()
        )
    }

    @Test
    fun `contas fora do time com data do bloqueio`() = runTest {
        val server = RoutedServer(
            mapOf(
                "GET /api/admin/v1/blocked-accounts" to
                    ok("""{"accounts":[{"accountKey":"conta-1","reason":"saiu","blockedAt":2000}]}""")
            )
        )
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        val account = repository.fetchBlockedAccounts().getOrThrow().single()

        assertEquals("conta-1", account.accountKey)
        assertEquals("saiu", account.reason)
        assertEquals(Instant.fromEpochMilliseconds(2000), account.blockedAt)
    }

    @Test
    fun `servidor sem a lista de bloqueio responde lista vazia`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        assertEquals(emptyList(), repository.fetchBlockedAccounts().getOrThrow())
    }

    @Test
    fun `devolve a conta ao time`() = runTest {
        val server = RoutedServer(
            mapOf("DELETE /api/admin/v1/blocked-accounts/conta-1" to ok("""{"accounts":[]}"""))
        )
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        assertEquals(emptyList(), repository.unblockAccount("conta-1").getOrThrow())
    }

    @Test
    fun `devolver conta contra servidor antigo pede atualizacao`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        val result = repository.unblockAccount("conta-1")

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("0.11.0"))
    }

    @Test
    fun `devolver conta com erro que nao e 404 propaga o erro`() = runTest {
        val server = RoutedServer(
            mapOf(
                "DELETE /api/admin/v1/blocked-accounts/conta-1" to
                    (HttpStatusCode.InternalServerError to """{"error":"falhou"}""")
            )
        )
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        val result = repository.unblockAccount("conta-1")

        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("falhou"))
    }

    @Test
    fun `visao do administrador traduz a origem do e-mail`() = runTest {
        val server = RoutedServer(
            mapOf(
                "GET /api/admin/v1/overview" to ok(
                    """{"accounts":[
                    {"accountKey":"c1","label":"a","accountEmail":"a@b.com","emailSource":"reported"},
                    {"accountKey":"c2","emailSource":"label"},
                    {"accountKey":"c3","emailSource":"outra"}]}"""
                )
            )
        )
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        val accounts = repository.fetchOverview(null).getOrThrow()

        assertEquals(listOf("c1", "c2", "c3"), accounts.map { it.accountKey })
        assertEquals(
            listOf(TeamAccountEmailSource.REPORTED, TeamAccountEmailSource.LABEL, null),
            accounts.map { it.emailSource }
        )
        assertEquals("a@b.com", accounts.first().accountEmail)
    }

    @Test
    fun `detalhe de sessao que sumiu vira nulo para o administrador`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        assertNull(repository.fetchSessionDetail("conta-1", "device-1", "s1").getOrThrow())
    }

    @Test
    fun `verificacao sem chave usa o token de administracao`() = runTest {
        val server = RoutedServer(mapOf("GET /api/v1/verify" to ok("""{"authorized":true}""")))
        val repository = TeamAdminRepositoryImpl(server.dataSource) { ADMIN_ONLY_SETTINGS }

        assertEquals(true, repository.verifyKeyForAccount("conta-1", null).getOrThrow().authorized)
        assertEquals("token-de-admin", server.requests.single().headers["x-admin-token"])
    }

    @Test
    fun `verificacao sem chave nem token falha sem rede`() = runTest {
        val server = RoutedServer(emptyMap())
        val settings = MEMBER_SETTINGS.copy(apiKey = "")
        val repository = TeamAdminRepositoryImpl(server.dataSource) { settings }

        assertTrue(repository.verifyKeyForAccount("conta-1", null).isFailure)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `leitura do time com chave de membro`() = runTest {
        val server = RoutedServer(
            mapOf(
                "GET /api/v1/team" to ok(
                    """{"members":[{"deviceId":"device-1","alias":"ana"}],
                    "rows":[{"deviceId":"device-1","sessionId":"s1","model":"claude-sonnet-4-5","turnCount":2,"inputTokens":10}]}"""
                )
            )
        )
        val repository = TeamUsageRepositoryImpl(server.dataSource, { MEMBER_SETTINGS })

        val snapshot = repository.fetch("conta-1", 5_000L).getOrThrow()

        assertEquals(1, snapshot.members.size)
        val request = server.requests.single()
        // Barra final da URL configurada não vira `//` no caminho.
        assertEquals("/api/v1/team", request.url.encodedPath)
        assertEquals("chave-de-time", request.headers["x-team-key"])
        assertEquals("5000", request.url.parameters["since"])
    }

    @Test
    fun `leitura do time sem credencial falha sem rede`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamUsageRepositoryImpl(server.dataSource, { TeamIntegrationSettings() })

        assertTrue(repository.fetch("conta-1", null).isFailure)
        assertTrue(repository.fetchTrend("conta-1", 7).isFailure)
        assertTrue(repository.fetchSessionDetail("conta-1", "d", "s").isFailure)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `leitura do time pelo administrador usa o token`() = runTest {
        val server = RoutedServer(mapOf("GET /api/v1/team" to ok()))
        val repository = TeamUsageRepositoryImpl(server.dataSource, { ADMIN_ONLY_SETTINGS })

        assertTrue(repository.fetch("conta-1", null).isSuccess)
        assertEquals("token-de-admin", server.requests.single().headers["x-admin-token"])
    }

    @Test
    fun `detalhe de sessao com turnos`() = runTest {
        val server = RoutedServer(
            mapOf(
                "GET /api/v1/session" to ok(
                    """{"session":{"deviceId":"device-1","sessionId":"s1","firstTs":1,"lastTs":2},
                    "turns":[{"messageId":"m1","ts":1,"model":"claude-sonnet-4-5","inputTokens":3}]}"""
                )
            )
        )
        val repository = TeamUsageRepositoryImpl(server.dataSource, { MEMBER_SETTINGS })

        val detail = repository.fetchSessionDetail("conta-1", "device-1", "s1").getOrThrow()

        assertEquals(1, detail?.turns?.size)
    }

    @Test
    fun `detalhe de sessao contra servidor antigo vira nulo`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamUsageRepositoryImpl(server.dataSource, { MEMBER_SETTINGS })

        assertNull(repository.fetchSessionDetail("conta-1", "device-1", "s1").getOrThrow())
    }

    @Test
    fun `detalhe de sessao com erro que nao e 404 falha`() = runTest {
        val server = RoutedServer(
            mapOf("GET /api/v1/session" to (HttpStatusCode.Forbidden to """{"error":"outra conta"}"""))
        )
        val repository = TeamUsageRepositoryImpl(server.dataSource, { MEMBER_SETTINGS })

        assertTrue(repository.fetchSessionDetail("conta-1", "device-1", "s1").isFailure)
    }

    @Test
    fun `teste de conexao confere so o health`() = runTest {
        val server = RoutedServer(mapOf("GET /api/health" to ok()))
        val repository = TeamUsageRepositoryImpl(server.dataSource, { MEMBER_SETTINGS })

        assertTrue(repository.checkConnection().isSuccess)
        assertEquals(listOf("GET /api/health"), server.paths())
    }

    @Test
    fun `teste de conexao sem configuracao falha sem rede`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamUsageRepositoryImpl(server.dataSource, { TeamIntegrationSettings() })

        assertTrue(repository.checkConnection().isFailure)
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `envio com integracao desligada falha sem rede`() = runTest {
        val server = RoutedServer(emptyMap())
        val repository = TeamUsageRepositoryImpl(server.dataSource, { MEMBER_SETTINGS.copy(enabled = false) })

        val result = repository.push(
            TeamIngestPayload(
                accountKey = "conta-1",
                member = TeamMemberIdentity(deviceId = "device-1", alias = "ana"),
                sessions = emptyList(),
                turns = emptyList()
            )
        )

        assertTrue(result.isFailure)
        assertTrue(server.requests.isEmpty())
    }
}
