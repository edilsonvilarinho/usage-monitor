package com.usagemonitor.terminal

/**
 * `usage-monitor service …` (#440): o motor de coleta sem janela, para um servidor sem
 * tela. A configuração do serviço do sistema (`install`, `start`…) vem na A11.
 */
internal sealed interface ServiceCommand : TerminalCommand {
    /** `service run`: o motor em primeiro plano, até o `SIGTERM`. É o que o systemd chama. */
    data object Run : ServiceCommand
}

internal object ServiceCommandNames {
    const val RUN = "run"

    val ALL: List<String> = listOf(RUN)
}

internal fun parseServiceCommand(arguments: List<String>): TerminalCommandParse {
    val actions = ServiceCommandNames.ALL.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${TerminalCommandNames.SERVICE}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = when (action) {
        ServiceCommandNames.RUN -> ServiceCommand.Run
        else -> return TerminalCommandParse.Invalid("Error: '$action' is not a service action. Use $actions.")
    }
    rest.firstOrNull()?.let { extra -> return unknownOption("${TerminalCommandNames.SERVICE} $action", extra) }
    return TerminalCommandParse.Parsed(command)
}
