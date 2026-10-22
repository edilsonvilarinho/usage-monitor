package com.usagemonitor.terminal

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Lê um segredo da entrada (#440, decisão D5): num terminal, sem eco; por pipe
 * (`echo … | usage-monitor keys set …`, um script), a primeira linha. Nunca de argumento
 * nem de variável de ambiente, que ficariam no histórico do shell e na lista de processos.
 */
internal fun interface TerminalSecretInput {
    /** O segredo digitado, ou `null` sem entrada nenhuma. [prompt] só aparece num terminal. */
    fun read(prompt: String): String?

    companion object {
        val SYSTEM = TerminalSecretInput { prompt ->
            val console = System.console()
            if (console != null) {
                console.readPassword("%s ", prompt)?.let { chars -> String(chars) }
            } else {
                BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)).readLine()
            }
        }
    }
}
