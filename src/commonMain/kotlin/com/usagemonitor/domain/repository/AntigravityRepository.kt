package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

/**
 * Falhas do Antigravity que não se resolvem tentando de novo: dependem de o usuário
 * instalar, atualizar ou autenticar o CLI, ou de reiniciar o app depois que o
 * disjuntor abriu. A tela as trata como configuração — banner sem "Tentar
 * novamente" e sem toast a cada coleta. [safeMessage] é a mensagem inteira que
 * chega à tela e o texto que a classificação procura.
 */
enum class AntigravityUsageFailureKind(val safeMessage: String) {
    CLI_NOT_INSTALLED("Antigravity CLI is not installed or is not on PATH"),
    UNSUPPORTED_LAUNCHER("Antigravity CLI must be the agy executable itself, not a .cmd or .bat launcher"),
    UNVERIFIED_VERSION("Antigravity CLI version is older than the verified 1.2.9 or could not be read"),
    AUTHENTICATION_UNAVAILABLE("Antigravity CLI authentication is unavailable"),

    /**
     * O CLI não provou ter respondido `/usage` sozinho. Repetir a chamada a cada
     * coleta repetiria o gasto de cota de modelo, então a fonte fica parada até o
     * app reiniciar.
     */
    COLLECTION_PAUSED(
        "Antigravity CLI collection is paused until the app restarts because /usage was not answered by the CLI itself"
    )
}

interface AntigravityRepository {
    suspend fun getUsage(): Result<ApiUsageStats>

    /**
     * Descarta a leitura guardada para que a próxima coleta chame o CLI.
     *
     * Só o pedido explícito do usuário invalida: o despertar por reset de outra
     * fonte e o poll reusam a leitura dentro do TTL, que existe para não abrir um
     * processo a cada um deles.
     */
    fun invalidateCachedReading() {}
}
