package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.requiresLocalApiKey

/**
 * Os comandos de configurar pelo terminal (#440). Cada família (`sources`, `config`,
 * `keys`…) declara os seus num arquivo próprio, todos sob esta interface: o despacho é
 * um `when` só, e a família nova não faz o parser crescer sem fim.
 */
internal sealed interface ConfigCommand : TerminalCommand

/** `sources list|enable|disable`: as fontes monitoradas (aba APIs das Configurações). */
internal sealed interface SourcesCommand : ConfigCommand {
    data object List : SourcesCommand

    data class Switch(val source: ApiSource, val enabled: Boolean) : SourcesCommand
}

internal object SourcesCommandNames {
    const val SOURCES = "sources"
    const val LIST = "list"
    const val ENABLE = "enable"
    const val DISABLE = "disable"

    val ACTIONS: kotlin.collections.List<String> = listOf(LIST, ENABLE, DISABLE)
}

/** O id de uma fonte no terminal: o nome do enum em minúsculas, como no `status --json`. */
internal fun ApiSource.terminalId(): String = name.lowercase()

internal fun apiSourceOfTerminalId(id: String): ApiSource? =
    ApiSource.entries.firstOrNull { source -> source.terminalId() == id.trim().lowercase() }

internal fun parseSourcesCommand(arguments: kotlin.collections.List<String>): TerminalCommandParse {
    val actions = SourcesCommandNames.ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${SourcesCommandNames.SOURCES}' needs an action: $actions.")
    return when (action) {
        SourcesCommandNames.LIST -> arguments.getOrNull(1)?.let { extra -> unknownOption("${SourcesCommandNames.SOURCES} $action", extra) }
            ?: TerminalCommandParse.Parsed(SourcesCommand.List)
        SourcesCommandNames.ENABLE, SourcesCommandNames.DISABLE -> {
            val ids = ApiSource.entries.joinToString(", ") { source -> source.terminalId() }
            val id = arguments.getOrNull(1)
                ?: return TerminalCommandParse.Invalid("Error: '${SourcesCommandNames.SOURCES} $action' needs a source: $ids.")
            arguments.getOrNull(2)?.let { extra -> return unknownOption("${SourcesCommandNames.SOURCES} $action", extra) }
            val source = apiSourceOfTerminalId(id)
                ?: return TerminalCommandParse.Invalid("Error: '$id' is not a source. Use one of: $ids.")
            TerminalCommandParse.Parsed(SourcesCommand.Switch(source, enabled = action == SourcesCommandNames.ENABLE))
        }
        else -> TerminalCommandParse.Invalid("Error: '$action' is not a sources action. Use $actions.")
    }
}

/** `config list [seção] | get <ajuste> | set <ajuste> [valor]` (4.3 do protótipo). */
internal sealed interface SettingCommand : ConfigCommand {
    data class List(val prefix: String? = null) : SettingCommand

    data class Get(val key: String) : SettingCommand

    /** [value] nulo: ajuste secreto, lido da entrada (D5). */
    data class Set(val key: String, val value: String?) : SettingCommand
}

internal object SettingCommandNames {
    const val CONFIG = "config"
    const val LIST = "list"
    const val GET = "get"
    const val SET = "set"

    val ACTIONS: kotlin.collections.List<String> = listOf(LIST, GET, SET)
}

