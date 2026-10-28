package com.usagemonitor.presentation.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

/** Texto corrido na moldura da TUI: nunca transborda a largura. */
class TuiTextTest {

    @Test
    fun `words wrap at the width`() {
        assertEquals(listOf("uma linha", "de texto"), TuiText.wrap("uma linha de texto", width = 10))
    }

    @Test
    fun `a word longer than the line is cut, closing the line before it`() {
        assertEquals(
            listOf("ver", "abcdefgh", "ij fim"),
            TuiText.wrap("ver abcdefghij fim", width = 8)
        )
    }

    @Test
    fun `extra spaces collapse and no width means no lines`() {
        assertEquals(listOf("a b"), TuiText.wrap("  a   b  ", width = 10))
        assertEquals(emptyList(), TuiText.wrap("texto", width = 0))
        assertEquals(emptyList(), TuiText.wrap("", width = 10))
    }
}
