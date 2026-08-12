package com.usagemonitor.data.datasource

import com.usagemonitor.AnthropicProfileLocation
import com.usagemonitor.data.dto.CredentialsFileDto
import com.usagemonitor.data.dto.OAuthCredentialsDto
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L  // renova se expira em menos de 5 min
private const val OAUTH_REFRESH_URL = "https://console.anthropic.com/v1/oauth/token"

internal class LocalCredentialDataSource(
    private val httpClient: HttpClient,
    // Costura de teste: permite apontar para um diretório temporário em vez de ~/.claude.
    private val homeDirProvider: () -> String = {
        System.getProperty("user.home")
            ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
    },
    private val claudeConfigFileProvider: (String) -> File = { homeDir -> File(homeDir, ".claude.json") },
    private val profileLocationProvider: (AnthropicProfileRef) -> AnthropicProfileLocation? = { profile ->
        if (profile.id == AnthropicProfileRef.DEFAULT.id) {
            val homeDir = homeDirProvider()
            AnthropicProfileLocation(
                profile = profile,
                configDirectory = File(homeDir, ".claude"),
                credentialsFile = File(homeDir, ".claude/.credentials.json"),
                identityFile = claudeConfigFileProvider(homeDir)
            )
        } else {
            null
        }
    },
    // Costura de teste: permite trocar a origem das credenciais (ficheiro ou Keychain).
    private val credentialStoreProvider: (AnthropicProfileLocation) -> AnthropicCredentialStore = { location ->
        defaultCredentialStore(location)
    }
) : CredentialDataSource {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val profileMutexes = mutableMapOf<String, Mutex>()

    override suspend fun loadAnthropicSession(): AnthropicSession {
        return loadAnthropicSession(AnthropicProfileRef.DEFAULT)
    }

    override suspend fun loadAnthropicSession(profile: AnthropicProfileRef): AnthropicSession {
        val mutex = synchronized(profileMutexes) {
            profileMutexes.getOrPut(profile.id) { Mutex() }
        }
        return mutex.withLock {
            val location = resolveLocation(profile)
            val now = System.currentTimeMillis()
            val store = credentialStoreProvider(location)
            val originalContent = store.read()
                ?: throw IllegalStateException(store.missingCredentialsMessage(profile.label))

            var creds = json.decodeFromString<CredentialsFileDto>(originalContent)
            val needsRefresh = creds.claudeAiOauth.expiresAt - now < REFRESH_MARGIN_MS

            if (needsRefresh && creds.claudeAiOauth.refreshToken.isNotEmpty()) {
                creds = refreshToken(store, originalContent, creds)
            }

            val accessToken = creds.claudeAiOauth.accessToken.ifBlank {
                throw IllegalStateException("Credenciais do Claude Code inválidas: accessToken ausente.")
            }

            AnthropicSession(
                accessToken = accessToken,
                accountContext = loadAccountContext(location)
            )
        }
    }

    override suspend fun isAnthropicSessionCurrent(
        profile: AnthropicProfileRef,
        session: AnthropicSession
    ): Boolean {
        val location = resolveLocation(profile)
        val storedContent = credentialStoreProvider(location).read() ?: return false

        return try {
            val creds = json.decodeFromString<CredentialsFileDto>(storedContent)
            val currentAccount = loadAccountContext(location)
            creds.claudeAiOauth.accessToken == session.accessToken &&
                currentAccount.key == session.accountContext.key
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveLocation(profile: AnthropicProfileRef): AnthropicProfileLocation {
        return profileLocationProvider(profile)
            ?: throw IllegalStateException("Perfil Anthropic não configurado: ${profile.label}.")
    }

    override suspend fun isAnthropicSessionCurrent(session: AnthropicSession): Boolean {
        return isAnthropicSessionCurrent(AnthropicProfileRef.DEFAULT, session)
    }

    private fun loadAccountContext(location: AnthropicProfileLocation): UsageAccountContext {
        val configFile = location.identityFile
        if (!configFile.exists()) {
            throw IllegalStateException(
                "Identidade do Claude Code não encontrada: ${configFile.absolutePath}. " +
                    "Execute /login no Claude Code para autenticar."
            )
        }

        val config = json.decodeFromString<ClaudeConfigDto>(configFile.readText())
        val account = config.oauthAccount
            ?: throw IllegalStateException("Identidade do Claude Code inválida: oauthAccount ausente.")
        val providerAccountId = account.accountUuid.ifBlank {
            throw IllegalStateException("Identidade do Claude Code inválida: accountUuid ausente.")
        }
        val email = account.emailAddress.ifBlank {
            throw IllegalStateException("Identidade do Claude Code inválida: emailAddress ausente.")
        }

        return UsageAccountContext(
            key = UsageAccountKey(
                source = ApiSource.ANTHROPIC,
                providerAccountId = providerAccountId,
                workspaceId = account.organizationUuid?.ifBlank { null }
            ),
            email = email,
            workspaceName = account.organizationName?.ifBlank { null }
        )
    }

    private suspend fun refreshToken(
        store: AnthropicCredentialStore,
        originalContent: String,
        creds: CredentialsFileDto
    ): CredentialsFileDto {
        val response = httpClient.post(OAUTH_REFRESH_URL) {
            contentType(ContentType.Application.Json)
            setBody(TokenRefreshRequest("refresh_token", creds.claudeAiOauth.refreshToken))
        }.body<TokenRefreshResponse>()

        val newAccessToken = response.accessToken
            ?: throw IllegalStateException("Token refresh retornou sem access_token")

        val updated = creds.copy(
            claudeAiOauth = creds.claudeAiOauth.copy(
                accessToken = newAccessToken,
                refreshToken = response.refreshToken ?: creds.claudeAiOauth.refreshToken,
                expiresAt = if (response.expiresIn != null)
                    System.currentTimeMillis() + response.expiresIn * 1000
                else creds.claudeAiOauth.expiresAt
            )
        )
        if (store.read() != originalContent) {
            throw IllegalStateException("As credenciais do Claude Code mudaram durante a renovação; a coleta será repetida.")
        }

        store.write(json.encodeToString(CredentialsFileDto.serializer(), updated))
        return updated
    }

    @Serializable
    private data class TokenRefreshRequest(
        val grant_type: String,
        val refresh_token: String,
    )

    @Serializable
    private data class TokenRefreshResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
    )

    @Serializable
    private data class ClaudeConfigDto(
        val oauthAccount: ClaudeOauthAccountDto? = null
    )

    @Serializable
    private data class ClaudeOauthAccountDto(
        val accountUuid: String = "",
        val emailAddress: String = "",
        val organizationUuid: String? = null,
        val organizationName: String? = null
    )
}
