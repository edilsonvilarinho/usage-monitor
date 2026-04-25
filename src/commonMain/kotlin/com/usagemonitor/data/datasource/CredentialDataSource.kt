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
     * Lê e retorna o accessToken OAuth do Claude.ai.
     * Lança exceção se o ficheiro não existir ou o token estiver expirado.
     */
    fun loadAnthropicAccessToken(): String
}
