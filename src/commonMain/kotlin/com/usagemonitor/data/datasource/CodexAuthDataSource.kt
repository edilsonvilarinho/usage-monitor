package com.usagemonitor.data.datasource

/**
 * Interface para leitura da sessão local do Codex autenticada com ChatGPT.
 *
 * A implementação fica no desktopMain porque lê ficheiros do utilizador.
 */
interface CodexAuthDataSource {
    suspend fun loadSession(): CodexSession

    suspend fun isSessionCurrent(session: CodexSession): Boolean = true
}

/**
 * Dados mínimos necessários para autenticar chamadas privadas do Codex.
 */
data class CodexSession(
    val accessToken: String,
    val capSid: String,
    val accountContext: com.usagemonitor.domain.entity.UsageAccountContext
)
