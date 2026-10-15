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

internal fun parseCommandLine(arguments: Array<String>): CommandLineRequest {
    var diagnostics = false
    var autostart = false
    val unknown = mutableListOf<String>()
    for (raw in arguments) {
        // `trim` como o `StartupOrigin.from` sempre fez: a chave `Run` antiga
        // chegava com espaço sobrando em algumas máquinas.
        val argument = raw.trim()
        when (argument) {
            "" -> Unit
            DiagnosticsExport.ARGUMENT -> diagnostics = true
            StartupOrigin.AUTO_START_ARGUMENT -> autostart = true
            else -> unknown += argument
        }
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
    "Warning: '$option' is not a known option and was ignored."
