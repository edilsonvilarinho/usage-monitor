package com.usagemonitor

import com.usagemonitor.data.datasource.LocalConfigRequestChannel
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.data.datasource.LocalTelegramSettingsDataSource
import com.usagemonitor.domain.entity.TELEGRAM_PAIRING_TTL_MILLIS
import com.usagemonitor.domain.entity.TelegramBotSettings
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/**
 * O que o coletor faz depois de uma mudança, além de gravar: recoletar a fonte, reenviar
 * ao time. Fora do coletor (terminal sem app nem serviço), nada.
 */
internal class ConfigHooks(
    val refreshSource: (ApiSource) -> Unit = {},
    /** Um ajuste de [ConfigKeys] mudou: quem guarda o valor em memória o relê. */
    val valueChanged: (ConfigKey) -> Unit = {}
)

/**
 * Aplica uma [ConfigChange] pelos mesmos caminhos das Configurações e do bot (decisão
 * D1 da #440). Um dono só: o coletor (app ou serviço) aplica o pedido do terminal com
 * ele, as Configurações do app também, e o terminal sem coletor aplica direto nos
 * arquivos com ele.
 */
internal class ConfigApplier(
    private val store: AppSettingsStore,
    private val hooks: ConfigHooks = ConfigHooks(),
    private val clock: Clock = Clock.System
) {

    private val accounts = ConfigAccounts(store, hooks)

    fun apply(change: ConfigChange): ConfigOutcome {
        val outcome = runCatching {
            when (change) {
                is ConfigChange.SourceEnabled -> sourceEnabled(change)
                is ConfigChange.SetValue -> setValue(change)
                is ConfigChange.SetApiKey -> setApiKey(change)
                is ConfigChange.RemoveApiKey -> removeApiKey(change)
                is ConfigChange.AddAccount -> accounts.add(change)
                is ConfigChange.RenameAccount -> accounts.rename(change)
                is ConfigChange.AccountEnabled -> accounts.switch(change)
                is ConfigChange.RemoveAccount -> accounts.remove(change)
                ConfigChange.RescanAccounts -> accounts.rescan()
                is ConfigChange.TelegramSetup -> telegramSetup(change)
                is ConfigChange.TelegramEnabled -> telegramUpdate { current -> current.copy(enabled = change.enabled) }
                ConfigChange.TelegramStartPairing -> telegramStartPairing()
                is ConfigChange.TelegramRemoveChat -> telegramChat(change.chat) { current, id ->
                    current.copy(authorizedChats = current.authorizedChats.filterNot { chat -> chat.id == id })
                }
                is ConfigChange.TelegramChatReadOnly -> telegramChat(change.chat) { current, id ->
                    current.copy(authorizedChats = current.authorizedChats.map { chat -> if (chat.id == id) chat.copy(readOnly = change.readOnly) else chat })
                }
            }
        }.getOrElse { error -> ConfigOutcome.Refused(ConfigRefusals.FAILED, error.message) }
        // O terminal que lê logo depois precisa ver o valor gravado (risco R2).
        store.flush()
        return outcome
    }

    /** Grava a chave e liga a fonte: quem salva uma chave pelo terminal quer a fonte coletando. */
    private fun setApiKey(change: ConfigChange.SetApiKey): ConfigOutcome {
        val source = keySourceOf(change.source) ?: return ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_SOURCE, change.source)
        val apiKey = change.apiKey.trim()
        store.apiKeyDataSource.save(source, apiKey)
        store.apiKeySettings.update { current -> current.withKey(source, apiKey) }
        persistEnabledApis(store, store.enabledApis.value + source)
        hooks.refreshSource(source)
        return ConfigOutcome.Applied()
    }

    /**
     * Apagar a chave desliga a fonte, e as duas gravações andam juntas, como na aba APIs
     * (`SettingsActions.removeApiKey`): ligada sem chave, a coleta falharia a cada tique.
     */
    private fun removeApiKey(change: ConfigChange.RemoveApiKey): ConfigOutcome {
        val source = keySourceOf(change.source) ?: return ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_SOURCE, change.source)
        store.apiKeyDataSource.clear(source)
        store.apiKeySettings.update { current -> current.withoutKey(source) }
        persistEnabledApis(store, store.enabledApis.value - source)
        hooks.refreshSource(source)
        return ConfigOutcome.Applied()
    }

    private fun telegramSetup(change: ConfigChange.TelegramSetup): ConfigOutcome =
        telegramUpdate { current -> current.copy(botToken = change.botToken.trim(), enabled = true) }

    private fun telegramUpdate(transform: (TelegramBotSettings) -> TelegramBotSettings): ConfigOutcome {
        updateTelegramSettings(store, transform)
        return ConfigOutcome.Applied()
    }

    /**
     * Abre um código por [TELEGRAM_PAIRING_TTL_MILLIS], como o "Parear" das Configurações
     * (`TelegramBotActions.startPairing`); o anterior deixa de valer.
     */
    private fun telegramStartPairing(): ConfigOutcome {
        if (store.telegramSettingsFlow.value.botToken.isBlank()) {
            return ConfigOutcome.Refused(ConfigRefusals.NO_BOT_TOKEN)
        }
        val code = LocalTelegramSettingsDataSource.newPairingCode()
        val expiresAt = clock.now().toEpochMilliseconds() + TELEGRAM_PAIRING_TTL_MILLIS
        updateTelegramSettings(store) { current -> current.copy(enabled = true, pairingCode = code, pairingExpiresAtMillis = expiresAt) }
        return ConfigOutcome.Applied(details = mapOf(DETAIL_CODE to code, DETAIL_EXPIRES_AT to expiresAt.toString()))
    }

    /** A conversa pelo id ou pelo nome (sem caixa e sem `@`). */
    private fun telegramChat(reference: String, transform: (TelegramBotSettings, Long) -> TelegramBotSettings): ConfigOutcome {
        val wanted = reference.trim().removePrefix("@")
        val chats = store.telegramSettingsFlow.value.authorizedChats
        val chat = chats.firstOrNull { candidate -> candidate.id.toString() == wanted }
            ?: chats.filter { candidate -> candidate.name.removePrefix("@").equals(wanted, ignoreCase = true) }.singleOrNull()
            ?: return ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_CHAT, reference)
        updateTelegramSettings(store) { current -> transform(current, chat.id) }
        return ConfigOutcome.Applied(details = mapOf(DETAIL_CHAT to chat.name))
    }

    private fun keySourceOf(name: String): ApiSource? =
        ApiSource.entries.firstOrNull { entry -> entry.name == name && entry in API_KEY_DEPENDENT_SOURCES }

    /** Um ajuste com nome: o valor é conferido aqui também, porque o pedido pode vir de outra versão. */
    private fun setValue(change: ConfigChange.SetValue): ConfigOutcome {
        val key = ConfigKeys.named(change.key) ?: return ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_KEY, change.key)
        val normalized = ConfigValues.normalize(key.type, change.value)
            ?: return ConfigOutcome.Refused(ConfigRefusals.INVALID_VALUE, key.name)
        key.write(store, normalized.takeUnless { value -> value == ConfigValues.OFF })
        hooks.valueChanged(key)
        return ConfigOutcome.Applied(restartNeeded = key.restartNeeded)
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

/** As chaves do que uma mudança aplicada devolve ([ConfigOutcome.Applied.details]). */
internal const val DETAIL_CODE = "code"
internal const val DETAIL_EXPIRES_AT = "expires_at"
internal const val DETAIL_CHAT = "chat"

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
