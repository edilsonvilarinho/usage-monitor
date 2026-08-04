package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CredentialsFileDto
import com.usagemonitor.data.dto.OAuthCredentialsDto
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
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val REFRESH_MARGIN_MS = 5 * 60 * 1000L  // renova se expira em menos de 5 min
private const val OAUTH_REFRESH_URL = "https://console.anthropic.com/v1/oauth/token"

class LocalCredentialDataSource(
    private val httpClient: HttpClient,
    // Costura de teste: permite apontar para um diretório temporário em vez de ~/.claude.
    private val homeDirProvider: () -> String = {
        System.getProperty("user.home")
            ?: throw IllegalStateException("Propriedade 'user.home' não disponível")
    },
    private val claudeConfigFileProvider: (String) -> File = { homeDir -> File(homeDir, ".claude.json") }
) : CredentialDataSource {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override suspend fun loadAnthropicSession(): AnthropicSession {
        val now = System.currentTimeMillis()
        val credentialsFile = credentialsFile()
        if (!credentialsFile.exists()) {
            throw IllegalStateException(
                "Credenciais não encontradas: ${credentialsFile.absolutePath}. " +
                "Execute o Claude Code CLI para autenticar."
            )
        }

        var creds = json.decodeFromString<CredentialsFileDto>(credentialsFile.readText())

        val needsRefresh = creds.claudeAiOauth.expiresAt - now < REFRESH_MARGIN_MS

        if (needsRefresh && creds.claudeAiOauth.refreshToken.isNotEmpty()) {
            creds = refreshToken(credentialsFile, creds)
        }

        val accessToken = creds.claudeAiOauth.accessToken.ifBlank {
            throw IllegalStateException("Credenciais do Claude Code inválidas: accessToken ausente.")
        }

        return AnthropicSession(
            accessToken = accessToken,
            accountContext = loadAccountContext()
        )
    }

    override suspend fun isAnthropicSessionCurrent(session: AnthropicSession): Boolean {
        val credentialsFile = credentialsFile()
        if (!credentialsFile.exists()) {
            return false
        }

        return try {
            val creds = json.decodeFromString<CredentialsFileDto>(credentialsFile.readText())
            val currentAccount = loadAccountContext()
            creds.claudeAiOauth.accessToken == session.accessToken &&
                currentAccount.key == session.accountContext.key
        } catch (_: Throwable) {
            false
        }
    }

    private fun credentialsFile(): File = File("${homeDirProvider()}/.claude/.credentials.json")

    private fun loadAccountContext(): UsageAccountContext {
        val homeDir = homeDirProvider()
        val configFile = claudeConfigFileProvider(homeDir)
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

    private suspend fun refreshToken(credentialsFile: File, creds: CredentialsFileDto): CredentialsFileDto {
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
        atomicWriteText(
            target = credentialsFile,
            content = json.encodeToString(CredentialsFileDto.serializer(), updated)
        )
        return updated
    }

    private fun atomicWriteText(target: File, content: String) {
        val parentDir = target.parentFile ?: throw IllegalStateException("Diretório pai do ficheiro de credenciais não encontrado.")
        parentDir.mkdirs()
        val tempFile = File(parentDir, "${target.name}.tmp")
        try {
            Files.writeString(tempFile.toPath(), content)
            try {
                Files.move(
                    tempFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tempFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
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
