package com.usagemonitor.data.datasource

/**
 * Nome da máquina local. As variáveis de ambiente cobrem Windows e Linux sem tocar
 * em DNS; `InetAddress` fica como último recurso.
 *
 * Saiu do `LocalCliSessionDataSource` na #408: o índice CLI grava o nome em cada
 * turno e o bot o escreve nas mensagens (AA20) — um dono só para a mesma resposta.
 */
internal fun resolveLocalHostName(): String? {
    val fromEnvironment = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
        ?: System.getenv("HOSTNAME")?.takeIf { it.isNotBlank() }
    if (fromEnvironment != null) {
        return fromEnvironment
    }
    return runCatching { java.net.InetAddress.getLocalHost().hostName }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}
