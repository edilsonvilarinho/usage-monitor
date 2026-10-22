package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import java.io.File

/** Uma conta, Anthropic ou Codex extra, como o terminal a vê e a encontra. */
internal data class ConfigAccount(val source: ApiSource, val id: String, val label: String, val directory: String, val enabled: Boolean)

/**
 * As contas do terminal (#440, `accounts`), pelos mesmos registros e métodos das
 * Configurações (aba Contas). Mudar uma conta publica os flows que a coleta lê e
 * recoleta a fonte, como `SettingsActions`. Monitorar uma conta liga a fonte dela: quem
 * adiciona uma conta pelo terminal quer vê-la coletando.
 */
internal class ConfigAccounts(private val store: AppSettingsStore, private val hooks: ConfigHooks) {

    fun add(change: ConfigChange.AddAccount): ConfigOutcome {
        val directory = File(change.directory)
        val label = change.label?.trim()?.takeIf { text -> text.isNotEmpty() }
        return when (change.source) {
            ApiSource.ANTHROPIC.name -> {
                store.profileRegistry.addManual(directory).getOrElse { error -> return ConfigOutcome.Refused(ConfigRefusals.FAILED, error.message) }
                val added = store.profileRegistry.profiles.value.firstOrNull { record ->
                    AnthropicProfileRegistry.normalizedPath(record.configDirectory) == AnthropicProfileRegistry.normalizedPath(directory.canonicalPath)
                } ?: return ConfigOutcome.Refused(ConfigRefusals.FAILED, directory.path)
                store.profileRegistry.setEnabled(added.id, true)
                label?.let { text -> renameAnthropic(added.id, text) }
                changed(ApiSource.ANTHROPIC, label ?: added.label)
            }
            ApiSource.CODEX.name -> {
                val added = store.codexProfileRegistry.add(directory).getOrElse { error -> return ConfigOutcome.Refused(ConfigRefusals.FAILED, error.message) }
                store.codexProfileRegistry.setEnabled(added.id, true)
                label?.let { text -> store.codexProfileRegistry.rename(added.id, text) }
                changed(ApiSource.CODEX, label ?: added.label)
            }
            else -> ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_SOURCE, change.source)
        }
    }

    fun rename(change: ConfigChange.RenameAccount): ConfigOutcome {
        val label = change.label.trim().takeIf { text -> text.isNotEmpty() } ?: return ConfigOutcome.Refused(ConfigRefusals.INVALID_VALUE, "label")
        val account = find(change.account).getOrElse { refused -> return refused }
        when (account.source) {
            ApiSource.ANTHROPIC -> renameAnthropic(account.id, label)
            else -> store.codexProfileRegistry.rename(account.id, label)
        }
        return changed(account.source, label)
    }

    fun switch(change: ConfigChange.AccountEnabled): ConfigOutcome {
        val account = find(change.account).getOrElse { refused -> return refused }
        when (account.source) {
            ApiSource.ANTHROPIC -> store.profileRegistry.setEnabled(account.id, change.enabled)
            else -> store.codexProfileRegistry.setEnabled(account.id, change.enabled)
        }
        return changed(account.source, account.label, enableSource = change.enabled)
    }

    fun remove(change: ConfigChange.RemoveAccount): ConfigOutcome {
        val account = find(change.account).getOrElse { refused -> return refused }
        // A conta padrão é a do próprio Claude Code: sai desligando, não removendo.
        if (account.source == ApiSource.ANTHROPIC && account.id == DEFAULT_ANTHROPIC_PROFILE_ID) {
            return ConfigOutcome.Refused(ConfigRefusals.DEFAULT_ACCOUNT, account.label)
        }
        when (account.source) {
            ApiSource.ANTHROPIC -> store.profileRegistry.removeFromMonitor(account.id)
            else -> store.codexProfileRegistry.remove(account.id)
        }
        return changed(account.source, account.label, enableSource = false)
    }

    fun rescan(): ConfigOutcome {
        store.profileRegistry.rescan(restoreRemoved = true)
        return changed(ApiSource.ANTHROPIC, label = "", enableSource = false)
    }

    /**
     * A conta pelo apelido (sem caixa) ou pelo id. Dois apelidos iguais são ambíguos, e a
     * recusa lista os ids: um script nunca muda a conta errada por acaso.
     */
    private fun find(reference: String): Result<ConfigAccount, ConfigOutcome.Refused> {
        val wanted = reference.trim()
        val all = accountsOf(store)
        // O id inteiro, ou o começo dele que a lista mostra (8 caracteres), se só uma conta o tiver.
        val byId = all.filter { account -> account.id == wanted }
            .ifEmpty { all.filter { account -> wanted.length >= MIN_ID_PREFIX && account.id.startsWith(wanted) } }
        val matches = byId.ifEmpty { all.filter { account -> account.label.equals(wanted, ignoreCase = true) } }
        return when (matches.size) {
            1 -> Result.Ok(matches.single())
            0 -> Result.Err(ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_ACCOUNT, wanted))
            else -> Result.Err(ConfigOutcome.Refused(ConfigRefusals.AMBIGUOUS_ACCOUNT, matches.joinToString(", ") { account -> account.id }))
        }
    }

    /** O apelido vai ao disco agora: o registro grava com atraso, e o terminal sai logo depois. */
    private fun renameAnthropic(profileId: String, label: String) {
        store.profileRegistry.updateLabel(profileId, label)
        store.profileRegistry.flushPendingLabelWrite()
    }

    private fun changed(source: ApiSource, label: String, enableSource: Boolean = true): ConfigOutcome {
        store.enabledAnthropicProfiles.value = resolveAnthropicProfiles(store.profileRegistry, store.profileRegistry.profiles.value).enabledProfiles
        store.enabledCodexProfiles.value = store.codexProfileRegistry.enabledProfiles
        if (enableSource && source !in store.enabledApis.value) {
            persistEnabledApis(store, store.enabledApis.value + source)
        }
        hooks.refreshSource(source)
        return ConfigOutcome.Applied(details = mapOf("label" to label, "source" to source.name))
    }

    private sealed interface Result<out T, out E> {
        data class Ok<T>(val value: T) : Result<T, Nothing>
        data class Err<E>(val error: E) : Result<Nothing, E>
    }

    private inline fun <T, E> Result<T, E>.getOrElse(onError: (E) -> T): T =
        when (this) {
            is Result.Ok -> value
            is Result.Err -> onError(error)
        }

    companion object {
        private const val MIN_ID_PREFIX = 4

        /**
         * As contas que se pode mudar, na ordem das Configurações: Anthropic (a padrão
         * primeiro) e depois as Codex extras. A conta Codex padrão não está aqui: ela não
         * tem registro, e liga e desliga com a fonte (`sources`).
         */
        fun accountsOf(store: AppSettingsStore): List<ConfigAccount> =
            store.profileRegistry.profiles.value.map { record -> ConfigAccount(ApiSource.ANTHROPIC, record.id, record.label, record.configDirectory, record.enabled) } +
                store.codexProfileRegistry.profiles.value.map { record -> ConfigAccount(ApiSource.CODEX, record.id, record.label, record.directory, record.enabled) }
    }
}