internal fun parseSettingCommand(arguments: kotlin.collections.List<String>): TerminalCommandParse {
    val actions = SettingCommandNames.ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${SettingCommandNames.CONFIG}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = "${SettingCommandNames.CONFIG} $action"
    return when (action) {
        SettingCommandNames.LIST -> rest.getOrNull(1)?.let { extra -> unknownOption(command, extra) }
            ?: TerminalCommandParse.Parsed(SettingCommand.List(rest.firstOrNull()?.trim()?.lowercase()?.removeSuffix(".")))
        SettingCommandNames.GET -> {
            val key = settingKeyOf(rest.firstOrNull(), command) ?: return invalidSettingKey(rest.firstOrNull(), command)
            rest.getOrNull(1)?.let { extra -> return unknownOption(command, extra) }
            TerminalCommandParse.Parsed(SettingCommand.Get(key.name))
        }
        SettingCommandNames.SET -> {
            val key = settingKeyOf(rest.firstOrNull(), command) ?: return invalidSettingKey(rest.firstOrNull(), command)
            val value = rest.getOrNull(1)
            rest.getOrNull(2)?.let { extra -> return unknownOption(command, extra) }
            when {
                // O segredo nunca vem por argumento: ficaria no histórico do shell e na lista de processos.
                key.secret && value != null -> TerminalCommandParse.Invalid("Error: '${key.name}' is a secret and is read from the input, never from an argument. Run 'usage-monitor config set ${key.name}' and type it.")
                !key.secret && value == null -> TerminalCommandParse.Invalid("Error: '$command ${key.name}' needs a value.")
                else -> TerminalCommandParse.Parsed(SettingCommand.Set(key.name, value))
            }
        }
        else -> TerminalCommandParse.Invalid("Error: '$action' is not a config action. Use $actions.")
    }
}

private fun settingKeyOf(name: String?, command: String): com.usagemonitor.ConfigKey? =
    name?.let(com.usagemonitor.ConfigKeys::named)

private fun invalidSettingKey(name: String?, command: String): TerminalCommandParse =
    if (name == null) {
        TerminalCommandParse.Invalid("Error: '$command' needs a setting. Run 'usage-monitor config list' to see them.")
    } else {
        TerminalCommandParse.Invalid("Error: '$name' is not a setting. Run 'usage-monitor config list' to see them.")
    }

/** `keys set <fonte> [--skip-test] | test <fonte> | remove <fonte>` (D5: a chave vem da entrada). */
internal sealed interface KeysCommand : ConfigCommand {
    val source: ApiSource

    data class Set(override val source: ApiSource, val skipTest: Boolean = false) : KeysCommand

    data class Test(override val source: ApiSource) : KeysCommand

    data class Remove(override val source: ApiSource) : KeysCommand
}

internal object KeysCommandNames {
    const val KEYS = "keys"
    const val SET = "set"
    const val TEST = "test"
    const val REMOVE = "remove"
    const val SKIP_TEST = "--skip-test"

    val ACTIONS: kotlin.collections.List<String> = listOf(SET, TEST, REMOVE)
}

internal fun parseKeysCommand(arguments: kotlin.collections.List<String>): TerminalCommandParse {
    val actions = KeysCommandNames.ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${KeysCommandNames.KEYS}' needs an action: $actions.")
    if (action !in KeysCommandNames.ACTIONS) {
        return TerminalCommandParse.Invalid("Error: '$action' is not a keys action. Use $actions.")
    }
    val command = "${KeysCommandNames.KEYS} $action"
    // Só as fontes que leem a chave de `api-keys.json` (o mesmo predicado do app).
    val ids = ApiSource.entries.filter { source -> source.requiresLocalApiKey() }.joinToString(", ") { source -> source.terminalId() }
    val id = arguments.getOrNull(1)
        ?: return TerminalCommandParse.Invalid("Error: '$command' needs a source: $ids.")
    val source = apiSourceOfTerminalId(id)?.takeIf { candidate -> candidate.requiresLocalApiKey() }
        ?: return TerminalCommandParse.Invalid("Error: '$id' has no API key. Use one of: $ids.")
    var skipTest = false
    for (extra in arguments.drop(2)) {
        when {
            action == KeysCommandNames.SET && extra == KeysCommandNames.SKIP_TEST -> skipTest = true
            // Uma chave colada como argumento ficaria no histórico do shell: recusar, e dizer por quê.
            !extra.startsWith("-") -> return TerminalCommandParse.Invalid("Error: the key is read from the input, never from an argument. Run '$command ${source.terminalId()}' and paste it.")
            else -> return unknownOption(command, extra)
        }
    }
    return TerminalCommandParse.Parsed(
        when (action) {
            KeysCommandNames.SET -> KeysCommand.Set(source, skipTest)
            KeysCommandNames.TEST -> KeysCommand.Test(source)
            else -> KeysCommand.Remove(source)
        }
    )
}

/** `accounts list|add|rename|enable|disable|remove|rescan` (aba Contas). */
internal sealed interface AccountsCommand : ConfigCommand {
    data object List : AccountsCommand

