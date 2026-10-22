package com.usagemonitor.terminal

/** `telegram setup|enable|disable|pair|test|status|remove|readonly` (seção do bot, aba Alertas). */
internal sealed interface TelegramCommand : ConfigCommand {
    data object Setup : TelegramCommand

    data class Switch(val enabled: Boolean) : TelegramCommand

    data class Pair(val timeout: kotlin.time.Duration? = null) : TelegramCommand

    data object Test : TelegramCommand

    data object Status : TelegramCommand

    data class Remove(val chat: String) : TelegramCommand

    data class ReadOnly(val chat: String, val readOnly: Boolean) : TelegramCommand
}

internal object TelegramCommandNames {
    const val TELEGRAM = "telegram"
    const val SETUP = "setup"
    const val ENABLE = "enable"
    const val DISABLE = "disable"
    const val PAIR = "pair"
    const val TEST = "test"
    const val STATUS = "status"
    const val REMOVE = "remove"
    const val READ_ONLY = "readonly"

    val ACTIONS: kotlin.collections.List<String> = listOf(SETUP, ENABLE, DISABLE, PAIR, TEST, STATUS, REMOVE, READ_ONLY)
}

internal fun parseTelegramCommand(arguments: kotlin.collections.List<String>): TerminalCommandParse {
    val actions = TelegramCommandNames.ACTIONS.joinToString(", ")
    val action = arguments.firstOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${TelegramCommandNames.TELEGRAM}' needs an action: $actions.")
    val rest = arguments.drop(1)
    val command = "${TelegramCommandNames.TELEGRAM} $action"
    fun only(parsed: TelegramCommand, from: Int = 0): TerminalCommandParse =
        rest.getOrNull(from)?.let { extra -> unknownOption(command, extra) } ?: TerminalCommandParse.Parsed(parsed)
    val needsChat = TerminalCommandParse.Invalid("Error: '$command' needs a chat: its name or id. Run 'usage-monitor telegram status' to see them.")
    return when (action) {
        // O token vem da entrada; colado como argumento ficaria no histórico do shell.
        TelegramCommandNames.SETUP -> if (rest.isEmpty()) TerminalCommandParse.Parsed(TelegramCommand.Setup)
            else TerminalCommandParse.Invalid("Error: the bot token is read from the input, never from an argument. Run '$command' and paste it.")
        TelegramCommandNames.ENABLE, TelegramCommandNames.DISABLE -> only(TelegramCommand.Switch(action == TelegramCommandNames.ENABLE))
        TelegramCommandNames.TEST -> only(TelegramCommand.Test)
        TelegramCommandNames.STATUS -> only(TelegramCommand.Status)
        TelegramCommandNames.PAIR -> {
            var timeout: kotlin.time.Duration? = null
            var index = 0
            while (index < rest.size) {
                val argument = rest[index]
                when {
                    argument == TerminalCommandNames.TIMEOUT -> timeout = rest.getOrNull(index + 1)?.let(::parseTerminalDuration)?.also { index += 1 }
                        ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.TIMEOUT} needs a time such as 90s, 10m or 2h.")
                    argument.startsWith("${TerminalCommandNames.TIMEOUT}=") -> timeout = parseTerminalDuration(argument.substringAfter('='))
                        ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.TIMEOUT} needs a time such as 90s, 10m or 2h.")
                    else -> return unknownOption(command, argument)
                }
                index += 1
            }
            TerminalCommandParse.Parsed(TelegramCommand.Pair(timeout))
        }
        TelegramCommandNames.REMOVE -> only(TelegramCommand.Remove(rest.firstOrNull() ?: return needsChat), from = 1)
        TelegramCommandNames.READ_ONLY -> {
            val chat = rest.firstOrNull() ?: return needsChat
            val readOnly = when (rest.getOrNull(1)) {
                "on" -> true
                "off" -> false
                else -> return TerminalCommandParse.Invalid("Error: '$command $chat' needs on or off.")
            }
            only(TelegramCommand.ReadOnly(chat, readOnly), from = 2)
        }
        else -> TerminalCommandParse.Invalid("Error: '$action' is not a telegram action. Use $actions.")
    }
}
