package com.usagemonitor.presentation.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TuiKeyDecoderTest {

    /** Decodifica tudo o que [input] traz, como o laço faria tecla a tecla. */
    private fun keys(input: String): List<TuiKey?> {
        val pending = ArrayDeque(input.map { char -> char.code })
        val out = mutableListOf<TuiKey?>()
        while (pending.isNotEmpty()) {
            out += TuiKeyDecoder.decode(pending.removeFirst()) { _ -> pending.removeFirstOrNull() ?: TuiKeyDecoder.TIMEOUT }
        }
        return out
    }

    @Test
    fun `setas nas duas formas e com modificador`() {
        assertEquals(listOf(TuiKey.Up, TuiKey.Down, TuiKey.Right, TuiKey.Left), keys("\u001B[A\u001B[B\u001B[C\u001B[D"))
        assertEquals(listOf(TuiKey.Up, TuiKey.Left), keys("\u001BOA\u001BOD"))
        assertEquals(listOf(TuiKey.Right), keys("\u001B[1;5C"))
    }

    @Test
    fun `esc sozinho so depois do prazo e sequencia desconhecida e engolida`() {
        assertEquals(listOf(TuiKey.Escape), keys("\u001B"))
        assertEquals(listOf(null, TuiKey.Text('q')), keys("\u001B[15~q"))
        assertEquals(listOf(null), keys("\u001Bx"))
    }

    @Test
    fun `teclas de controle e caracteres comuns`() {
        assertEquals(listOf(TuiKey.Enter, TuiKey.Enter, TuiKey.Tab, TuiKey.Space, TuiKey.Backspace, TuiKey.Backspace), keys("\r\n\t \u007F\b"))
        assertEquals(listOf(TuiKey.Interrupt), keys("\u0003"))
        assertEquals(listOf(TuiKey.Text('q'), TuiKey.Text('?'), TuiKey.Text('ç')), keys("q?ç"))
        assertEquals(TuiKey.Interrupt, TuiKeyDecoder.decode(TuiKeyDecoder.END_OF_INPUT) { _ -> TuiKeyDecoder.TIMEOUT })
    }
}
