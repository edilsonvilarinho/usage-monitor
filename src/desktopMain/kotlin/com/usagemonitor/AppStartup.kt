package com.usagemonitor

import com.usagemonitor.update.updateAckTokenFromEnv

/**
 * O que o arranque sabe sobre si mesmo: de onde veio, em que máquina, e se foi
 * lançado pelo atualizador do Linux.
 *
 * O contexto da máquina é resolvido uma vez e reusado pelos pontos que gravam: a
 * resolução lê a entrada de autostart do disco, e a resposta não muda dentro do
 * processo.
 */
internal class AppStartup(args: Array<String>) {
    val diagnostics = StartupDiagnostics()
    val origin = StartupOrigin.from(args)
    val machineContext: StartupMachineContext = StartupMachineContext.current()
    val focusRequests = FocusRequestChannel()

    /**
     * Token do health check da atualização Linux, quando este processo foi lançado
     * pelo `linux-updater.sh`. Vem de variável de ambiente, não de argv — um
     * argumento `--update-ack=X` vazava como opção da própria JVM no launcher
     * nativo do jpackage (issue #118). Fora de [StartupOrigin]: aquele enum
     * responde "autostart ou manual", e o health check não é uma terceira origem.
     */
    val updateAckToken: String? = updateAckTokenFromEnv()

    fun record(outcome: StartupOutcome, context: StartupMachineContext = machineContext) {
        diagnostics.record(origin, outcome, machineContext = context)
    }
}
