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
    /** `status [--json] [--max-age <tempo>]`: as cotas de todas as contas. */
    data class Status(val maxAge: Duration? = null, val json: Boolean = false) : TerminalCommand

    /** `check [--max <n>] [--max-age <tempo>] [--quiet]`: a decisão pelo código de saída. */
    data class Check(val max: Int? = null, val maxAge: Duration? = null, val quiet: Boolean = false) : TerminalCommand
}

internal object TerminalCommandNames {
    const val STATUS = "status"
    const val CHECK = "check"
    const val MAX_AGE = "--max-age"
    const val JSON = "--json"
    const val MAX = "--max"
    const val QUIET = "--quiet"

    /** Os nomes que fazem de um argumento solto um comando. */
    val ALL: Set<String> = setOf(STATUS, CHECK)
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
        TerminalCommandNames.CHECK -> parseCheck(arguments)
        else -> TerminalCommandParse.Invalid("Error: '$name' is not a command. Run with --help to list the commands.")
    }

private fun parseStatus(arguments: List<String>): TerminalCommandParse {
    var maxAge: Duration? = null
    var json = false
    val reader = OptionReader(arguments)
    while (reader.hasNext()) {
        val argument = reader.next()
        when {
            argument == TerminalCommandNames.JSON -> json = true
            reader.isOption(argument, TerminalCommandNames.MAX_AGE) -> maxAge = reader.value(argument)?.let(::parseTerminalDuration)
                ?: return invalidMaxAge()
            else -> return unknownOption(TerminalCommandNames.STATUS, argument)
        }
    }
    return TerminalCommandParse.Parsed(TerminalCommand.Status(maxAge, json))
}

private fun parseCheck(arguments: List<String>): TerminalCommandParse {
    var max: Int? = null
    var maxAge: Duration? = null
    var quiet = false
    val reader = OptionReader(arguments)
    while (reader.hasNext()) {
        val argument = reader.next()
        when {
            argument == TerminalCommandNames.QUIET -> quiet = true
            reader.isOption(argument, TerminalCommandNames.MAX_AGE) -> maxAge = reader.value(argument)?.let(::parseTerminalDuration)
                ?: return invalidMaxAge()
            reader.isOption(argument, TerminalCommandNames.MAX) -> max = reader.value(argument)?.toIntOrNull()?.takeIf { value -> value in 1..100 }
                ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.MAX} needs a percentage from 1 to 100.")
            else -> return unknownOption(TerminalCommandNames.CHECK, argument)
        }
    }
    return TerminalCommandParse.Parsed(TerminalCommand.Check(max, maxAge, quiet))
}

private fun invalidMaxAge(): TerminalCommandParse =
    TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.MAX_AGE} needs a time such as 90s, 10m or 2h.")

/** Percorre os argumentos de um comando, com opção de valor nas formas `--x v` e `--x=v`. */
private class OptionReader(private val arguments: List<String>) {
    private var index = 0

    fun hasNext(): Boolean = index < arguments.size

    fun next(): String = arguments[index++]

    fun isOption(argument: String, option: String): Boolean = argument == option || argument.startsWith("$option=")

    /** O valor da opção: o que vem depois do `=`, ou o próximo argumento, que é consumido. */
    fun value(argument: String): String? {
        if (argument.contains('=')) {
            return argument.substringAfter('=')
        }
        return arguments.getOrNull(index)?.also { index += 1 }
    }
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
