package com.usagemonitor.data

import com.usagemonitor.AnthropicProfileLocation
import com.usagemonitor.data.datasource.LocalCredentialDataSource
import com.usagemonitor.domain.entity.AnthropicProfileRef
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalCredentialProfileTest {
    @Test
    fun `loads credentials and identity from custom config directory`() = kotlinx.coroutines.test.runTest {
        val root = createTempDirectory("anthropic-custom-profile").toFile()
        try {
            val configDirectory = root.resolve(".claude-work").also { it.mkdirs() }
            val credentials = configDirectory.resolve(".credentials.json")
            val identity = configDirectory.resolve(".claude.json")
            val futureExpiry = System.currentTimeMillis() + 60 * 60 * 1000L
            credentials.writeText(
                """{"claudeAiOauth":{"accessToken":"custom-token","refreshToken":"rt","expiresAt":$futureExpiry}}"""
            )
            identity.writeText(
                """{"oauthAccount":{"accountUuid":"account-work","emailAddress":"work@example.com","organizationUuid":"org-work","organizationName":"Work"}}"""
            )
            val profile = AnthropicProfileRef("work", "Empresa")
            val dataSource = LocalCredentialDataSource(
                httpClient = HttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
                homeDirProvider = { root.absolutePath },
                profileLocationProvider = { requested ->
                    if (requested.id == profile.id) {
                        AnthropicProfileLocation(requested, configDirectory, credentials, identity)
                    } else {
                        null
                    }
                }
            )

            val session = dataSource.loadAnthropicSession(profile)

            assertEquals("custom-token", session.accessToken)
            assertEquals("work@example.com", session.accountContext.email)
            assertEquals("org-work", session.accountContext.key.workspaceId)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `does not overwrite credentials changed externally during token refresh`() = kotlinx.coroutines.test.runTest {
        val root = createTempDirectory("anthropic-refresh-race").toFile()
        try {
            val configDirectory = root.resolve(".claude-work").also { it.mkdirs() }
            val credentials = configDirectory.resolve(".credentials.json")
            val identity = configDirectory.resolve(".claude.json")
            val nearExpiry = System.currentTimeMillis() + 1_000L
            val externalContent = """{"claudeAiOauth":{"accessToken":"external-token","refreshToken":"external-rt","expiresAt":${System.currentTimeMillis() + 3_600_000L}}}"""
            credentials.writeText(
                """{"claudeAiOauth":{"accessToken":"old-token","refreshToken":"old-rt","expiresAt":$nearExpiry}}"""
            )
            identity.writeText(
                """{"oauthAccount":{"accountUuid":"account-work","emailAddress":"work@example.com"}}"""
            )
            val profile = AnthropicProfileRef("work", "Empresa")
            val httpClient = HttpClient(MockEngine {
                credentials.writeText(externalContent)
                respond(
                    content = ByteReadChannel("""{"access_token":"refreshed-token","refresh_token":"new-rt","expires_in":3600}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
            val dataSource = LocalCredentialDataSource(
                httpClient = httpClient,
                profileLocationProvider = { requested ->
                    AnthropicProfileLocation(requested, configDirectory, credentials, identity)
                }
            )

            val error = assertFailsWith<IllegalStateException> {
                dataSource.loadAnthropicSession(profile)
            }

            assertTrue(error.message.orEmpty().contains("mudaram durante a renovação"))
            assertEquals(externalContent, credentials.readText())
            httpClient.close()
        } finally {
            root.deleteRecursively()
        }
    }
}
