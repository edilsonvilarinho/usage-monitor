package com.usagemonitor.terminal

import com.usagemonitor.presentation.terminal.TuiKey
import com.usagemonitor.presentation.terminal.TuiSize
import org.jline.terminal.Attributes
import org.jline.terminal.Size
import org.jline.terminal.impl.ExternalTerminal
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O [JLineTuiIo] sobre um terminal JLine em memória: o que a TUI escreve sai num buffer e as
 * teclas entram por um cano, sem terminal de verdade.
 */
class JLineTuiIoTest {
    private val keyboard = PipedOutputStream()
    private val input = PipedInputStream(keyboard)
    private val output = ByteArrayOutputStream()
    private val opened = mutableListOf<ExternalTerminal>()

    @AfterTest
    fun closeTerminals() {
        opened.forEach { terminal -> runCatching { terminal.close() } }
    }

    private fun terminal(type: String = "xterm-256color"): ExternalTerminal =
        ExternalTerminal("test", type, input, output, Charsets.UTF_8).also { terminal -> opened += terminal }

    private fun written(): String = output.toString(Charsets.UTF_8)

    private fun type(text: String) {
        keyboard.write(text.toByteArray(Charsets.UTF_8))
        keyboard.flush()
    }

    @Test
    fun `opening enters the alternate screen hides the cursor and raw mode`() {
        val terminal = terminal()
        val io = JLineTuiIo(terminal)

        assertTrue(written().contains("\u001B[?1049h"), written())
        assertTrue(written().contains("\u001B[?25l"), written())
        assertFalse(terminal.attributes.getLocalFlag(Attributes.LocalFlag.ICANON))
        assertFalse(terminal.attributes.getLocalFlag(Attributes.LocalFlag.ECHO))
        assertEquals("UTF-8", io.encoding)
    }

    @Test
    fun `a terminal without the capability gets the xterm sequence`() {
        // O `dumb` da terminfo não descreve tela alternativa nem cursor: entra a sequência do xterm.
        JLineTuiIo(terminal(type = "dumb"))

        assertTrue(written().contains("\u001B[?1049h"), written())
        assertTrue(written().contains("\u001B[?25l"), written())
    }

    @Test
    fun `a frame homes the cursor clears each line end and the rest of the screen`() {
        val terminal = terminal()
        val io = JLineTuiIo(terminal)
        // O terminal emulado aplica `ONLCR` como um tty (`\n` vira `\r\n`); sem o OPOST, sai o
        // quadro tal como a TUI o escreveu.
        terminal.attributes = Attributes(terminal.attributes).apply { setOutputFlag(Attributes.OutputFlag.OPOST, false) }
        output.reset()

        io.draw(listOf("uso", "cota"))

        assertEquals("\u001B[Huso\u001B[K\r\ncota\u001B[K\u001B[J", written())
    }

    @Test
    fun `size falls back to 80 by 24 while the terminal reports zero`() {
        val terminal = terminal()
        val io = JLineTuiIo(terminal)
        terminal.setSize(Size(0, 0))

        assertEquals(TuiSize(columns = 80, rows = 24), io.size())

        terminal.setSize(Size(132, 43))
        assertEquals(TuiSize(columns = 132, rows = 43), io.size())
    }

    @Test
    fun `keys are decoded and the deadline gives null`() {
        val io = JLineTuiIo(terminal())

        type("q")
        assertEquals(TuiKey.Text('q'), io.readKey(2_000))
        type("\u001B[A")
        assertEquals(TuiKey.Up, io.readKey(2_000))
        type("\r")
        assertEquals(TuiKey.Enter, io.readKey(2_000))
        assertNull(io.readKey(50))
    }

    @Test
    fun `ctrl c arrives as a signal and becomes an interrupt`() {
        // O modo cru mantém o ISIG: o byte 3 vira SIGINT no terminal, não caractere.
        val io = JLineTuiIo(terminal())

        type("\u0003")

        assertEquals(TuiKey.Interrupt, io.readKey(2_000))
    }

    @Test
    fun `a size change without the signal stops the wait and is reported once`() {
        val terminal = terminal()
        terminal.setSize(Size(100, 30))
        val io = JLineTuiIo(terminal)
        // A primeira volta só guarda o tamanho visto.
        assertNull(io.readKey(10))
        assertFalse(io.consumeResize())

        terminal.setSize(Size(120, 30))

        assertNull(io.readKey(5_000))
        assertTrue(io.consumeResize())
        assertFalse(io.consumeResize())
    }

    @Test
    fun `closing restores the screen and the previous mode only once`() {
        val terminal = terminal()
        val io = JLineTuiIo(terminal)
        output.reset()

        io.close()
        val restored = written()
        io.close()

        assertTrue(restored.startsWith("\u001B[0m"), restored)
        // A terminfo do xterm junta o cursor que pisca: `ESC [ ? 12 ; 25 h`.
        assertTrue(Regex("\u001B\\[\\?[0-9;]*25h").containsMatchIn(restored), restored)
        assertTrue(restored.contains("\u001B[?1049l"), restored)
        assertEquals(restored, written())
        // O terminal do JLine foi fechado: ler os atributos depois disso falha.
        assertFailsWith<IllegalStateException> { terminal.attributes }
    }
}
