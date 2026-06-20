package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CredentialsFileDto
import com.usagemonitor.data.dto.OAuthCredentialsDto
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
    }
) : CredentialDataSource {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedExpiresAt: Long = 0L

    override suspend fun loadAnthropicAccessToken(): String {
        val now = System.currentTimeMillis()
        val cached = cachedToken
        if (cached != null && cachedExpiresAt - now > REFRESH_MARGIN_MS) {
            return cached
        }

        val credentialsFile = credentialsFile()

        if (!credentialsFile.exists()) {
            throw IllegalStateException(
                "Credenciais não encontradas: ${credentialsFile.absolutePath}. " +
                "Execute o Claude Code CLI para autenticar."
            )
        }

        val creds = json.decodeFromString<CredentialsFileDto>(credentialsFile.readText())

        val needsRefresh = creds.claudeAiOauth.expiresAt - now < REFRESH_MARGIN_MS

        if (needsRefresh && creds.claudeAiOauth.refreshToken.isNotEmpty()) {
            return refreshToken(credentialsFile, creds)
        }

        cachedToken = creds.claudeAiOauth.accessToken
        cachedExpiresAt = creds.claudeAiOauth.expiresAt
        return creds.claudeAiOauth.accessToken
    }

    override fun invalidateAnthropicAccessTokenCache() {
        cachedToken = null
        cachedExpiresAt = 0L
    }

    private fun credentialsFile(): File = File("${homeDirProvider()}/.claude/.credentials.json")

    private suspend fun refreshToken(credentialsFile: File, creds: CredentialsFileDto): String {
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
        cachedToken = newAccessToken
        cachedExpiresAt = updated.claudeAiOauth.expiresAt
        return newAccessToken
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
}
