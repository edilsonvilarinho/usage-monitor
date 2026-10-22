package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource

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
