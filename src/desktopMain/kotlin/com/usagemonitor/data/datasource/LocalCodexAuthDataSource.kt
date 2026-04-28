package com.usagemonitor.data.datasource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class LocalCodexAuthDataSource : CodexAuthDataSource {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadSession(): CodexSession {
        val homeDir = System.getProperty("user.home")
            ?: throw IllegalStateException("Propriedade 'user.home' não disponível")

        val authFile = File("$homeDir/.codex/auth.json")
        if (!authFile.exists()) {
            throw IllegalStateException(
                "Sessão do Codex não encontrada: ${authFile.absolutePath}. " +
                "Abra o Codex e autentique-se com a sua conta ChatGPT."
            )
        }

        val capSidFile = File("$homeDir/.codex/cap_sid")
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
        val capSid = capSidFile.readText().trim()
        if (capSid.isBlank()) {
            throw IllegalStateException("Sessão do Codex inválida: cap_sid vazio.")
        }

        return CodexSession(
            accessToken = accessToken,
            capSid = capSid
        )
    }
}

@Serializable
private data class CodexAuthFileDto(
    val tokens: CodexTokensDto
)

@Serializable
private data class CodexTokensDto(
    @SerialName("access_token") val accessToken: String = ""
)
