package com.usagemonitor.terminal

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Um comando do modo terminal (#439), já validado. Os comandos respondem e saem; abrir o
 * app continua sendo `usage-monitor` sem comando.
 */
internal sealed interface TerminalCommand {
    /** Os comandos que só leem a última coleta (#439). */
    sealed interface Reading : TerminalCommand

    /**
     * `status [--json] [--max-age <tempo>]`: as cotas de todas as contas. Com `--watch`
     * (#453), [watch] é o intervalo da releitura, e a tabela se redesenha até o Ctrl+C.
     */
    data class Status(val maxAge: Duration? = null, val json: Boolean = false, val watch: Duration? = null) : Reading

    /** `check [--max <n>] [--max-age <tempo>] [--quiet]`: a decisão pelo código de saída. */
    data class Check(val max: Int? = null, val maxAge: Duration? = null, val quiet: Boolean = false) : Reading

    /** `wait <id> --below <n> [--timeout <tempo>]`: espera a cota cair abaixo de n%. */
    data class Wait(val quotaId: String, val below: Int, val timeout: Duration? = null) : Reading

    /** `statusline claude-code`: a linha embaixo do prompt do Claude Code, lida do JSON da entrada. */
    data object ClaudeCodeStatusLine : Reading

    /** `statusline tmux`: o segmento da barra do tmux, no formato de estilo dele. */
    data object TmuxStatusLine : Reading

    /** `alerts [--since <tempo>]`: o registro do que foi avisado (#440, 5.2). Sem `--since`, hoje. */
    data class Alerts(val since: Duration? = null) : Reading

    /**
     * `analysis [conta] [--json]` (#463, AI3): a corrida esgotar × reiniciar das contas com análise.
     * [filter] é a fonte (`claude`, `codex`) ou o apelido da conta; sem ele, todas.
     */
    data class Analysis(val filter: String? = null, val json: Boolean = false) : Reading
}

internal object TerminalCommandNames {
    const val STATUS = "status"
    const val CHECK = "check"
    const val MAX_AGE = "--max-age"
    const val JSON = "--json"
    const val MAX = "--max"
    const val QUIET = "--quiet"
    const val WAIT = "wait"
    const val BELOW = "--below"
    const val TIMEOUT = "--timeout"
    const val STATUS_LINE = "statusline"
    const val CLAUDE_CODE = "claude-code"
    const val TMUX = "tmux"
    const val SERVICE = "service"
    const val ALERTS = "alerts"
    const val SINCE = "--since"
    const val TODAY = "--today"
    const val WATCH = "--watch"
    const val INTERVAL = "--interval"
    const val ANALYSIS = "analysis"

    /** Os nomes que fazem de um argumento solto um comando. */
    val ALL: Set<String> = setOf(STATUS, CHECK, WAIT, STATUS_LINE, SERVICE, ALERTS, ANALYSIS, SourcesCommandNames.SOURCES, SettingCommandNames.CONFIG, KeysCommandNames.KEYS, AccountsCommandNames.ACCOUNTS, TelegramCommandNames.TELEGRAM, TeamCommandNames.TEAM, TeamCommandNames.WEB, SetupCommandNames.SETUP, SetupCommandNames.UPDATE, TuiCommandNames.TUI)

    /** Os alvos da linha de status. */
    val STATUS_LINE_TARGETS: List<String> = listOf(CLAUDE_CODE, TMUX)
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
        TerminalCommandNames.WAIT -> parseWait(arguments)
        TerminalCommandNames.STATUS_LINE -> parseStatusLine(arguments)
        TerminalCommandNames.SERVICE -> parseServiceCommand(arguments)
        TerminalCommandNames.ALERTS -> parseAlerts(arguments)
        TerminalCommandNames.ANALYSIS -> parseAnalysis(arguments)
        SourcesCommandNames.SOURCES -> parseSourcesCommand(arguments)
        SettingCommandNames.CONFIG -> parseSettingCommand(arguments)
        KeysCommandNames.KEYS -> parseKeysCommand(arguments)
        AccountsCommandNames.ACCOUNTS -> parseAccountsCommand(arguments)
        TelegramCommandNames.TELEGRAM -> parseTelegramCommand(arguments)
        TeamCommandNames.TEAM -> parseTeamCommand(arguments)
        TeamCommandNames.WEB -> parseWebCommand(arguments)
        SetupCommandNames.SETUP -> parseSingleCommand(name, SetupCommand, arguments)
        SetupCommandNames.UPDATE -> parseSingleCommand(name, UpdateCommand, arguments)
        TuiCommandNames.TUI -> parseSingleCommand(name, TuiCommand, arguments)
        else -> TerminalCommandParse.Invalid("Error: '$name' is not a command. Run with --help to list the commands.")
    }

private fun parseStatus(arguments: List<String>): TerminalCommandParse {
    var maxAge: Duration? = null
    var json = false
    var watch = false
    var interval: Duration? = null
    val reader = OptionReader(arguments)
    while (reader.hasNext()) {
        val argument = reader.next()
        when {
            argument == TerminalCommandNames.JSON -> json = true
            argument == TerminalCommandNames.WATCH -> watch = true
            reader.isOption(argument, TerminalCommandNames.MAX_AGE) -> maxAge = reader.value(argument)?.let(::parseTerminalDuration)
                ?: return invalidMaxAge()
            reader.isOption(argument, TerminalCommandNames.INTERVAL) -> interval = reader.value(argument)?.let(::parseTerminalDuration)?.takeIf { value -> value >= MIN_WATCH_INTERVAL }
                ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.INTERVAL} needs a time of at least ${MIN_WATCH_INTERVAL.inWholeSeconds}s, such as 15s or 1m.")
            else -> return unknownOption(TerminalCommandNames.STATUS, argument)
        }
    }
    if (!watch) {
        if (interval != null) {
            return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.INTERVAL} only goes with ${TerminalCommandNames.WATCH}.")
        }
        return TerminalCommandParse.Parsed(TerminalCommand.Status(maxAge, json))
    }
    // `--json` e `--max-age` são contrato de quem roda uma vez e lê a saída ou o código (D3 da #453).
    val conflicting = listOfNotNull(TerminalCommandNames.JSON.takeIf { json }, TerminalCommandNames.MAX_AGE.takeIf { maxAge != null })
    if (conflicting.isNotEmpty()) {
        return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.WATCH} does not go with ${conflicting.joinToString(" or ")}.")
    }
    return TerminalCommandParse.Parsed(TerminalCommand.Status(watch = interval ?: DEFAULT_WATCH_INTERVAL))
}

