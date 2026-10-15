package com.usagemonitor

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
        val unknownOptions: List<String>
    ) : CommandLineRequest
}

internal object CommandLineOptions {
    const val HELP = "--help"
    const val HELP_SHORT = "-h"
    const val VERSION = "--version"
    const val VERSION_SHORT = "-v"
}

internal fun parseCommandLine(arguments: Array<String>): CommandLineRequest {
    var help = false
    var version = false
    var diagnostics = false
    var autostart = false
    val unknown = mutableListOf<String>()
    for (raw in arguments) {
        // `trim` como o `StartupOrigin.from` sempre fez: a chave `Run` antiga
        // chegava com espaço sobrando em algumas máquinas.
        val argument = raw.trim()
        when (argument) {
            "" -> Unit
            CommandLineOptions.HELP, CommandLineOptions.HELP_SHORT -> help = true
            CommandLineOptions.VERSION, CommandLineOptions.VERSION_SHORT -> version = true
            DiagnosticsExport.ARGUMENT -> diagnostics = true
            StartupOrigin.AUTO_START_ARGUMENT -> autostart = true
            else -> unknown += argument
        }
    }
    // Só leitura vence ação: `--help --diagnostics` não grava zip nenhum.
    if (help) {
        return CommandLineRequest.Help
    }
    if (version) {
        return CommandLineRequest.Version
    }
    if (diagnostics) {
        return CommandLineRequest.Diagnostics
    }
    return CommandLineRequest.Launch(autostart = autostart, unknownOptions = unknown)
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
""".trimMargin()
