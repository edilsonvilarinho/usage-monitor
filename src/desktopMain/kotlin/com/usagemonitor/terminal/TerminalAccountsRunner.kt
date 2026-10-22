package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileRegistry
import com.usagemonitor.CodexProfileRegistry
import com.usagemonitor.ConfigChange
import com.usagemonitor.ConfigRefusals
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.terminal.TerminalAccountRow
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import java.io.File
import java.util.prefs.Preferences

/**
 * `accounts …` (#440, aba Contas). Listar lê os registros gravados, sem montar nenhum
 * (montar redescobre e grava); mudar vai pelo [TerminalConfigClient].
 */
internal class TerminalAccountsRunner(
    private val client: TerminalConfigClient,
    private val preferences: Preferences,
    private val home: File,
    /** A conta Codex padrão: `CODEX_HOME` ou `~/.codex`. */
    private val codexHome: File,
    private val codexSourceEnabled: () -> Boolean,
    /** No macOS a conta padrão guarda o login no Keychain, e o disco não diz se está pronta. */
    private val credentialsInKeychain: Boolean
) {

    fun run(command: AccountsCommand, labels: TerminalConfigLabels, profile: TerminalProfile): TerminalOutput {
        val glyphs = profile.glyphs
        return when (command) {
            AccountsCommand.List -> TerminalConfigView.accounts(rows(), labels, profile)
            is AccountsCommand.Add -> {
                // Absoluto aqui: o coletor roda em outro diretório de trabalho.
                val directory = File(command.directory).absoluteFile.normalize().path
                val source = command.source.displayName()
                val label = command.label ?: File(directory).name
                delivered(client.apply(ConfigChange.AddAccount(command.source.name, directory, command.label)), labels, profile, labels.accountAdded(source, label)) { refused ->
                    TerminalConfigView.warning(labels.accountFailed(refused.detail), null, glyphs)
                }
            }
            is AccountsCommand.Rename -> accountChange(ConfigChange.RenameAccount(command.account, command.label), command.account, labels, profile, labels.accountRenamed(command.label))
            is AccountsCommand.Switch -> {
                val label = rowFor(command.account)?.label ?: command.account
                accountChange(ConfigChange.AccountEnabled(command.account, command.enabled), command.account, labels, profile, labels.accountSwitched(label, command.enabled))
            }
            is AccountsCommand.Remove -> {
                val label = rowFor(command.account)?.label ?: command.account
                accountChange(ConfigChange.RemoveAccount(command.account), command.account, labels, profile, labels.accountRemoved(label))
            }
            AccountsCommand.Rescan -> delivered(client.apply(ConfigChange.RescanAccounts), labels, profile, labels.accountsRescanned) { null }
        }
    }

    private fun accountChange(change: ConfigChange, account: String, labels: TerminalConfigLabels, profile: TerminalProfile, doneText: String): TerminalOutput {
        val glyphs = profile.glyphs
        return delivered(client.apply(change), labels, profile, doneText) { refused ->
            when (refused.code) {
                ConfigRefusals.UNKNOWN_ACCOUNT -> TerminalConfigView.warning(labels.unknownAccount(account), "usage-monitor accounts list", glyphs)
                ConfigRefusals.AMBIGUOUS_ACCOUNT -> TerminalConfigView.warning(labels.ambiguousAccount(account, refused.detail.orEmpty()), null, glyphs)
                ConfigRefusals.DEFAULT_ACCOUNT -> TerminalConfigView.warning(labels.defaultAccount, "usage-monitor accounts disable $DEFAULT_ANTHROPIC_PROFILE_ID", glyphs)
                else -> null
            }
        }
    }

    /** O apelido mostrado na saída: o da lista, quando a referência bate com uma conta só. */
    private fun rowFor(reference: String): TerminalAccountRow? =
        rows().filter { row -> !row.bySource && (row.label.equals(reference, ignoreCase = true) || row.id == reference) }.singleOrNull()

    /** Na ordem das Configurações: Anthropic (a padrão primeiro), Codex padrão e as Codex extras. */
    private fun rows(): List<TerminalAccountRow> {
        val anthropic = AnthropicProfileRegistry.readStored(preferences)
            .filterNot { record -> record.hidden }
            .sortedWith(
                compareBy<com.usagemonitor.AnthropicProfileRecord> { record -> record.id != DEFAULT_ANTHROPIC_PROFILE_ID }
                    .thenBy { record -> record.label.lowercase() }
                    .thenBy { record -> AnthropicProfileRegistry.normalizedPath(record.configDirectory) }
            )
            .map { record ->
                val directory = File(record.configDirectory)
                val identity = if (record.id == DEFAULT_ANTHROPIC_PROFILE_ID) File(home, ".claude.json") else File(directory, ".claude.json")
                val ready = if (credentialsInKeychain && record.id == DEFAULT_ANTHROPIC_PROFILE_ID) {
                    identity.isFile.takeIf { exists -> !exists }
                } else {
                    File(directory, ".credentials.json").isFile && identity.isFile
                }
                TerminalAccountRow(ApiSource.ANTHROPIC, record.label, shortId(record.id), shown(record.configDirectory), record.enabled, ready)
            }
        val codexDefault = TerminalAccountRow(
            ApiSource.CODEX, CODEX_DEFAULT_LABEL, ApiSource.CODEX.terminalId(), shown(codexHome.path), codexSourceEnabled(),
            ready = File(codexHome, "auth.json").isFile, bySource = true
        ).takeIf { row -> row.monitored || row.ready == true }
        val codexExtras = CodexProfileRegistry.readStored(preferences).map { record ->
            TerminalAccountRow(ApiSource.CODEX, record.label, shortId(record.id), shown(record.directory), record.enabled, File(record.directory, "auth.json").isFile)
        }
        return anthropic + listOfNotNull(codexDefault) + codexExtras
    }

    /** O começo do id que basta para achar a conta (o coletor aceita o id inteiro ou este começo). */
    private fun shortId(id: String): String = if (id.length > SHORT_ID) id.take(SHORT_ID) else id

    private fun shown(path: String): String {
        val homePath = home.path.trimEnd(File.separatorChar)
        return if (homePath.isNotEmpty() && (path == homePath || path.startsWith(homePath + File.separator))) "~" + path.removePrefix(homePath) else path
    }

    private companion object {
        const val SHORT_ID = 13
        const val CODEX_DEFAULT_LABEL = "padrão"
    }
}
