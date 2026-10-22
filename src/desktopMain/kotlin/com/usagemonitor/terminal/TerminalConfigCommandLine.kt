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
