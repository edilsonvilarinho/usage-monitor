package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CredentialsFileDto
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Implementação JVM da interface CredentialDataSource.
 *
 * Fica no desktopMain porque usa java.io.File, que é específico da JVM.
 * O commonMain só conhece a interface — nunca esta classe diretamente.
 *
 * Resolve o caminho do ficheiro dinamicamente:
 *   Linux/Mac: /home/usuario/.claude/.credentials.json
 *   Windows:   C:\Users\usuario\.claude\.credentials.json
 */
class LocalCredentialDataSource : CredentialDataSource {

    // Json leniente: ignora campos desconhecidos (o ficheiro pode ter campos extras)
    private val json = Json { ignoreUnknownKeys = true }

    override fun loadAnthropicAccessToken(): String {
        val homeDir = System.getProperty("user.home")
            ?: throw IllegalStateException("Propriedade 'user.home' não disponível no sistema")

        val credentialsFile = File("$homeDir/.claude/.credentials.json")

        if (!credentialsFile.exists()) {
            throw IllegalStateException(
                "Ficheiro de credenciais não encontrado: ${credentialsFile.absolutePath}\n" +
                "Execute o Claude Code CLI para gerar as credenciais."
            )
        }

        val fileContent = credentialsFile.readText()
        val credentials = json.decodeFromString<CredentialsFileDto>(fileContent)

        // Verifica se o token ainda é válido
        val nowMs = Clock.System.now().toEpochMilliseconds()
        if (nowMs > credentials.claudeAiOauth.expiresAt) {
            throw IllegalStateException(
                "Token OAuth do Claude.ai expirado. " +
                "Execute `claude` no terminal para renovar as credenciais."
            )
        }

        return credentials.claudeAiOauth.accessToken
    }
}
