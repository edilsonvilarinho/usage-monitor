package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ProxySettings
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A conferência que o `team join` faz antes de gravar: a mesma do "Testar
 * conexão" das Configurações — servidor de pé e a chave vinculada a cada conta.
 */
class TerminalTeamCheckTest {

    private val settings = TeamIntegrationSettings(
        enabled = true,
        serverUrl = "http://team.local/",
        apiKey = "chave-de-time",
        alias = "ana",
        deviceId = "device-1"
    )
    private val requests = mutableListOf<HttpRequestData>()

    private fun check(
        accounts: List<TeamCheckAccount>,
        respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
    ): List<String> {
        val run = TerminalTeamRunner.realCheck(
            proxy = { ProxySettings() },
            httpClientFactory = {
                HttpClient(
                    MockEngine { request ->
                        requests += request
                        respond(request)
                    }
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
                }
            }
        )
        return run(settings, accounts)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun paths() = requests.map { request -> "${request.method.value} ${request.url.encodedPath}" }

    @Test
    fun `a server that is down stops the check before any claim`() {
        val problems = check(listOf(TeamCheckAccount("default", "Pessoal", "conta-1", "a@b.com"))) {
            json("""{"error":"manutenção"}""", HttpStatusCode.ServiceUnavailable)
        }

        assertEquals(1, problems.size)
        assertTrue(problems.single().isNotBlank())
        assertEquals(listOf("GET /api/health"), paths())
    }

    @Test
    fun `every account is claimed and only the problems come back, one line per account`() {
        val accounts = listOf(
            TeamCheckAccount("default", "Pessoal", "conta-1", "a@b.com"),
            TeamCheckAccount("work", "Trabalho", "conta-2", "b@b.com"),
            TeamCheckAccount("home", "Casa", null, null),
            TeamCheckAccount("other", "Outra", "conta-3", null)
        )

        val problems = check(accounts) { request ->
            val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
            when {
                request.url.encodedPath == "/api/health" -> json("{}")
                body.contains("conta-1") -> json("""{"authorized":true,"claimed":true}""")
                body.contains("conta-2") -> json("""{"authorized":false,"claimed":false}""")
                else -> json("""{"error":"chave revogada"}""", HttpStatusCode.Unauthorized)
            }
        }

        assertEquals(
            listOf("GET /api/health", "POST /api/v1/claim", "POST /api/v1/claim", "POST /api/v1/claim"),
            paths(),
            "conta sem login não vai à rede"
        )
        assertEquals(4 - 1, problems.size)
        assertEquals("Trabalho: a chave não cobre esta conta", problems[0])
        assertEquals("Casa: sem login", problems[1])
        assertTrue(problems[2].startsWith("Outra: "), problems[2])
        assertTrue(problems[2].length > "Outra: ".length, "a causa do servidor aparece")
    }

    @Test
    fun `with nothing wrong the check is empty`() {
        val problems = check(listOf(TeamCheckAccount("default", "Pessoal", "conta-1", "a@b.com"))) { request ->
            if (request.url.encodedPath == "/api/health") json("{}") else json("""{"authorized":true,"claimed":true}""")
        }

        assertEquals(emptyList<String>(), problems)
    }
}
