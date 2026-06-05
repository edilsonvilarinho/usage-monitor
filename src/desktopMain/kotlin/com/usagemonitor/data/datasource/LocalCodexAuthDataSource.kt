package com.usagemonitor.data.datasource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

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
