package com.usagemonitor.terminal

import com.usagemonitor.presentation.terminal.TuiKey
import com.usagemonitor.presentation.terminal.TuiKeyDecoder
import com.usagemonitor.presentation.terminal.TuiSize
import org.jline.terminal.Attributes
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.InfoCmp.Capability
import org.jline.utils.NonBlockingReader
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/**
 * O terminal do modo interativo, do jeito que o laço precisa: tamanho, uma tecla com prazo e
 * o desenho de uma tela inteira. Interface para o teste do laço rodar sem terminal.
 */
internal interface TuiIo {
    fun size(): TuiSize

    /** A próxima tecla, ou `null` quando [timeoutMillis] passa sem nenhuma. */
    fun readKey(timeoutMillis: Long): TuiKey?

    /** Verdadeiro uma vez depois de cada mudança de tamanho (SIGWINCH). */
    fun consumeResize(): Boolean

    fun draw(lines: List<String>)
}

/**
 * O terminal pelo JLine (decisão D1 do plano da #441): modo cru, teclas, tamanho e a tela
 * alternativa, que devolve o terminal como estava ao sair. O desenho não passa pelo JLine:
 * as linhas já chegam em ANSI, pelas quatro paletas da #439.
 *
 * O construtor é `internal` para o teste montar sobre um terminal JLine em memória; o app
 * só entra por [open], que recusa o terminal burro.
 */
internal class JLineTuiIo internal constructor(private val terminal: Terminal) : TuiIo, AutoCloseable {
    private val reader: NonBlockingReader = terminal.reader()
    private val resized = AtomicBoolean(false)
    private val interrupted = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val previous: Attributes = terminal.enterRawMode()
    private var lastSize: TuiSize? = null

    init {
        terminal.handle(Terminal.Signal.WINCH) { resized.set(true) }
        terminal.handle(Terminal.Signal.INT) { interrupted.set(true) }
        capability(Capability.enter_ca_mode, "${CSI}?1049h")
        capability(Capability.keypad_xmit, "")
        capability(Capability.cursor_invisible, "${CSI}?25l")
        terminal.flush()
    }

    val encoding: String
        get() = terminal.stdoutEncoding().name()

    override fun size(): TuiSize {
        val size = terminal.size
        return TuiSize(columns = size.columns.takeIf { columns -> columns > 0 } ?: DEFAULT_COLUMNS, rows = size.rows.takeIf { rows -> rows > 0 } ?: DEFAULT_ROWS)
    }

    override fun readKey(timeoutMillis: Long): TuiKey? {
        // Ctrl+C chega como sinal (o modo cru do JLine mantém o ISIG): o laço o vê na próxima volta.
        var remaining = timeoutMillis
        while (remaining > 0) {
            if (interrupted.getAndSet(false)) {
                return TuiKey.Interrupt
            }
            val slice = minOf(remaining, POLL_MILLIS)
            val first = reader.read(slice)
            if (first != NonBlockingReader.READ_EXPIRED) {
                return TuiKeyDecoder.decode(first) { timeout -> reader.read(timeout) }
            }
            if (resized.get() || sizeChanged()) {
                return null
            }
            remaining -= slice
        }
        return if (interrupted.getAndSet(false)) TuiKey.Interrupt else null
    }

    override fun consumeResize(): Boolean = resized.getAndSet(false)

    /**
     * O tamanho mudou desde a última volta. Rede para quando o SIGWINCH não chega (sem
     * `sun.misc.Signal`, ou pelo SSH de um cliente que não repassa o sinal).
     */
    private fun sizeChanged(): Boolean {
        val current = size()
        val changed = lastSize != null && current != lastSize
        lastSize = current
        if (changed) {
            resized.set(true)
        }
        return changed
    }

    /**
     * Uma tela inteira de uma vez: cursor no canto, cada linha seguida de "apaga até o fim", e
     * "apaga o resto" no fim. Sem limpar a tela antes, o terminal não pisca entre os quadros.
     */
    override fun draw(lines: List<String>) {
        val frame = StringBuilder()
        frame.append(CSI).append('H')
        lines.forEachIndexed { index, line ->
            if (index > 0) {
                frame.append("\r\n")
            }
            frame.append(line).append(CSI).append('K')
        }
        frame.append(CSI).append('J')
        terminal.writer().write(frame.toString())
        terminal.flush()
    }

    /** Devolve o terminal: tela normal, cursor visível e o modo de antes. Pode ser chamado de novo. */
    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        runCatching {
            terminal.writer().write("${CSI}0m")
            capability(Capability.cursor_visible, "${CSI}?25h")
            capability(Capability.keypad_local, "")
            capability(Capability.exit_ca_mode, "${CSI}?1049l")
            terminal.flush()
            terminal.attributes = previous
        }
        runCatching { terminal.close() }
    }

    /** A sequência da terminfo; sem ela (terminal pouco descrito), a do xterm. */
    private fun capability(capability: Capability, fallback: String) {
        if (!terminal.puts(capability) && fallback.isNotEmpty()) {
            terminal.writer().write(fallback)
        }
    }

    companion object {
        private const val CSI = "\u001B["
        private const val POLL_MILLIS = 200L
        private const val DEFAULT_COLUMNS = 80
        private const val DEFAULT_ROWS = 24

        /** O logger do JLine avisa no `stderr` quando cai no terminal burro; aqui quem avisa é a TUI. */
        private val jlineLogger: Logger = Logger.getLogger("org.jline")

        /**
         * O terminal do processo, ou `null` quando não há um de verdade (entrada ou saída
         * redirecionada, `TERM=dumb`): ali não há modo cru nem tela alternativa.
         */
        fun open(): JLineTuiIo? {
            jlineLogger.level = Level.OFF
            if (System.console() == null) {
                return null
            }
            val terminal = runCatching { TerminalBuilder.builder().system(true).dumb(true).build() }.getOrNull() ?: return null
            if (terminal.type == Terminal.TYPE_DUMB || terminal.type == Terminal.TYPE_DUMB_COLOR) {
                terminal.close()
                return null
            }
            return JLineTuiIo(terminal)
        }
    }
}
