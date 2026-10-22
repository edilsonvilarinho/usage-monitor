package com.usagemonitor.terminal

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Um comando do modo terminal (#439), já validado. Os comandos respondem e saem; abrir o
 * app continua sendo `usage-monitor` sem comando.
 */
internal sealed interface TerminalCommand {
    /** `status [--max-age <tempo>]`: as cotas de todas as contas. */
    data class Status(val maxAge: Duration? = null) : TerminalCommand
}

internal object TerminalCommandNames {
    const val STATUS = "status"
    const val MAX_AGE = "--max-age"

    /** Os nomes que fazem de um argumento solto um comando. */
    val ALL: Set<String> = setOf(STATUS)
}

internal sealed interface TerminalCommandParse {
    data class Parsed(val command: TerminalCommand) : TerminalCommandParse
    data class Invalid(val message: String) : TerminalCommandParse
}

/**
 * Os argumentos de um comando, depois que `parseCommandLine` tirou as opções globais
 * (`--locale`, `--user-data-dir`). Função pura. Ao contrário da opção desconhecida sem
 * comando, que só avisa, aqui o pedido é explícito: opção desconhecida é erro.
 */
internal fun parseTerminalCommand(name: String, arguments: List<String>): TerminalCommandParse =
    when (name) {
        TerminalCommandNames.STATUS -> parseStatus(arguments)
        else -> TerminalCommandParse.Invalid("Error: '$name' is not a command. Run with --help to list the commands.")
    }

private fun parseStatus(arguments: List<String>): TerminalCommandParse {
    var maxAge: Duration? = null
    var index = 0
    while (index < arguments.size) {
        val argument = arguments[index]
        when {
            argument == TerminalCommandNames.MAX_AGE || argument.startsWith("${TerminalCommandNames.MAX_AGE}=") -> {
                val inline = argument.substringAfter('=', missingDelimiterValue = "").takeIf { argument.contains('=') }
                val value = inline ?: arguments.getOrNull(index + 1)?.also { index += 1 }
                maxAge = value?.let(::parseTerminalDuration)
                    ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.MAX_AGE} needs a time such as 90s, 10m or 2h.")
            }
            else -> return unknownOption(TerminalCommandNames.STATUS, argument)
        }
        index += 1
    }
    return TerminalCommandParse.Parsed(TerminalCommand.Status(maxAge))
}

private fun unknownOption(command: String, argument: String): TerminalCommandParse =
    TerminalCommandParse.Invalid("Error: '$argument' is not an option of '$command'. Run with --help to list the options.")

/**
 * "90s", "10m", "2h" ou só o número, em segundos. Zero ou negativo não é um tempo: `null`.
 */
internal fun parseTerminalDuration(text: String): Duration? {
    val trimmed = text.trim().lowercase()
    val unit = trimmed.lastOrNull()?.takeIf { char -> char == 's' || char == 'm' || char == 'h' }
    val amount = (if (unit != null) trimmed.dropLast(1) else trimmed).toLongOrNull() ?: return null
    if (amount <= 0L) {
        return null
    }
    return when (unit) {
        'h' -> amount.hours
        'm' -> amount.minutes
        else -> amount.seconds
    }
}
