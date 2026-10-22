package com.usagemonitor.presentation.terminal

import com.usagemonitor.presentation.ui.TelegramBotMessages
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * `usage-monitor status --watch` (#453, tela 2.4 do protótipo): a tabela do `status`
 * com uma linha no alto, redesenhada no lugar a cada releitura, para ficar aberta num
 * painel do tmux. O cabeçalho não diz "ao vivo": com nada coletando, a tabela já avisa
 * que os números não mudam, e ele não pode contradizer.
 */
internal object TerminalWatchView {
    private const val CSI = "\u001B["

    /**
     * Ao entrar: esconde o cursor e desliga a quebra automática de linha (DECAWM). Num
     * painel estreito a linha é cortada na borda, em vez de quebrar e empurrar o cabeçalho
     * para fora do painel. [LEAVE] devolve os dois.
     */
    const val ENTER = "${CSI}?25l${CSI}?7l"
    const val LEAVE = "${CSI}?7h${CSI}?25h"

    fun header(now: Instant, interval: Duration, labels: TerminalLabels): TerminalLine =
        terminalLine(
            TerminalSpan(" usage monitor", TerminalColor.Gold, bold = true),
            TerminalSpan("  " + labels.watchHeader(TelegramBotMessages.clock(now), interval), TerminalColor.Muted)
        )

    /** O quadro inteiro: o cabeçalho, uma linha em branco e o que o `status` imprimiria. */
    fun lines(status: TerminalOutput, now: Instant, interval: Duration, labels: TerminalLabels): List<TerminalLine> =
        listOf(header(now, interval, labels), TerminalLine.EMPTY) + status.lines

    /**
     * Um quadro de uma vez, no lugar do anterior: cursor no canto, cada linha seguida de
     * "apaga até o fim" e "apaga o resto" no fim, como o desenho da TUI. Sem limpar a tela
     * antes, o terminal não pisca; sem quebra de linha depois da última, um quadro da altura
     * do painel não rola a tela.
     */
    fun frame(lines: List<String>): String {
        val frame = StringBuilder()
        frame.append(CSI).append('H')
        lines.forEachIndexed { index, line ->
            if (index > 0) {
                frame.append('\n')
            }
            frame.append(line).append(CSI).append('K')
        }
        frame.append(CSI).append('J')
        return frame.toString()
    }
}
