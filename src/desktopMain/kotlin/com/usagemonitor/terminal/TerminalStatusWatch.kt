package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalStatusView
import com.usagemonitor.presentation.terminal.TerminalWatchView
import kotlin.time.Duration

/**
 * O laço do `status --watch` (#453): relê o cache a cada intervalo e redesenha a tabela no
 * lugar, até o Ctrl+C. Nenhuma requisição: quem muda o número é o coletor. Sem JLine, sem
 * tela alternativa e sem modo cru (D4): o comando não lê tecla, e o último quadro fica na
 * tela depois do Ctrl+C. Mais linhas que a altura do painel rolam a tela, e o cabeçalho sai
 * por cima: o comando não lê o tamanho do terminal.
 */
internal object TerminalStatusWatch {
    const val EXIT_NO_TERMINAL = 1

    fun run(
        interval: Duration,
        languageOverride: AppLanguage?,
        reader: TerminalReader,
        console: TerminalConsole,
        redrawsInPlace: Boolean,
        pause: (Duration) -> Unit
    ): Int {
        if (!redrawsInPlace) {
            console.printText(TerminalLabels(reader.language(languageOverride)).watchNeedsTerminal)
            return EXIT_NO_TERMINAL
        }
        // O Ctrl+C encerra o JVM sem passar pelo `finally`: cursor e quebra de linha voltam pelo shutdown hook.
        val restore = Thread { console.write(TerminalWatchView.LEAVE + "\n") }
        Runtime.getRuntime().addShutdownHook(restore)
        console.write(TerminalWatchView.ENTER)
        try {
            while (true) {
                val reading = reader.read(languageOverride)
                val labels = TerminalLabels(reading.language)
                val status = TerminalStatusView.render(reading.accounts, reading.now, reading.collector, maxAge = null, labels, console.profile)
                val lines = TerminalWatchView.lines(status, reading.now, interval, labels)
                console.write(TerminalWatchView.frame(lines.map { line -> TerminalAnsi.render(line, console.profile) }))
                pause(interval)
            }
        } finally {
            console.write(TerminalWatchView.LEAVE + "\n")
            runCatching { Runtime.getRuntime().removeShutdownHook(restore) }
        }
    }
}

/**
 * Redesenhar no lugar pede um terminal de verdade, o mesmo critério do `tui`: saída num
 * pipe ou num arquivo ficaria cheia de sequências de controle, e `TERM=dumb` não as entende.
 */
internal fun canRedrawInPlace(consolePresent: Boolean, environment: Map<String, String>): Boolean =
    consolePresent && !environment["TERM"].equals("dumb", ignoreCase = true)
