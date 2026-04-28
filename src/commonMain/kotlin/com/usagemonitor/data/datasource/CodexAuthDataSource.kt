package com.usagemonitor.data.datasource

/**
 * Interface para leitura da sessão local do Codex autenticada com ChatGPT.
 *
 * A implementação fica no desktopMain porque lê ficheiros do utilizador.
 */
interface CodexAuthDataSource {
    suspend fun loadSession(): CodexSession
}

/**
 * Dados mínimos necessários para autenticar chamadas privadas do Codex.
 */
data class CodexSession(
    val accessToken: String,
    val capSid: String
)
