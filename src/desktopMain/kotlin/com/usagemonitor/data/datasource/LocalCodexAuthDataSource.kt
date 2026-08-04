package com.usagemonitor.data.datasource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey

class LocalCodexAuthDataSource(
    private val homeDirProvider: () -> String? = { System.getProperty("user.home") },
    private val authFileProvider: (String) -> File = { homeDir -> File("$homeDir/.codex/auth.json") },
    private val capSidFileProvider: (String) -> File = { homeDir -> File("$homeDir/.codex/cap_sid") },
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CodexAuthDataSource {

    override suspend fun loadSession(): CodexSession {
        val homeDir = homeDirProvider()
            ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

        val authFile = authFileProvider(homeDir)
        if (!authFile.exists()) {
            throw IllegalStateException(
                "Sessão do Codex não encontrada: ${authFile.absolutePath}. " +
                "Abra o Codex e autentique-se com a sua conta ChatGPT."
            )
        }

        val capSidFile = capSidFileProvider(homeDir)
        if (!capSidFile.exists()) {
            throw IllegalStateException(
                "Cookie cap_sid do Codex não encontrado: ${capSidFile.absolutePath}. " +
                "Abra o Codex Desktop para renovar a sessão."
            )
        }

        val auth = json.decodeFromString<CodexAuthFileDto>(authFile.readText())
        val accessToken = auth.tokens.accessToken.ifBlank {
            throw IllegalStateException("Sessão do Codex inválida: access_token ausente.")
        }
        val workspaceId = auth.tokens.accountId.ifBlank {
            throw IllegalStateException("Sessão do Codex inválida: account_id ausente.")
        }
        val identityPayload = decodeJwtPayload(auth.tokens.idToken)
            ?: throw IllegalStateException("Sessão do Codex inválida: id_token ilegível.")
        val providerAccountId = identityPayload["sub"]?.jsonPrimitive?.content.orEmpty().ifBlank {
            throw IllegalStateException("Sessão do Codex inválida: identificador do usuário ausente.")
        }
        val email = identityPayload["email"]?.jsonPrimitive?.content.orEmpty().ifBlank {
            throw IllegalStateException("Sessão do Codex inválida: e-mail da conta ausente.")
        }
        val capSid = capSidFile.readText().trim()
        if (capSid.isBlank()) {
            throw IllegalStateException("Sessão do Codex inválida: cap_sid vazio.")
        }

        return CodexSession(
            accessToken = accessToken,
            capSid = capSid,
            accountContext = UsageAccountContext(
                key = UsageAccountKey(
                    source = ApiSource.CODEX,
                    providerAccountId = providerAccountId,
                    workspaceId = workspaceId
                ),
                email = email
            )
        )
    }

    override suspend fun isSessionCurrent(session: CodexSession): Boolean {
        val current = try {
            loadSession()
        } catch (_: Throwable) {
            return false
        }

        return current.accessToken == session.accessToken &&
            current.capSid == session.capSid &&
            current.accountContext.key == session.accountContext.key
    }

    private fun decodeJwtPayload(token: String): kotlinx.serialization.json.JsonObject? {
        if (token.isBlank()) {
            return null
        }
        val parts = token.split('.')
        if (parts.size < 2) {
            return null
        }

        return try {
            val decoded = Base64.getUrlDecoder().decode(parts[1])
            json.parseToJsonElement(decoded.decodeToString()).jsonObject
        } catch (_: Throwable) {
            null
        }
    }
}

@Serializable
private data class CodexAuthFileDto(
    val tokens: CodexTokensDto
)

@Serializable
private data class CodexTokensDto(
    @SerialName("id_token") val idToken: String = "",
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("account_id") val accountId: String = ""
)
