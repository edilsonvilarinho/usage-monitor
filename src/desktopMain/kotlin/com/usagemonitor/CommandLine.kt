package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.terminal.TerminalCommand
import com.usagemonitor.terminal.TerminalCommandNames
import com.usagemonitor.terminal.TerminalCommandParse
import com.usagemonitor.terminal.parseTerminalCommand

/**
 * O que a linha de comando pede ao processo (issue #418).
 *
 * Um dono só para a leitura de `args`: antes cada opção tinha o próprio
 * `args.any { … }`, e argumento desconhecido sumia sem aviso — `--diagnostic`
 * digitado errado abria o app como se nada tivesse sido pedido.
 *
 * Função pura: o `main()` decide o que fazer com o pedido, e o teste não
 * precisa de processo nem de console.
 */
internal sealed interface CommandLineRequest {
    /** Imprime [commandLineHelp] e sai. Vence qualquer outra opção, como no `code --help`. */
    data object Help : CommandLineRequest

    /** Imprime [commandLineVersion] e sai. */
    data object Version : CommandLineRequest

    /** Empacota o diagnóstico e sai, sem janela (#416), da raiz de [userDataDir] quando dada. */
    data class Diagnostics(val userDataDir: String? = null) : CommandLineRequest

    /**
     * Abre o app (ou foca a instância viva).
     *
     * [unknownOptions] não impede a abertura: o atalho antigo, a chave `Run` e
     * o launcher de outra plataforma podem passar algo que esta versão não
     * conhece, e recusar ali seria um app que não abre. O aviso vai para o
     * `stderr` e para o `app.log`.
     */
    data class Launch(
        val autostart: Boolean,
        val unknownOptions: List<String>,
        /** `--locale`: idioma só desta execução, nunca gravado; `null` = a preferência. */
        val locale: AppLanguage? = null,
        /** `--user-data-dir` como digitado (#419); o `main()` resolve e valida no disco. */
        val userDataDir: String? = null
    ) : CommandLineRequest

    /**
     * Um comando do modo terminal (#439): responde e sai, sem janela, antes da instância
     * única. [locale] e [userDataDir] valem como na abertura do app.
     */
    data class Terminal(
        val command: TerminalCommand,
        val locale: AppLanguage? = null,
        val userDataDir: String? = null
    ) : CommandLineRequest

    /**
     * Pedido explícito com valor inválido (`--locale xx`): sai com a mensagem e
     * o código 2. Ao contrário da opção desconhecida, aqui abrir o app num
     * idioma que não foi pedido seria ignorar o que o usuário digitou.
     */
    data class Invalid(val message: String) : CommandLineRequest
}

internal object CommandLineOptions {
    const val HELP = "--help"
    const val HELP_SHORT = "-h"
    const val VERSION = "--version"
    const val VERSION_SHORT = "-v"
    const val LOCALE = "--locale"
    const val USER_DATA_DIR = "--user-data-dir"
}

internal fun parseCommandLine(arguments: Array<String>): CommandLineRequest {
    var help = false
    var version = false
    var diagnostics = false
    var autostart = false
    var locale: AppLanguage? = null
    var userDataDir: String? = null
    var invalid: String? = null
    val unknown = mutableListOf<String>()
    var terminalCommand: String? = null
    val terminalArguments = mutableListOf<String>()
    val tokens = commandLineTokens(arguments)
    var index = 0
    while (index < tokens.size) {
        val argument = tokens[index]
        val next = tokens.getOrNull(index + 1)
        val localeValue = optionValue(argument, CommandLineOptions.LOCALE, next)
        val dataDirValue = optionValue(argument, CommandLineOptions.USER_DATA_DIR, next)
        when {
            argument == CommandLineOptions.HELP || argument == CommandLineOptions.HELP_SHORT -> help = true
            argument == CommandLineOptions.VERSION || argument == CommandLineOptions.VERSION_SHORT -> version = true
            argument == DiagnosticsExport.ARGUMENT -> diagnostics = true
            argument == StartupOrigin.AUTO_START_ARGUMENT -> autostart = true
            localeValue != null -> {
                val parsed = localeValue.value?.let(::localeOf)
                if (parsed == null) {
                    invalid = invalid ?: localeError(localeValue.value)
                } else {
                    locale = parsed
                }
            }
            dataDirValue != null -> {
                if (dataDirValue.value.isNullOrEmpty()) {
                    invalid = invalid ?: "Error: ${CommandLineOptions.USER_DATA_DIR} needs a folder."
                } else {
                    userDataDir = dataDirValue.value
                }
            }
            // O primeiro argumento solto que é nome de comando abre o modo terminal; dali
            // em diante, o que não é opção global é argumento do comando.
            terminalCommand == null && argument in TerminalCommandNames.ALL -> terminalCommand = argument
            terminalCommand != null -> terminalArguments += argument
            else -> unknown += argument
        }
        if (localeValue?.consumedNext == true || dataDirValue?.consumedNext == true) {
            index += 1
        }
        index += 1
    }
    // Só leitura vence ação: `--help --diagnostics` não grava zip nenhum.
    if (help) {
        return CommandLineRequest.Help
    }
    if (version) {
        return CommandLineRequest.Version
    }
    invalid?.let { message -> return CommandLineRequest.Invalid(message) }
    terminalCommand?.let { name ->
        return terminalRequest(name, terminalArguments, unknown, diagnostics, locale, userDataDir)
    }
    if (diagnostics) {
        return CommandLineRequest.Diagnostics(userDataDir = userDataDir)
    }
    return CommandLineRequest.Launch(
        autostart = autostart,
        unknownOptions = unknown,
        locale = locale,
        userDataDir = userDataDir
    )
}

