package com.usagemonitor.terminal

/** `usage-monitor setup` (#440, 4.1): o assistente da primeira vez. */
internal data object SetupCommand : TerminalCommand

/** `usage-monitor update`: confere a versão nova e diz como atualizar esta instalação. */
internal data object UpdateCommand : TerminalCommand

internal object SetupCommandNames {
    const val SETUP = "setup"
    const val UPDATE = "update"
}

internal fun parseSingleCommand(name: String, command: TerminalCommand, arguments: List<String>): TerminalCommandParse =
    arguments.firstOrNull()?.let { extra -> unknownOption(name, extra) } ?: TerminalCommandParse.Parsed(command)
