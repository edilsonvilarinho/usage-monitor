package com.usagemonitor

import com.usagemonitor.domain.entity.AppLanguage

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

    /** Empacota o diagnóstico e sai, sem janela (#416). */
    data object Diagnostics : CommandLineRequest

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
        val locale: AppLanguage? = null
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
}

internal fun parseCommandLine(arguments: Array<String>): CommandLineRequest {
    var help = false
    var version = false
    var diagnostics = false
    var autostart = false
    var locale: AppLanguage? = null
    var invalid: String? = null
    val unknown = mutableListOf<String>()
    // `trim` como o `StartupOrigin.from` sempre fez (o teste dele cobre " --autostart ").
    val tokens = arguments.map { raw -> raw.trim() }.filter { argument -> argument.isNotEmpty() }
    var index = 0
    while (index < tokens.size) {
        val argument = tokens[index]
        when {
            argument == CommandLineOptions.HELP || argument == CommandLineOptions.HELP_SHORT -> help = true
            argument == CommandLineOptions.VERSION || argument == CommandLineOptions.VERSION_SHORT -> version = true
            argument == DiagnosticsExport.ARGUMENT -> diagnostics = true
            argument == StartupOrigin.AUTO_START_ARGUMENT -> autostart = true
            argument == CommandLineOptions.LOCALE || argument.startsWith("${CommandLineOptions.LOCALE}=") -> {
                // Duas formas: `--locale en`, a documentada, e `--locale=en`.
                val inline = if (argument.contains('=')) argument.substringAfter('=') else null
                val value = inline ?: tokens.getOrNull(index + 1)?.takeUnless { next -> next.startsWith("-") }
                if (inline == null && value != null) {
                    index += 1
                }
                val parsed = value?.let(::localeOf)
                if (parsed == null) {
                    invalid = invalid ?: localeError(value)
                } else {
                    locale = parsed
                }
            }
            else -> unknown += argument
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
    if (diagnostics) {
        return CommandLineRequest.Diagnostics
    }
    return CommandLineRequest.Launch(autostart = autostart, unknownOptions = unknown, locale = locale)
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
""".trimMargin()
