package com.usagemonitor

import com.usagemonitor.data.datasource.LocalConfigRequestChannel
import com.usagemonitor.domain.entity.ApiSource

/**
 * O que o coletor faz depois de uma mudança, além de gravar: recoletar a fonte, reenviar
 * ao time. Fora do coletor (terminal sem app nem serviço), nada.
 */
internal class ConfigHooks(
    val refreshSource: (ApiSource) -> Unit = {}
)

/**
 * Aplica uma [ConfigChange] pelos mesmos caminhos das Configurações e do bot (decisão
 * D1 da #440). Um dono só: o coletor (app ou serviço) aplica o pedido do terminal com
 * ele, as Configurações do app também, e o terminal sem coletor aplica direto nos
 * arquivos com ele.
 */
internal class ConfigApplier(
    private val store: AppSettingsStore,
    private val hooks: ConfigHooks = ConfigHooks()
) {

    fun apply(change: ConfigChange): ConfigOutcome {
        val outcome = runCatching {
            when (change) {
                is ConfigChange.SourceEnabled -> sourceEnabled(change)
            }
        }.getOrElse { error -> ConfigOutcome.Refused(ConfigRefusals.FAILED, error.message) }
        // O terminal que lê logo depois precisa ver o valor gravado (risco R2).
        store.flush()
        return outcome
    }

    /**
     * Fonte que depende de chave não liga sem ela: a coleta falharia a cada tique, e o
     * filtro de arranque a desligaria de qualquer forma (`persistedEnabledApis`).
     */
    private fun sourceEnabled(change: ConfigChange.SourceEnabled): ConfigOutcome {
        val source = ApiSource.entries.firstOrNull { entry -> entry.name == change.source }
            ?: return ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_SOURCE, change.source)
        if (change.enabled && source in API_KEY_DEPENDENT_SOURCES && store.apiKeySettings.value.forSource(source) == null) {
            return ConfigOutcome.Refused(ConfigRefusals.KEY_REQUIRED, source.name)
        }
        val current = store.enabledApis.value
        persistEnabledApis(store, if (change.enabled) current + source else current - source)
        hooks.refreshSource(source)
        return ConfigOutcome.Applied()
    }
}

/**
 * Atende os pedidos à espera no canal, na ordem de chegada: aplica, responde e avisa
 * [onApplied] do que mudou. Pedido ilegível (versão vizinha com um tipo que esta não
 * conhece) é recusado, e não ignorado: o terminal precisa ouvir que nada mudou.
 */
internal fun serveConfigRequests(channel: LocalConfigRequestChannel, applier: ConfigApplier, onApplied: (ConfigChange) -> Unit = {}) {
    for ((id, payload) in channel.takePending()) {
        val change = ConfigJson.decodeChange(payload)
        val outcome = change?.let(applier::apply) ?: ConfigOutcome.Refused(ConfigRefusals.FAILED, "unreadable request")
        channel.reply(id, ConfigJson.encodeOutcome(outcome))
        if (change != null && outcome is ConfigOutcome.Applied) {
            onApplied(change)
        }
    }
}
