package com.usagemonitor.data

import com.usagemonitor.data.datasource.LocalCredentialDataSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
        credentialsFile.writeText(
            """
            {
              "claudeAiOauth": {
                "accessToken": "$accessToken",
                "refreshToken": "$refreshToken",
                "expiresAt": $expiresAt
              }
            }
            """.trimIndent()
        )
    }

    private fun readCredentialsJson(): JsonObject {
        return Json.parseToJsonElement(credentialsFile.readText()).jsonObject
    }

    private fun throwingHttpClient(): HttpClient {
        return jsonHttpClient { respondError(HttpStatusCode.InternalServerError) }
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
