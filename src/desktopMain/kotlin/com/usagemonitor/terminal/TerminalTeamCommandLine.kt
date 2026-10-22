package com.usagemonitor.terminal

/** `team join|status|leave` (aba Time). A chave do time vem da entrada (D5). */
internal sealed interface TeamCommand : ConfigCommand {
    data class Join(
        val server: String? = null,
        val alias: String? = null,
        /** Apelidos ou ids das contas Anthropic que participam; `null`: as monitoradas. */
        val accounts: kotlin.collections.List<String>? = null,
        val skipCheck: Boolean = false
    ) : TeamCommand

    data object Status : TeamCommand

    data object Leave : TeamCommand
}

/** `web enable [--port]|disable|status|token --show|--regenerate` (acesso pela rede local, aba Rede). */
internal sealed interface WebCommand : ConfigCommand {
    data class Switch(val enabled: Boolean, val port: Int? = null) : WebCommand

    data object Status : WebCommand

    /** D5: o endereço com o token só sai a pedido explícito, como o "copiar" das Configurações. */
    data object ShowToken : WebCommand

    data object RegenerateToken : WebCommand
}

internal object TeamCommandNames {
    const val TEAM = "team"
    const val JOIN = "join"
    const val STATUS = "status"
    const val LEAVE = "leave"
    const val SERVER = "--server"
    const val ALIAS = "--alias"
    const val ACCOUNTS = "--accounts"
    const val SKIP_CHECK = "--skip-check"

    const val WEB = "web"
    const val ENABLE = "enable"
    const val DISABLE = "disable"
    const val TOKEN = "token"
    const val PORT = "--port"
    const val SHOW = "--show"
    const val REGENERATE = "--regenerate"

    val TEAM_ACTIONS: List<String> = listOf(JOIN, STATUS, LEAVE)
    val WEB_ACTIONS: List<String> = listOf(ENABLE, DISABLE, STATUS, TOKEN)
}

internal fun parseTeamCommand(arguments: List<String>): TerminalCommandParse {
    val actions = TeamCommandNames.TEAM_ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${TeamCommandNames.TEAM}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = "${TeamCommandNames.TEAM} $action"
    return when (action) {
        TeamCommandNames.STATUS -> rest.firstOrNull()?.let { extra -> unknownOption(command, extra) } ?: TerminalCommandParse.Parsed(TeamCommand.Status)
        TeamCommandNames.LEAVE -> rest.firstOrNull()?.let { extra -> unknownOption(command, extra) } ?: TerminalCommandParse.Parsed(TeamCommand.Leave)
        TeamCommandNames.JOIN -> {
            var join = TeamCommand.Join()
            var index = 0
            while (index < rest.size) {
                val argument = rest[index]
                val name = argument.substringBefore('=')
                val inline = argument.substringAfter('=', missingDelimiterValue = "").takeIf { argument.contains('=') }
                fun value(): String? = inline ?: rest.getOrNull(index + 1)?.also { index += 1 }
                join = when (name) {
                    TeamCommandNames.SERVER -> join.copy(server = value() ?: return TerminalCommandParse.Invalid("Error: ${TeamCommandNames.SERVER} needs the server address."))
                    TeamCommandNames.ALIAS -> join.copy(alias = value() ?: return TerminalCommandParse.Invalid("Error: ${TeamCommandNames.ALIAS} needs a name."))
                    TeamCommandNames.ACCOUNTS -> join.copy(
                        accounts = value()?.split(',')?.map { account -> account.trim() }?.filter { account -> account.isNotEmpty() }
                            ?: return TerminalCommandParse.Invalid("Error: ${TeamCommandNames.ACCOUNTS} needs account labels or ids separated by commas.")
                    )
                    TeamCommandNames.SKIP_CHECK -> join.copy(skipCheck = true)
                    // A chave colada como argumento ficaria no histórico do shell.
                    else -> return if (argument.startsWith("-")) unknownOption(command, argument)
                        else TerminalCommandParse.Invalid("Error: the team key is read from the input, never from an argument. Run '$command' and paste it.")
                }
                index += 1
            }
            TerminalCommandParse.Parsed(join)
        }
        else -> TerminalCommandParse.Invalid("Error: '$action' is not a team action. Use $actions.")
    }
}

internal fun parseWebCommand(arguments: List<String>): TerminalCommandParse {
    val actions = TeamCommandNames.WEB_ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${TeamCommandNames.WEB}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = "${TeamCommandNames.WEB} $action"
    return when (action) {
        TeamCommandNames.ENABLE -> {
            val portText = when {
                rest.isEmpty() -> null
                rest.first() == TeamCommandNames.PORT && rest.size == 2 -> rest[1]
                rest.first().startsWith("${TeamCommandNames.PORT}=") && rest.size == 1 -> rest.first().substringAfter('=')
                else -> return unknownOption(command, rest.first())
            }
            val port = portText?.let { text -> text.toIntOrNull()?.takeIf { value -> value in 1_024..65_535 } ?: return TerminalCommandParse.Invalid("Error: ${TeamCommandNames.PORT} needs a port from 1024 to 65535.") }
            TerminalCommandParse.Parsed(WebCommand.Switch(true, port))
        }
        TeamCommandNames.DISABLE -> rest.firstOrNull()?.let { extra -> unknownOption(command, extra) } ?: TerminalCommandParse.Parsed(WebCommand.Switch(false))
        TeamCommandNames.STATUS -> rest.firstOrNull()?.let { extra -> unknownOption(command, extra) } ?: TerminalCommandParse.Parsed(WebCommand.Status)
        TeamCommandNames.TOKEN -> when (rest) {
            listOf(TeamCommandNames.SHOW) -> TerminalCommandParse.Parsed(WebCommand.ShowToken)
            listOf(TeamCommandNames.REGENERATE) -> TerminalCommandParse.Parsed(WebCommand.RegenerateToken)
            else -> TerminalCommandParse.Invalid("Error: '$command' needs ${TeamCommandNames.SHOW} (print the address with the token) or ${TeamCommandNames.REGENERATE}.")
        }
        else -> TerminalCommandParse.Invalid("Error: '$action' is not a web action. Use $actions.")
    }
}