/** O do `wait` e da TUI: a coleta é de 60 s com sessão ativa. */
internal val DEFAULT_WATCH_INTERVAL: Duration = 15.seconds

/** Reler mais rápido que isso só avança a idade da leitura: o cache muda a cada coleta (D2 da #453). */
internal val MIN_WATCH_INTERVAL: Duration = 5.seconds

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

private fun parseWait(arguments: List<String>): TerminalCommandParse {
    var quotaId: String? = null
    var below: Int? = null
    var timeout: Duration? = null
    val reader = OptionReader(arguments)
    while (reader.hasNext()) {
        val argument = reader.next()
        when {
            reader.isOption(argument, TerminalCommandNames.BELOW) -> below = reader.value(argument)?.toIntOrNull()?.takeIf { value -> value in 1..100 }
                ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.BELOW} needs a percentage from 1 to 100.")
            reader.isOption(argument, TerminalCommandNames.TIMEOUT) -> timeout = reader.value(argument)?.let(::parseTerminalDuration)
                ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.TIMEOUT} needs a time such as 90s, 10m or 2h.")
            quotaId == null && !argument.startsWith("-") -> quotaId = argument
            else -> return unknownOption(TerminalCommandNames.WAIT, argument)
        }
    }
    val id = quotaId ?: return TerminalCommandParse.Invalid("Error: '${TerminalCommandNames.WAIT}' needs a quota id. Run 'usage-monitor status --json' to list them.")
    val percent = below ?: return TerminalCommandParse.Invalid("Error: '${TerminalCommandNames.WAIT}' needs ${TerminalCommandNames.BELOW} <percentage>.")
    return TerminalCommandParse.Parsed(TerminalCommand.Wait(id, percent, timeout))
}

private fun parseStatusLine(arguments: List<String>): TerminalCommandParse {
    val targets = TerminalCommandNames.STATUS_LINE_TARGETS.joinToString(" or ")
    val target = arguments.singleOrNull()
        ?: return TerminalCommandParse.Invalid("Error: '${TerminalCommandNames.STATUS_LINE}' needs one target: $targets.")
    return when (target) {
        TerminalCommandNames.CLAUDE_CODE -> TerminalCommandParse.Parsed(TerminalCommand.ClaudeCodeStatusLine)
        TerminalCommandNames.TMUX -> TerminalCommandParse.Parsed(TerminalCommand.TmuxStatusLine)
        else -> TerminalCommandParse.Invalid("Error: '$target' is not a status line target. Use $targets.")
    }
}

private fun parseAlerts(arguments: List<String>): TerminalCommandParse {
    var since: Duration? = null
    val reader = OptionReader(arguments)
    while (reader.hasNext()) {
        val argument = reader.next()
        when {
            argument == TerminalCommandNames.TODAY -> since = null
            reader.isOption(argument, TerminalCommandNames.SINCE) -> since = reader.value(argument)?.let(::parseTerminalDuration)
                ?: return TerminalCommandParse.Invalid("Error: ${TerminalCommandNames.SINCE} needs a time such as 12h or 7d.")
            else -> return unknownOption(TerminalCommandNames.ALERTS, argument)
        }
    }
    return TerminalCommandParse.Parsed(TerminalCommand.Alerts(since))
}

private fun parseAnalysis(arguments: List<String>): TerminalCommandParse {
    var filter: String? = null
    var json = false
    for (argument in arguments) {
        when {
            argument == TerminalCommandNames.JSON -> json = true
            !argument.startsWith("-") && filter == null -> filter = argument
            else -> return unknownOption(TerminalCommandNames.ANALYSIS, argument)
        }
    }
    return TerminalCommandParse.Parsed(TerminalCommand.Analysis(filter, json))
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

internal fun unknownOption(command: String, argument: String): TerminalCommandParse =
    TerminalCommandParse.Invalid("Error: '$argument' is not an option of '$command'. Run with --help to list the options.")

/**
 * "90s", "10m", "2h", "7d" ou só o número, em segundos. Zero ou negativo não é um tempo: `null`.
 */
internal fun parseTerminalDuration(text: String): Duration? {
    val trimmed = text.trim().lowercase()
    val unit = trimmed.lastOrNull()?.takeIf { char -> char == 's' || char == 'm' || char == 'h' || char == 'd' }
    val amount = (if (unit != null) trimmed.dropLast(1) else trimmed).toLongOrNull() ?: return null
    if (amount <= 0L) {
        return null
    }
    return when (unit) {
        'd' -> amount.days
        'h' -> amount.hours
        'm' -> amount.minutes
        else -> amount.seconds
    }
}
