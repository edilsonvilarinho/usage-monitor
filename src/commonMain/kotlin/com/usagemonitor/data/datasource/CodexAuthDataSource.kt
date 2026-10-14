package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.redactedSecret
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
) {
    /** Segredo nunca no `toString` (log, relatório de bug, mensagem de exceção). */
    override fun toString(): String =
        "CodexSession(accessToken=${redactedSecret(accessToken)}, capSid=${redactedSecret(capSid)}, accountContext=$accountContext)"
}
