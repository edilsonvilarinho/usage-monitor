package com.usagemonitor.data

import com.usagemonitor.data.datasource.RemoteTeamDataSource
import com.usagemonitor.data.datasource.TeamCredential
import com.usagemonitor.data.datasource.TeamServerException
import com.usagemonitor.data.dto.CreateTeamKeyRequestDto
import com.usagemonitor.data.dto.TeamIngestRequestDto
import com.usagemonitor.data.dto.TeamMemberDto
import com.usagemonitor.data.dto.TeamPresenceRequestDto
import com.usagemonitor.data.dto.UpdateTeamKeyRequestDto
import com.usagemonitor.domain.entity.TURN_GAP_CUTOFF_MILLIS
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BASE_URL = "http://team.local"
private const val TEAM_KEY = "chave-de-time"
private const val ADMIN_TOKEN = "token-de-admin"

/**
 * Cada rota do servidor de time contra um `MockEngine`: método, caminho, header de
 * credencial, query e corpo que saem, e o DTO que volta. É o contrato que o
 * `server/README.md` descreve, conferido do lado do cliente.
 */
class RemoteTeamDataSourceHttpTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun dataSource(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = "{}"
    ): RemoteTeamDataSource = dataSource { respondJson(body, status) }

    private fun dataSource(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): RemoteTeamDataSource {
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        return RemoteTeamDataSource(client)
    }

    private fun single(): HttpRequestData = requests.single()

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `healthcheck nao manda credencial`() = runTest {
        dataSource().checkHealth(BASE_URL)

        val request = single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/health", request.url.encodedPath)
        assertNull(request.headers["x-team-key"])
        assertNull(request.headers["x-admin-token"])
    }

    @Test
    fun `leitura do time manda chave, conta, corte e janela`() = runTest {
        val snapshot = dataSource(
            body = """{"members":[{"deviceId":"d1","alias":"ana","lastSeenAt":9}],
                "rows":[{"deviceId":"d1","sessionId":"s1","turnCount":3}],
                "activity":[{"deviceId":"d1","sessionId":"s1","activeMillis":120}]}"""
        ).fetchTeam(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", 1_000L)

        val request = single()
        assertEquals("/api/v1/team", request.url.encodedPath)
        assertEquals(TEAM_KEY, request.headers["x-team-key"])
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
        assertEquals("conta-1", request.url.parameters["accountKey"])
        assertEquals("1000", request.url.parameters["since"])
        assertEquals(TURN_GAP_CUTOFF_MILLIS.toString(), request.url.parameters["gapCutoffMs"])
        assertEquals("ana", snapshot.members.single().alias)
        assertEquals(3, snapshot.rows.single().turnCount)
        assertEquals(120L, snapshot.activity.single().activeMillis)
    }

    @Test
    fun `leitura do time sem janela nao manda since`() = runTest {
        dataSource().fetchTeam(BASE_URL, TeamCredential.AdminToken(ADMIN_TOKEN), "conta-1", null)

        val request = single()
        assertNull(request.url.parameters["since"])
        assertEquals(ADMIN_TOKEN, request.headers["x-admin-token"])
        assertNull(request.headers["x-team-key"])
    }

    @Test
    fun `detalhe da sessao manda conta, maquina e sessao`() = runTest {
        val detail = dataSource(
            body = """{"session":{"deviceId":"d1","sessionId":"s1","cwd":"/repo"},
                "turns":[{"messageId":"m1","ts":5,"inputTokens":7}]}"""
        ).fetchSessionDetail(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", "d1", "s1")

        val request = single()
        assertEquals("/api/v1/session", request.url.encodedPath)
        assertEquals("d1", request.url.parameters["deviceId"])
        assertEquals("s1", request.url.parameters["sessionId"])
        assertEquals("/repo", detail.session.cwd)
        assertEquals(7L, detail.turns.single().inputTokens)
    }

    @Test
    fun `tendencia manda a quantidade de dias`() = runTest {
        val trend = dataSource(
            body = """{"members":[{"deviceId":"d1","alias":"ana"}],
                "rows":[{"deviceId":"d1","dayStartMillis":86400000,"turnCount":2}]}"""
        ).fetchTeamTrend(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", 30)

        val request = single()
        assertEquals("/api/v1/team/trend", request.url.encodedPath)
        assertEquals("30", request.url.parameters["days"])
        assertEquals(86_400_000L, trend.rows.single().dayStartMillis)
    }

    @Test
    fun `envio usa a chave de time e corpo json`() = runTest {
        val response = dataSource(body = """{"acceptedTurns":4,"ignoredTurns":1,"acceptedSessions":2}""")
            .pushIngest(
                BASE_URL,
                TEAM_KEY,
                TeamIngestRequestDto(accountKey = "conta-1", member = TeamMemberDto("d1", "ana"))
            )

        val request = single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/ingest", request.url.encodedPath)
        assertEquals(TEAM_KEY, request.headers["x-team-key"])
        assertTrue(request.bodyText().contains("\"accountKey\":\"conta-1\""))
        assertEquals(4, response.acceptedTurns)
        assertEquals(1, response.ignoredTurns)
        assertEquals(2, response.acceptedSessions)
    }

    @Test
    fun `presenca usa a chave de time`() = runTest {
        val response = dataSource(body = """{"lastSeenAt":42}""").touchPresence(
            BASE_URL,
            TEAM_KEY,
            TeamPresenceRequestDto(accountKey = "conta-1", member = TeamMemberDto("d1", "ana"))
        )

        val request = single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/presence", request.url.encodedPath)
        assertTrue(request.bodyText().contains("\"deviceId\":\"d1\""))
        assertEquals(42L, response.lastSeenAt)
    }

    @Test
    fun `remocao de integrante vai na query, sem corpo`() = runTest {
        dataSource().deleteMember(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", "d1")

        val request = single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/v1/member", request.url.encodedPath)
        assertEquals("conta-1", request.url.parameters["accountKey"])
        assertEquals("d1", request.url.parameters["deviceId"])
    }

    @Test
    fun `remocao de sessao codifica cada segmento do caminho`() = runTest {
        dataSource().deleteSession(BASE_URL, ADMIN_TOKEN, "conta 1", "d/1", "s 1")

        val request = single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals(
            "/api/admin/v1/accounts/conta%201/members/d%2F1/sessions/s%201",
            request.url.encodedPath
        )
        assertEquals(ADMIN_TOKEN, request.headers["x-admin-token"])
    }

    @Test
    fun `verificacao manda o e-mail so quando existe`() = runTest {
        val source = dataSource(body = """{"authorized":true,"claimed":true,"label":"time","maxAccounts":2,"claimedAccounts":1}""")

        val withEmail = source.verifyKey(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", "a@b.com")
        source.verifyKey(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1")

        assertEquals("/api/v1/verify", requests[0].url.encodedPath)
        assertEquals("a@b.com", requests[0].url.parameters["accountEmail"])
        assertNull(requests[1].url.parameters["accountEmail"])
        assertEquals(true, withEmail.authorized)
        assertEquals("time", withEmail.label)
        assertEquals(2, withEmail.maxAccounts)
        assertEquals(1, withEmail.claimedAccounts)
    }

    @Test
    fun `vinculo manda conta e e-mail no corpo`() = runTest {
        val result = dataSource(body = """{"authorized":true,"claimed":true}""")
            .claimKey(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", "a@b.com")

        val request = single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/v1/claim", request.url.encodedPath)
        val body = request.bodyText()
        assertTrue(body.contains("\"accountKey\":\"conta-1\""))
        assertTrue(body.contains("\"accountEmail\":\"a@b.com\""))
        assertEquals(true, result.claimed)
    }

    @Test
    fun `ping de administracao usa o token`() = runTest {
        dataSource().checkAdminToken(BASE_URL, ADMIN_TOKEN)

        val request = single()
        assertEquals("/api/admin/v1/ping", request.url.encodedPath)
        assertEquals(ADMIN_TOKEN, request.headers["x-admin-token"])
    }

    @Test
    fun `visao do administrador manda janela e corte`() = runTest {
        val overview = dataSource(
            body = """{"accounts":[{"accountKey":"conta-1","label":"time","accountEmail":"a@b.com"}]}"""
        ).fetchOverview(BASE_URL, ADMIN_TOKEN, 500L)

        val request = single()
        assertEquals("/api/admin/v1/overview", request.url.encodedPath)
        assertEquals("500", request.url.parameters["since"])
        assertEquals(TURN_GAP_CUTOFF_MILLIS.toString(), request.url.parameters["gapCutoffMs"])
        assertEquals("a@b.com", overview.accounts.single().accountEmail)
    }

    @Test
    fun `visao do administrador sem janela nao manda since`() = runTest {
        dataSource().fetchOverview(BASE_URL, ADMIN_TOKEN, null)

        assertNull(single().url.parameters["since"])
    }

    @Test
    fun `lista de chaves`() = runTest {
        val keys = dataSource(
            body = """{"keys":[{"id":"k1","label":"time","keyPrefix":"tk_","maxAccounts":3,
                "accounts":["conta-1"],"accountDetails":[{"accountKey":"conta-1","authorized":false}]}]}"""
        ).listKeys(BASE_URL, ADMIN_TOKEN)

        assertEquals("/api/admin/v1/keys", single().url.encodedPath)
        val key = keys.keys.single()
        assertEquals("tk_", key.keyPrefix)
        assertEquals(3, key.maxAccounts)
        assertEquals(false, key.accountDetails.single().authorized)
    }

    @Test
    fun `criacao de chave devolve a chave crua`() = runTest {
        val key = dataSource(body = """{"id":"k1","label":"time","key":"tk_cru"}""")
            .createKey(BASE_URL, ADMIN_TOKEN, CreateTeamKeyRequestDto(label = "time", maxAccounts = 2))

        val request = single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/admin/v1/keys", request.url.encodedPath)
        assertTrue(request.bodyText().contains("\"maxAccounts\":2"))
        assertEquals("tk_cru", key.key)
    }

    @Test
    fun `alteracao de chave e patch no id codificado`() = runTest {
        dataSource(body = """{"id":"k 1","label":"novo"}""")
            .updateKey(BASE_URL, ADMIN_TOKEN, "k 1", UpdateTeamKeyRequestDto(label = "novo"))

        val request = single()
        assertEquals(HttpMethod.Patch, request.method)
        assertEquals("/api/admin/v1/keys/k%201", request.url.encodedPath)
        // PATCH parcial: campo nulo fica fora do corpo para não sobrescrever.
        val body = request.bodyText()
        assertTrue(body.contains("\"label\":\"novo\""))
        assertTrue(!body.contains("maxAccounts"))
    }

    @Test
    fun `regeracao de chave`() = runTest {
        val key = dataSource(body = """{"id":"k1","key":"tk_novo"}""").regenerateKey(BASE_URL, ADMIN_TOKEN, "k1")

        val request = single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/admin/v1/keys/k1/regenerate", request.url.encodedPath)
        assertEquals("tk_novo", key.key)
    }

    @Test
    fun `revogacao de chave`() = runTest {
        val key = dataSource(body = """{"id":"k1","revokedAt":77}""").revokeKey(BASE_URL, ADMIN_TOKEN, "k1")

        val request = single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/admin/v1/keys/k1", request.url.encodedPath)
        assertEquals(77L, key.revokedAt)
    }

    @Test
    fun `desvinculo de conta`() = runTest {
        dataSource(body = """{"id":"k1"}""").unclaimAccount(BASE_URL, ADMIN_TOKEN, "k1", "conta 1")

        val request = single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/admin/v1/keys/k1/accounts/conta%201", request.url.encodedPath)
    }

    @Test
    fun `remocao de conta devolve o recibo`() = runTest {
        val receipt = dataSource(
            body = """{"deletedTurns":10,"deletedSessions":2,"deletedMembers":1,"unlinkedKeys":1}"""
        ).deleteAccount(BASE_URL, ADMIN_TOKEN, "conta-1")

        val request = single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/admin/v1/accounts/conta-1", request.url.encodedPath)
        assertEquals(10, receipt.deletedTurns)
        assertEquals(2, receipt.deletedSessions)
    }

    @Test
    fun `contas fora do time`() = runTest {
        val blocked = dataSource(
            body = """{"accounts":[{"accountKey":"conta-1","accountEmail":"a@b.com","reason":"saiu","blockedAt":3}]}"""
        ).fetchBlockedAccounts(BASE_URL, ADMIN_TOKEN)

        assertEquals("/api/admin/v1/blocked-accounts", single().url.encodedPath)
        val account = blocked.accounts.single()
        assertEquals("saiu", account.reason)
        assertEquals(3L, account.blockedAt)
    }

    @Test
    fun `devolucao da conta ao time`() = runTest {
        val remaining = dataSource(body = """{"accounts":[]}""").unblockAccount(BASE_URL, ADMIN_TOKEN, "conta 1")

        val request = single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("/api/admin/v1/blocked-accounts/conta%201", request.url.encodedPath)
        assertTrue(remaining.accounts.isEmpty())
    }

    @Test
    fun `erro com corpo json usa a frase do servidor`() = runTest {
        val error = assertFailsWith<TeamServerException> {
            dataSource(HttpStatusCode.Unauthorized, """{"error":"chave invalida","code":"bad_key"}""")
                .fetchTeam(BASE_URL, TeamCredential.TeamKey(TEAM_KEY), "conta-1", null)
        }

        assertEquals(401, error.statusCode)
        assertEquals("chave invalida", error.detail)
        assertEquals(
            "Servidor de time recusou a leitura do time (HTTP 401): chave invalida",
            error.message
        )
    }

    @Test
    fun `erro com corpo que nao e json cai no texto cru truncado`() = runTest {
        val html = "<html>" + "x".repeat(400) + "</html>"

        val error = assertFailsWith<TeamServerException> {
            dataSource(HttpStatusCode.BadGateway, html).checkHealth(BASE_URL)
        }

        assertEquals(502, error.statusCode)
        assertEquals(html.take(300), error.detail)
    }

    @Test
    fun `erro com json sem frase cai no texto cru`() = runTest {
        val error = assertFailsWith<TeamServerException> {
            dataSource(HttpStatusCode.Forbidden, """{"code":"forbidden"}""").listKeys(BASE_URL, ADMIN_TOKEN)
        }

        assertEquals("""{"code":"forbidden"}""", error.detail)
    }

    @Test
    fun `erro sem corpo diz sem detalhe`() = runTest {
        val error = assertFailsWith<TeamServerException> {
            dataSource(HttpStatusCode.NotFound, "").deleteAccount(BASE_URL, ADMIN_TOKEN, "conta-1")
        }

        assertEquals(404, error.statusCode)
        assertEquals("sem detalhe", error.detail)
    }

    @Test
    fun `excecao sem detalhe explicito usa a mensagem`() {
        val error = TeamServerException(statusCode = 500, message = "falhou")

        assertEquals("falhou", error.detail)
    }
}

private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode): HttpResponseData =
    respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json")
    )