/**
 * O pedido de um comando do modo terminal. Com comando, tudo é explícito: opção
 * desconhecida antes dele e `--diagnostics` junto viram erro, em vez do aviso que a
 * abertura do app dá.
 */
private fun terminalRequest(
    name: String,
    arguments: List<String>,
    unknown: List<String>,
    diagnostics: Boolean,
    locale: AppLanguage?,
    userDataDir: String?
): CommandLineRequest {
    unknown.firstOrNull()?.let { option ->
        return CommandLineRequest.Invalid("Error: '$option' is not a known option. Run with --help to list the options.")
    }
    if (diagnostics) {
        return CommandLineRequest.Invalid("Error: ${DiagnosticsExport.ARGUMENT} cannot be combined with '$name'.")
    }
    return when (val parsed = parseTerminalCommand(name, arguments)) {
        is TerminalCommandParse.Parsed -> CommandLineRequest.Terminal(parsed.command, locale, userDataDir)
        is TerminalCommandParse.Invalid -> CommandLineRequest.Invalid(parsed.message)
    }
}

/** `trim` como o `StartupOrigin.from` sempre fez (o teste dele cobre " --autostart "). */
private fun commandLineTokens(arguments: Array<String>): List<String> =
    arguments.map { raw -> raw.trim() }.filter { argument -> argument.isNotEmpty() }

private class OptionValue(val value: String?, val consumedNext: Boolean)

/**
 * Valor de uma opção nas duas formas: `--opcao valor`, a documentada, e
 * `--opcao=valor`. O próximo argumento só é consumido se não for outra opção.
 * `null` quando [argument] não é [option].
 */
private fun optionValue(argument: String, option: String, next: String?): OptionValue? {
    if (argument == option) {
        val value = next?.takeUnless { candidate -> candidate.startsWith("-") }
        return OptionValue(value, consumedNext = value != null)
    }
    if (argument.startsWith("$option=")) {
        return OptionValue(argument.substringAfter('='), consumedNext = false)
    }
    return null
}

/**
 * Os argumentos com `--user-data-dir` trocado por [absoluteRoot] — ou removido,
 * com `null` (raiz padrão).
 *
 * Existe para o relançamento do `usage-monitor.exe`: o exe GUI sobe com outro
 * diretório de trabalho, e um caminho relativo apontaria para outra pasta.
 */
internal fun withUserDataDir(arguments: Array<String>, absoluteRoot: String?): List<String> {
    val tokens = commandLineTokens(arguments)
    val kept = mutableListOf<String>()
    var index = 0
    while (index < tokens.size) {
        val dataDirValue = optionValue(tokens[index], CommandLineOptions.USER_DATA_DIR, tokens.getOrNull(index + 1))
        if (dataDirValue == null) {
            kept += tokens[index]
        } else if (dataDirValue.consumedNext) {
            index += 1
        }
        index += 1
    }
    if (absoluteRoot != null) {
        kept += listOf(CommandLineOptions.USER_DATA_DIR, absoluteRoot)
    }
    return kept
}

/**
 * `pt` ou `en`, sem caixa; aceita a região (`pt-BR`, `en_US`), que é a forma do
 * `code --locale`, e a descarta: o app tem um português e um inglês.
 */
private fun localeOf(value: String): AppLanguage? =
    when (value.lowercase().substringBefore('-').substringBefore('_')) {
        "pt" -> AppLanguage.PT
        "en" -> AppLanguage.EN
        else -> null
    }

private fun localeError(value: String?): String =
    if (value.isNullOrEmpty()) {
        "Error: ${CommandLineOptions.LOCALE} needs a value: pt or en."
    } else {
        "Error: '$value' is not a supported ${CommandLineOptions.LOCALE}. Use pt or en."
    }

/**
 * O aviso de opção ignorada. Inglês e ASCII pelo mesmo motivo do
 * `--diagnostics`: o console do Windows decodifica com a página de código dele.
 */
internal fun unknownOptionWarning(option: String): String =
    "Warning: '$option' is not a known option and was ignored. Run with --help to list the options."

internal fun commandLineVersion(version: String = CURRENT_APP_VERSION): String = "Usage Monitor $version"

/**
 * Lista só o que existe: `--autostart` fica de fora porque é a marca que a chave
 * `Run` deixa para o registro de arranque, e quem a digita à mão só falsifica a
 * origem gravada. Inglês e ASCII, como [unknownOptionWarning].
 */
internal fun commandLineHelp(version: String = CURRENT_APP_VERSION): String = """
    |${commandLineVersion(version)}
    |
    |Usage: usage-monitor [options]
    |
    |Without options, opens Usage Monitor, or brings the running one to the front.
    |
    |Options
    |  -h, --help         Print this help and exit.
    |  -v, --version      Print the version and exit.
    |  --diagnostics      Save a diagnostics package to the home folder and exit.
    |                     It holds metadata only: no prompt, response or credential.
    |  --locale <pt|en>   Interface language for this run only. The saved choice
    |                     does not change. Ignored if Usage Monitor is already open.
    |  --user-data-dir <dir>
    |                     Keep this run's settings, history, keys and logs in <dir>
    |                     instead of ~/.usage-monitor. Another folder runs side by
    |                     side with the default one; there, automatic updates and
    |                     start with the system stay off.
""".trimMargin()