    data class Add(val source: ApiSource, val directory: String, val label: String? = null) : AccountsCommand

    data class Rename(val account: String, val label: String) : AccountsCommand

    data class Switch(val account: String, val enabled: Boolean) : AccountsCommand

    data class Remove(val account: String) : AccountsCommand

    data object Rescan : AccountsCommand
}

internal object AccountsCommandNames {
    const val ACCOUNTS = "accounts"
    const val LIST = "list"
    const val ADD = "add"
    const val RENAME = "rename"
    const val ENABLE = "enable"
    const val DISABLE = "disable"
    const val REMOVE = "remove"
    const val RESCAN = "rescan"
    const val LABEL = "--label"

    val ACTIONS: kotlin.collections.List<String> = listOf(LIST, ADD, RENAME, ENABLE, DISABLE, REMOVE, RESCAN)
}

internal fun parseAccountsCommand(arguments: kotlin.collections.List<String>): TerminalCommandParse {
    val actions = AccountsCommandNames.ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${AccountsCommandNames.ACCOUNTS}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = "${AccountsCommandNames.ACCOUNTS} $action"
    fun noExtra(from: Int, parsed: AccountsCommand): TerminalCommandParse =
        rest.getOrNull(from)?.let { extra -> unknownOption(command, extra) } ?: TerminalCommandParse.Parsed(parsed)
    fun account(): String? = rest.firstOrNull()?.takeIf { value -> !value.startsWith("-") }
    val needsAccount = TerminalCommandParse.Invalid("Error: '$command' needs an account: its label or id. Run 'usage-monitor accounts list' to see them.")
    return when (action) {
        AccountsCommandNames.LIST -> noExtra(0, AccountsCommand.List)
        AccountsCommandNames.RESCAN -> noExtra(0, AccountsCommand.Rescan)
        AccountsCommandNames.ADD -> parseAccountAdd(rest, command)
        AccountsCommandNames.RENAME -> {
            val target = account() ?: return needsAccount
            val label = rest.getOrNull(1)?.takeIf { value -> value.isNotBlank() }
                ?: return TerminalCommandParse.Invalid("Error: '$command' needs the new label.")
            noExtra(2, AccountsCommand.Rename(target, label))
        }
        AccountsCommandNames.ENABLE, AccountsCommandNames.DISABLE -> noExtra(1, AccountsCommand.Switch(account() ?: return needsAccount, action == AccountsCommandNames.ENABLE))
        AccountsCommandNames.REMOVE -> noExtra(1, AccountsCommand.Remove(account() ?: return needsAccount))
        else -> TerminalCommandParse.Invalid("Error: '$action' is not an accounts action. Use $actions.")
    }
}

private fun parseAccountAdd(arguments: kotlin.collections.List<String>, command: String): TerminalCommandParse {
    val sources = listOf(ApiSource.ANTHROPIC, ApiSource.CODEX)
    val names = sources.joinToString(" or ") { source -> source.terminalId() }
    val source = arguments.firstOrNull()?.let(::apiSourceOfTerminalId)?.takeIf { candidate -> candidate in sources }
        ?: return TerminalCommandParse.Invalid("Error: '$command' needs the source, $names, and the config directory.")
    var directory: String? = null
    var label: String? = null
    var index = 1
    while (index < arguments.size) {
        val argument = arguments[index]
        when {
            argument == AccountsCommandNames.LABEL -> {
                label = arguments.getOrNull(index + 1) ?: return TerminalCommandParse.Invalid("Error: ${AccountsCommandNames.LABEL} needs a label.")
                index += 1
            }
            argument.startsWith("${AccountsCommandNames.LABEL}=") -> label = argument.substringAfter('=')
            directory == null && !argument.startsWith("-") -> directory = argument
            else -> return unknownOption(command, argument)
        }
        index += 1
    }
    val path = directory
        ?: return TerminalCommandParse.Invalid("Error: '$command ${source.terminalId()}' needs the config directory (CLAUDE_CONFIG_DIR for Claude Code, CODEX_HOME for Codex).")
    return TerminalCommandParse.Parsed(AccountsCommand.Add(source, path, label))
}
