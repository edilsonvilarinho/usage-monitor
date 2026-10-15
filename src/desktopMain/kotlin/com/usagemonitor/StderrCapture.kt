package com.usagemonitor

import com.usagemonitor.data.diagnostics.AppLogFile
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset

/**
 * Copia o `stderr` do processo para o `app.log` (issue #416).
 *
 * O app empacotado é aberto pelo menu ou pelo autostart, sem terminal: o que a
 * JVM, a AWT e o Skiko escrevem no `stderr` — aviso de render API, falha de
 * visual X11, `Exception in thread` — vai para lugar nenhum. É justamente o
 * texto que explicaria uma janela preta. A saída original continua: quem roda
 * pelo terminal vê o mesmo de antes.
 *
 * **Só a linha de cabeçalho de uma pilha entra.** Linhas `at …`, `... N more`,
 * `Caused by:` e `Suppressed:` ficam fora: a falha não tratada já entra no log
 * pelo `CrashHandler`, com a pilha estruturada; copiá-las aqui daria cinquenta
 * linhas para a mesma falha.
 *
 * **Teto por minuto** ([maxLinesPerMinute]): uma biblioteca que despeja aviso em
 * laço não pode girar o log e apagar o travamento de ontem. O excedente vira uma
 * linha só com a contagem, na janela seguinte.
 */
internal class StderrCapture(
    private val appLog: AppLogFile,
    private val maxLinesPerMinute: Int = DEFAULT_MAX_LINES_PER_MINUTE,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L }
) {
    private val lock = Any()
    private var windowStart = Long.MIN_VALUE
    private var linesInWindow = 0
    private var droppedInWindow = 0

    /** Escrita do próprio log que volte ao `stderr` não pode virar laço. */
    private val writing = ThreadLocal.withInitial { false }

    fun install() {
        val original = System.err
        val charset = stderrCharset()
        val tee = TeeLineOutputStream(original, charset, ::onLine)
        System.setErr(PrintStream(tee, true, charset))
    }

    internal fun onLine(line: String) {
        if (writing.get() || line.isBlank() || isStackContinuation(line)) {
            return
        }
        val dropped = admit() ?: return
        writing.set(true)
        try {
            if (dropped > 0) {
                appLog.warn("stderr", "$dropped linhas do stderr descartadas pelo teto de $maxLinesPerMinute por minuto")
            }
            appLog.warn("stderr", line)
        } finally {
            writing.set(false)
        }
    }

    /**
     * Devolve quantas linhas a janela anterior descartou (para registrar ao abrir
     * a nova), ou nulo se esta linha estoura o teto da janela atual.
     */
    private fun admit(): Int? {
        val now = nowMillis()
        synchronized(lock) {
            var droppedBefore = 0
            if (windowStart == Long.MIN_VALUE || now - windowStart >= WINDOW_MILLIS) {
                droppedBefore = droppedInWindow
                windowStart = now
                linesInWindow = 0
                droppedInWindow = 0
            }
            if (linesInWindow >= maxLinesPerMinute) {
                droppedInWindow++
                return null
            }
            linesInWindow++
            return droppedBefore
        }
    }

    internal companion object {
        const val DEFAULT_MAX_LINES_PER_MINUTE = 60
        private const val WINDOW_MILLIS = 60_000L

        private val STACK_CONTINUATION = Regex("^\\s+(at |\\.\\.\\. \\d+ more)|^\\s*(Caused by:|Suppressed:)")

        fun isStackContinuation(line: String): Boolean = STACK_CONTINUATION.containsMatchIn(line)

        /**
         * O `stderr` do JDK 17 codifica com `sun.stderr.encoding` quando há console
         * e com o default da plataforma fora dele. Usar o mesmo nas duas pontas
         * mantém o terminal legível e a linha do log decodificada certo.
         */
        private fun stderrCharset(): Charset {
            val name = System.getProperty("sun.stderr.encoding")
            return runCatching { if (name != null) Charset.forName(name) else null }.getOrNull()
                ?: Charset.defaultCharset()
        }
    }
}

/**
 * Repassa cada byte à saída original e entrega cada linha completa a [onLine].
 * A linha só sai no `\n`: o `PrintStream` escreve uma mensagem em pedaços.
 */
private class TeeLineOutputStream(
    private val original: OutputStream,
    private val charset: Charset,
    private val onLine: (String) -> Unit
) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    override fun write(byte: Int) {
        original.write(byte)
        if (byte == '\n'.code) {
            emitLine()
        } else {
            buffer.write(byte)
        }
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        original.write(bytes, offset, length)
        for (index in offset until offset + length) {
            val byte = bytes[index].toInt()
            if (byte == '\n'.code) {
                emitLine()
            } else {
                buffer.write(byte)
            }
        }
    }

    override fun flush() = original.flush()

    private fun emitLine() {
        val line = buffer.toString(charset).trimEnd('\r')
        buffer.reset()
        // Copiar para o log nunca pode quebrar a escrita no stderr.
        runCatching { onLine(line) }
    }
}
