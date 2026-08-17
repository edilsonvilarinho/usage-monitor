package com.usagemonitor.data

import com.usagemonitor.data.datasource.AnthropicCredentialStore
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalCredentialDataSourceTest {

    private val tempDir: File = createTempDirectory(prefix = "usage-monitor-test").toFile()
    private val claudeDir: File = File(tempDir, ".claude").also { it.mkdirs() }
    private val credentialsFile: File = File(claudeDir, ".credentials.json")
    private val claudeConfigFile: File = File(tempDir, ".claude.json").also { file ->
        writeClaudeConfig(file, "account-a", "org-a", "first@example.com", "Org A")
    }
    private val homeDirProvider: () -> String = { tempDir.absolutePath }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `loads token directly when not near expiration`() = runTest {
        val futureExpiry = System.currentTimeMillis() + 60 * 60 * 1000L
        writeCredentials(accessToken = "fresh-token", refreshToken = "rt", expiresAt = futureExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider
        )

        val session = dataSource.loadAnthropicSession()

        assertEquals("fresh-token", session.accessToken)
        assertEquals("account-a", session.accountContext.key.providerAccountId)
        assertEquals("org-a", session.accountContext.key.workspaceId)
        assertEquals("first@example.com", session.accountContext.email)
    }

    @Test
    fun `rereads token from disk on every session load`() = runTest {
        val futureExpiry = System.currentTimeMillis() + 60 * 60 * 1000L
        writeCredentials(accessToken = "fresh-token", refreshToken = "rt", expiresAt = futureExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider
        )

        val firstToken = dataSource.loadAnthropicSession().accessToken
        writeCredentials(accessToken = "rotated-on-disk", refreshToken = "rt", expiresAt = futureExpiry)
        val secondToken = dataSource.loadAnthropicSession().accessToken

        assertEquals("fresh-token", firstToken)
        assertEquals("rotated-on-disk", secondToken)
    }

    @Test
    fun `throws IllegalStateException with PT message when file missing`() = runTest {
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider
        )

        val error = assertFailsWith<IllegalStateException> { dataSource.loadAnthropicSession() }
        assertTrue(error.message.orEmpty().contains("Credenciais não encontradas"))
    }

    @Test
    fun `refreshes token when within 5-minute margin`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "rt", expiresAt = nearExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = refreshingHttpClient(
                accessToken = "rotated-token",
                refreshToken = "new-rt",
                expiresIn = 3600
            ),
            homeDirProvider = homeDirProvider
        )

        val token = dataSource.loadAnthropicSession().accessToken

        assertEquals("rotated-token", token)
    }

    @Test
    fun `refresh updates file with new access and refresh tokens`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "old-rt", expiresAt = nearExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = refreshingHttpClient(
                accessToken = "rotated-token",
                refreshToken = "new-rt",
                expiresIn = 3600
            ),
            homeDirProvider = homeDirProvider
        )

        dataSource.loadAnthropicSession()

        val written = readCredentialsJson()
        val oauth = written["claudeAiOauth"]!!.jsonObject
        assertEquals("rotated-token", oauth["accessToken"]!!.jsonPrimitive.content)
        assertEquals("new-rt", oauth["refreshToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun `refresh preserves old refresh token when response omits it`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "preserved-rt", expiresAt = nearExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = refreshingHttpClient(accessToken = "rotated-token", refreshToken = null, expiresIn = 3600),
            homeDirProvider = homeDirProvider
        )

        dataSource.loadAnthropicSession()

        val oauth = readCredentialsJson()["claudeAiOauth"]!!.jsonObject
        assertEquals("preserved-rt", oauth["refreshToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun `refresh recomputes expiresAt from response expires_in`() = runTest {
        val originalExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "rt", expiresAt = originalExpiry)
        val before = System.currentTimeMillis()
        val dataSource = LocalCredentialDataSource(
            httpClient = refreshingHttpClient(accessToken = "rotated-token", refreshToken = "new-rt", expiresIn = 3600),
            homeDirProvider = homeDirProvider
        )

        dataSource.loadAnthropicSession()

        val oauth = readCredentialsJson()["claudeAiOauth"]!!.jsonObject
        val newExpiresAt = oauth["expiresAt"]!!.jsonPrimitive.content.toLong()
        assertNotEquals(originalExpiry, newExpiresAt)
        // Janela tolerante: deve cair entre [before + 3600s, after + 3600s + folga].
        val expectedMin = before + 3600 * 1000L
        val expectedMax = System.currentTimeMillis() + 3600 * 1000L + 5_000L
        assertTrue(newExpiresAt in expectedMin..expectedMax, "expiresAt=$newExpiresAt out of [$expectedMin,$expectedMax]")
    }

    @Test
    fun `throws when refresh response has no access_token`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "rt", expiresAt = nearExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel("{}"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            homeDirProvider = homeDirProvider
        )

        val error = assertFailsWith<IllegalStateException> { dataSource.loadAnthropicSession() }
        assertTrue(error.message.orEmpty().contains("Token refresh retornou sem access_token"))
    }

    @Test
    fun `does not refresh when refresh_token field empty`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "expired-token", refreshToken = "", expiresAt = nearExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider
        )

        // Sem refresh_token o data source devolve o accessToken atual sem chamar HTTP.
        val token = dataSource.loadAnthropicSession().accessToken
        assertEquals("expired-token", token)
    }

    @Test
    fun `accepts null optional Claude metadata fields`() = runTest {
        val futureExpiry = System.currentTimeMillis() + 60 * 60 * 1000L
        credentialsFile.writeText(
            """
            {
              "claudeAiOauth": {
                "accessToken": "fresh-token",
                "refreshToken": "rt",
                "expiresAt": $futureExpiry,
                "subscriptionType": null,
                "rateLimitTier": null
              }
            }
            """.trimIndent()
        )
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider
        )

        val token = dataSource.loadAnthropicSession().accessToken

        assertEquals("fresh-token", token)
    }

    @Test
    fun `reads from the injected store when there is no credentials file`() = runTest {
        val futureExpiry = System.currentTimeMillis() + 60 * 60 * 1000L
        val store = InMemoryCredentialStore(credentialsJson("keychain-token", "rt", futureExpiry))
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider,
            credentialStoreProvider = { store }
        )

        val session = dataSource.loadAnthropicSession()

        assertEquals("keychain-token", session.accessToken)
        assertEquals(false, credentialsFile.exists())
    }

    @Test
    fun `refresh writes back to the injected store`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        val store = InMemoryCredentialStore(credentialsJson("old-token", "old-rt", nearExpiry))
        val dataSource = LocalCredentialDataSource(
            httpClient = refreshingHttpClient(
                accessToken = "rotated-token",
                refreshToken = "new-rt",
                expiresIn = 3600
            ),
            homeDirProvider = homeDirProvider,
            credentialStoreProvider = { store }
        )

        dataSource.loadAnthropicSession()

        val oauth = Json.parseToJsonElement(store.read()!!).jsonObject["claudeAiOauth"]!!.jsonObject
        assertEquals("rotated-token", oauth["accessToken"]!!.jsonPrimitive.content)
        assertEquals("new-rt", oauth["refreshToken"]!!.jsonPrimitive.content)
        assertEquals(false, credentialsFile.exists())
    }

    @Test
    fun `surfaces the store message when the store has no credentials`() = runTest {
        val store = InMemoryCredentialStore(content = null)
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider,
            credentialStoreProvider = { store }
        )

        val error = assertFailsWith<IllegalStateException> { dataSource.loadAnthropicSession() }
        assertTrue(error.message.orEmpty().contains("sem credenciais"))
    }

    @Test
    fun `detects account switch without recreating datasource`() = runTest {
        val futureExpiry = System.currentTimeMillis() + 60 * 60 * 1000L
        writeCredentials(accessToken = "token-a", refreshToken = "rt-a", expiresAt = futureExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = throwingHttpClient(),
            homeDirProvider = homeDirProvider
        )

        val firstSession = dataSource.loadAnthropicSession()
        writeCredentials(accessToken = "token-b", refreshToken = "rt-b", expiresAt = futureExpiry)
        writeClaudeConfig(claudeConfigFile, "account-b", "org-b", "second@example.com", "Org B")
        val secondSession = dataSource.loadAnthropicSession()

        assertEquals(false, dataSource.isAnthropicSessionCurrent(firstSession))
        assertEquals(true, dataSource.isAnthropicSessionCurrent(secondSession))
        assertEquals("second@example.com", secondSession.accountContext.email)
    }

    // ── Formato do request de renovação ──────────────────────────────────
    // Sem `client_id` o endpoint responde HTTP 400 "Invalid request format" com
    // qualquer refresh token, e a renovação nunca acontecia (issue #64).

    @Test
    fun `refresh request carries client_id grant_type and scope`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "rt", expiresAt = nearExpiry)
        val captured = CapturedRequest()
        val dataSource = LocalCredentialDataSource(
            httpClient = capturingRefreshClient(captured),
            homeDirProvider = homeDirProvider
        )

        dataSource.loadAnthropicSession()

        assertEquals("https://platform.claude.com/v1/oauth/token", captured.url)
        val body = Json.parseToJsonElement(captured.body.orEmpty()).jsonObject
        assertEquals("refresh_token", body["grant_type"]!!.jsonPrimitive.content)
        assertEquals("rt", body["refresh_token"]!!.jsonPrimitive.content)
        assertEquals("9d1c250a-e61b-44d9-88ed-5944d1962f5e", body["client_id"]!!.jsonPrimitive.content)
        assertEquals(
            "user:profile user:inference user:sessions:claude_code user:mcp_servers user:file_upload",
            body["scope"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun `refresh scope mirrors the scopes stored in the file`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        credentialsFile.writeText(
            """
            {
              "claudeAiOauth": {
                "accessToken": "old-token",
                "refreshToken": "rt",
                "expiresAt": $nearExpiry,
                "scopes": ["user:profile", "user:inference"]
              }
            }
            """.trimIndent()
        )
        val captured = CapturedRequest()
        val dataSource = LocalCredentialDataSource(
            httpClient = capturingRefreshClient(captured),
            homeDirProvider = homeDirProvider
        )

        dataSource.loadAnthropicSession()

        val body = Json.parseToJsonElement(captured.body.orEmpty()).jsonObject
        assertEquals("user:profile user:inference", body["scope"]!!.jsonPrimitive.content)
    }

    @Test
    fun `refresh preserves credential nodes the app does not know`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        val refreshTokenExpiry = nearExpiry + 15 * 24 * 60 * 60 * 1000L
        credentialsFile.writeText(
            """
            {
              "claudeAiOauth": {
                "accessToken": "old-token",
                "refreshToken": "old-rt",
                "expiresAt": $nearExpiry,
                "refreshTokenExpiresAt": $refreshTokenExpiry,
                "subscriptionType": "max"
              },
              "mcpOAuth": {
                "atlassian|abc": {
                  "serverName": "atlassian",
                  "accessToken": "mcp-token",
                  "clientId": "mcp-client"
                }
              }
            }
            """.trimIndent()
        )
        val dataSource = LocalCredentialDataSource(
            httpClient = refreshingHttpClient(
                accessToken = "rotated-token",
                refreshToken = "new-rt",
                expiresIn = 3600
            ),
            homeDirProvider = homeDirProvider
        )

        dataSource.loadAnthropicSession()

        val root = readCredentialsJson()
        val oauth = root["claudeAiOauth"]!!.jsonObject
        assertEquals("rotated-token", oauth["accessToken"]!!.jsonPrimitive.content)
        assertEquals("new-rt", oauth["refreshToken"]!!.jsonPrimitive.content)
        // Serializar o DTO de volta apagava estes nós em silêncio.
        assertEquals(refreshTokenExpiry, oauth["refreshTokenExpiresAt"]!!.jsonPrimitive.content.toLong())
        assertEquals("max", oauth["subscriptionType"]!!.jsonPrimitive.content)
        val mcpEntry = root["mcpOAuth"]!!.jsonObject["atlassian|abc"]!!.jsonObject
        assertEquals("mcp-token", mcpEntry["accessToken"]!!.jsonPrimitive.content)
        assertEquals("mcp-client", mcpEntry["clientId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `refresh failure reports the http status and body`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "rt", expiresAt = nearExpiry)
        val dataSource = LocalCredentialDataSource(
            httpClient = jsonHttpClient {
                respond(
                    content = ByteReadChannel(
                        """{"type":"error","error":{"type":"invalid_request_error","message":"Invalid request format"}}"""
                    ),
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            },
            homeDirProvider = homeDirProvider
        )

        val error = assertFailsWith<IllegalStateException> { dataSource.loadAnthropicSession() }
        val message = error.message.orEmpty()
        assertTrue(message.contains("Token refresh falhou (HTTP 400)"), message)
        assertTrue(message.contains("invalid_request_error"), message)
    }

    @Test
    fun `refresh failure does not touch the credentials file`() = runTest {
        val nearExpiry = System.currentTimeMillis() + 60 * 1000L
        writeCredentials(accessToken = "old-token", refreshToken = "rt", expiresAt = nearExpiry)
        val before = credentialsFile.readText()
        val dataSource = LocalCredentialDataSource(
            httpClient = jsonHttpClient { respondError(HttpStatusCode.BadRequest) },
            homeDirProvider = homeDirProvider
        )

        assertFailsWith<IllegalStateException> { dataSource.loadAnthropicSession() }

        assertEquals(before, credentialsFile.readText())
    }

    private fun writeClaudeConfig(
        file: File,
        accountUuid: String,
        organizationUuid: String,
        email: String,
        organizationName: String
    ) {
        file.writeText(
            """
            {
              "oauthAccount": {
                "accountUuid": "$accountUuid",
                "organizationUuid": "$organizationUuid",
                "emailAddress": "$email",
                "organizationName": "$organizationName"
              }
            }
            """.trimIndent()
        )
    }

    private fun writeCredentials(accessToken: String, refreshToken: String, expiresAt: Long) {
        credentialsFile.writeText(credentialsJson(accessToken, refreshToken, expiresAt))
    }

    private fun credentialsJson(accessToken: String, refreshToken: String, expiresAt: Long): String {
        return """
            {
              "claudeAiOauth": {
                "accessToken": "$accessToken",
                "refreshToken": "$refreshToken",
                "expiresAt": $expiresAt
              }
            }
        """.trimIndent()
    }

    /** Substitui o Keychain nos testes: a origem real do macOS não existe no Windows/Linux. */
    private class InMemoryCredentialStore(
        private var content: String?
    ) : AnthropicCredentialStore {
        override fun read(): String? = content

        override fun write(content: String) {
            this.content = content
        }

        override fun missingCredentialsMessage(profileLabel: String): String {
            return "Origem sem credenciais para o perfil '$profileLabel'."
        }
    }

    private fun readCredentialsJson(): JsonObject {
        return Json.parseToJsonElement(credentialsFile.readText()).jsonObject
    }

    private fun throwingHttpClient(): HttpClient {
        return jsonHttpClient { respondError(HttpStatusCode.InternalServerError) }
    }

    private class CapturedRequest(
        var url: String? = null,
        var body: String? = null
    )

    /** Guarda o que foi enviado ao endpoint OAuth e responde uma renovação válida. */
    private fun capturingRefreshClient(captured: CapturedRequest): HttpClient {
        return jsonHttpClient { request ->
            captured.url = request.url.toString()
            captured.body = (request.body as? TextContent)?.text
            respond(
                content = ByteReadChannel(
                    """{"access_token":"rotated-token","refresh_token":"new-rt","expires_in":3600}"""
                ),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }

    private fun refreshingHttpClient(
        accessToken: String,
        refreshToken: String?,
        expiresIn: Int
    ): HttpClient {
        val refreshTokenJson = refreshToken?.let { "\"refresh_token\": \"$it\"," } ?: ""
        val body = """
            {
              "access_token": "$accessToken",
              $refreshTokenJson
              "expires_in": $expiresIn
            }
        """.trimIndent()
        return jsonHttpClient {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
    }

    private fun jsonHttpClient(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
