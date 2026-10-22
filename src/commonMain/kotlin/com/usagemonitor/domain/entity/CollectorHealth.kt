package com.usagemonitor.domain.entity

/**
 * A saúde de cada parte do coletor (#440, 5.1 do protótipo), gravada por ele a cada
 * 30 s para o `usage-monitor service status` ler de fora. Só metadados: nada de token,
 * chave, prompt ou caminho de projeto.
 */
data class CollectorHealth(
    val updatedAtEpochMillis: Long,
    val nextCollectionAtEpochMillis: Long? = null,
    val sources: List<CollectorSourceHealth> = emptyList(),
    val telegram: CollectorTelegramHealth = CollectorTelegramHealth(),
    val team: CollectorTeamHealth = CollectorTeamHealth(),
    val web: CollectorWebHealth = CollectorWebHealth()
)

/** Uma conta coletada: em dia, ou com a última leitura em falha (e até quando espera, num 429). */
data class CollectorSourceHealth(
    val source: ApiSource,
    val label: String,
    val ok: Boolean,
    val retryAtEpochMillis: Long? = null,
    /**
     * A chave do alvo (`UsageTargetKey.storageKey`), para quem lê de fora achar a conta sem
     * depender do rótulo (#441). `null` no arquivo de um coletor anterior.
     */
    val target: String? = null,
    /** A frase da falha, a mesma do aviso do app (#441, 6.2). Nunca token nem caminho. */
    val failure: String? = null
)

/** O bot como o card das Configurações o mostra. [state]: `off`, `connecting`, `connected`, `failed`, `token_rejected`. */
data class CollectorTelegramHealth(
    val state: String = "off",
    val botUsername: String? = null,
    val chatCount: Int = 0
)

/** O envio para o time: ligado, a última passada boa e a última falha. */
data class CollectorTeamHealth(
    val active: Boolean = false,
    val server: String? = null,
    val lastSuccessAtEpochMillis: Long? = null,
    val lastFailure: String? = null
)

/** O servidor da rede local. [state]: `stopped`, `running`, `failed`. Nunca o token. */
data class CollectorWebHealth(
    val state: String = "stopped",
    val port: Int? = null,
    val addresses: List<String> = emptyList(),
    val failure: String? = null
)
