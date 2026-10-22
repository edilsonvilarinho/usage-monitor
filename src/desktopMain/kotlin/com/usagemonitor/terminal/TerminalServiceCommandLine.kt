package com.usagemonitor.terminal

/**
 * `usage-monitor service …` (#440): o motor de coleta sem janela, para um servidor sem
 * tela, e o serviço do usuário no systemd que o mantém de pé (só no Linux, D3).
 */
internal sealed interface ServiceCommand : TerminalCommand {
    /** `service run`: o motor em primeiro plano, até o `SIGTERM`. É o que o systemd chama. */
    data object Run : ServiceCommand

    /** `service install`: cria a unit, liga e inicia; avisa do `loginctl enable-linger`. */
    data object Install : ServiceCommand

    data object Uninstall : ServiceCommand

    /** `service start|stop|restart`: o `systemctl --user` da unit. */
    data class Control(val action: String) : ServiceCommand

    /** `service status`: a saúde de cada parte (5.1). */
    data object Status : ServiceCommand

    /** `service logs [--follow] [--lines <n>]`: o journal do serviço. */
    data class Logs(val follow: Boolean = false, val lines: Int = DEFAULT_LOG_LINES) : ServiceCommand

    companion object {
        const val DEFAULT_LOG_LINES = 50
    }
}

internal object ServiceCommandNames {
    const val RUN = "run"
    const val INSTALL = "install"
    const val UNINSTALL = "uninstall"
    const val START = "start"
    const val STOP = "stop"
    const val RESTART = "restart"
    const val STATUS = "status"
    const val LOGS = "logs"
    const val FOLLOW = "--follow"
    const val LINES = "--lines"

    val ALL: List<String> = listOf(INSTALL, UNINSTALL, START, STOP, RESTART, STATUS, LOGS, RUN)
}

internal fun parseServiceCommand(arguments: List<String>): TerminalCommandParse {
    val actions = ServiceCommandNames.ALL.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${TerminalCommandNames.SERVICE}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = "${TerminalCommandNames.SERVICE} $action"
    if (action == ServiceCommandNames.LOGS) {
        var logs = ServiceCommand.Logs()
        var index = 0
        while (index < rest.size) {
            val argument = rest[index]
            logs = when {
                argument == ServiceCommandNames.FOLLOW || argument == "-f" -> logs.copy(follow = true)
                argument == ServiceCommandNames.LINES -> logs.copy(
                    lines = rest.getOrNull(index + 1)?.toIntOrNull()?.takeIf { lines -> lines in 1..10_000 }?.also { index += 1 }
                        ?: return TerminalCommandParse.Invalid("Error: ${ServiceCommandNames.LINES} needs a number from 1 to 10000.")
                )
                else -> return unknownOption(command, argument)
            }
            index += 1
        }
        return TerminalCommandParse.Parsed(logs)
    }
    val parsed = when (action) {
        ServiceCommandNames.RUN -> ServiceCommand.Run
        ServiceCommandNames.INSTALL -> ServiceCommand.Install
        ServiceCommandNames.UNINSTALL -> ServiceCommand.Uninstall
        ServiceCommandNames.START, ServiceCommandNames.STOP, ServiceCommandNames.RESTART -> ServiceCommand.Control(action)
        ServiceCommandNames.STATUS -> ServiceCommand.Status
        else -> return TerminalCommandParse.Invalid("Error: '$action' is not a service action. Use $actions.")
    }
    rest.firstOrNull()?.let { extra -> return unknownOption(command, extra) }
    return TerminalCommandParse.Parsed(parsed)
}
