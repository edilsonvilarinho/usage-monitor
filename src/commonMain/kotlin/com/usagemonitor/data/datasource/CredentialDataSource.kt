package com.usagemonitor.data.datasource

/**
 * Interface para leitura de credenciais locais.
 *
 * Está no commonMain (código comum) mas a implementação fica no desktopMain
 * porque usa java.io.File, que é específico da JVM.
 *
 * Este é o padrão de Inversão de Dependência aplicado entre camadas de
 * plataforma: o código comum define O QUE precisa; a plataforma define COMO.
 */
interface CredentialDataSource {
    /**
     * Lê a sessão OAuth do Claude.ai junto da identidade da conta ativa.
     * Lança exceção se as credenciais ou a identidade não estiverem disponíveis.
     */
    suspend fun loadAnthropicSession(): AnthropicSession

    suspend fun isAnthropicSessionCurrent(session: AnthropicSession): Boolean = true
}

data class AnthropicSession(
    val accessToken: String,
    val accountContext: com.usagemonitor.domain.entity.UsageAccountContext
)
