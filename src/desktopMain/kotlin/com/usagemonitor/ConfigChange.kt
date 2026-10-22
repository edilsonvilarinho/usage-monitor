package com.usagemonitor

import com.usagemonitor.domain.entity.redactedSecret
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Uma mudança de configuração pedida pelo terminal (#440), como dado: é o que viaja até
 * o coletor vivo num pedido (decisão D1), e o que o [ConfigApplier] aplica — no coletor,
 * ou direto nos arquivos quando nada coleta. Nomes estáveis no JSON: um app aberto de
 * versão vizinha precisa ler o pedido.
 */
@Serializable
internal sealed interface ConfigChange {

    /** `sources enable|disable <fonte>`. A fonte vai pelo nome do enum. */
    @Serializable
    @SerialName("source_enabled")
    data class SourceEnabled(val source: String, val enabled: Boolean) : ConfigChange

    /** `keys set <fonte>`: grava a chave e liga a fonte. A chave viaja aqui; o `toString` a esconde. */
    @Serializable
    @SerialName("api_key_set")
    data class SetApiKey(val source: String, val apiKey: String) : ConfigChange {
        override fun toString(): String = "SetApiKey(source=$source, apiKey=${redactedSecret(apiKey)})"
    }

    /** `keys remove <fonte>`: apaga a chave e desliga a fonte, como na aba APIs. */
    @Serializable
    @SerialName("api_key_remove")
    data class RemoveApiKey(val source: String) : ConfigChange

    /**
     * `config set <ajuste> <valor>` ([ConfigKeys]). O valor de um ajuste secreto
     * (`proxy.password`) viaja aqui, então o `toString` o esconde.
     */
    @Serializable
    @SerialName("set_value")
    data class SetValue(val key: String, val value: String) : ConfigChange {
        override fun toString(): String =
            "SetValue(key=$key, value=${if (ConfigKeys.named(key)?.secret == true) redactedSecret(value) else value})"
    }
}

/** O que aconteceu com uma [ConfigChange]. */
@Serializable
internal sealed interface ConfigOutcome {

    /** Aplicada. [restartNeeded]: só vale depois de reiniciar quem coleta (o proxy). */
    @Serializable
    @SerialName("applied")
    data class Applied(val restartNeeded: Boolean = false, val details: Map<String, String> = emptyMap()) : ConfigOutcome

    /**
     * Recusada, com o motivo por código estável (texto, e não enum: um enum novo a cada
     * comando seria valor novo em enum existente) e um detalhe opcional.
     */
    @Serializable
    @SerialName("refused")
    data class Refused(val code: String, val detail: String? = null) : ConfigOutcome
}

/** Os códigos de recusa. */
internal object ConfigRefusals {
    const val UNKNOWN_SOURCE = "unknown_source"
    const val KEY_REQUIRED = "key_required"
    const val UNKNOWN_KEY = "unknown_key"
    const val INVALID_VALUE = "invalid_value"
    const val FAILED = "failed"
}

internal object ConfigJson {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encodeChange(change: ConfigChange): String = json.encodeToString(ConfigChange.serializer(), change)

    fun decodeChange(text: String): ConfigChange? = runCatching { json.decodeFromString(ConfigChange.serializer(), text) }.getOrNull()

    fun encodeOutcome(outcome: ConfigOutcome): String = json.encodeToString(ConfigOutcome.serializer(), outcome)

    fun decodeOutcome(text: String): ConfigOutcome? = runCatching { json.decodeFromString(ConfigOutcome.serializer(), text) }.getOrNull()
}
