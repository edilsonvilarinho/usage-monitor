package com.usagemonitor.presentation.terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalProfileTest {

    private fun detect(
        environment: Map<String, String> = emptyMap(),
        terminal: Boolean = true,
        encoding: String? = "UTF-8"
    ) = detectTerminalProfile(environment, terminal, encoding)

    @Test
    fun `terminal sem COLORTERM fica em 16 cores`() {
        assertEquals(TerminalPalette.ANSI16, detect().palette)
    }

    @Test
    fun `COLORTERM truecolor e 24bit ligam o truecolor escuro`() {
        assertEquals(TerminalPalette.TRUECOLOR_DARK, detect(mapOf("COLORTERM" to "truecolor")).palette)
        assertEquals(TerminalPalette.TRUECOLOR_DARK, detect(mapOf("COLORTERM" to "24BIT")).palette)
    }

    @Test
    fun `Windows Terminal conta como truecolor`() {
        assertEquals(TerminalPalette.TRUECOLOR_DARK, detect(mapOf("WT_SESSION" to "abc")).palette)
    }

    @Test
    fun `fundo claro pelo COLORFGBG troca para a paleta clara`() {
        val light = mapOf("COLORTERM" to "truecolor", "COLORFGBG" to "0;15")
        val dark = mapOf("COLORTERM" to "truecolor", "COLORFGBG" to "15;0")
        val grey = mapOf("COLORTERM" to "truecolor", "COLORFGBG" to "15;8")
        assertEquals(TerminalPalette.TRUECOLOR_LIGHT, detect(light).palette)
        assertEquals(TerminalPalette.TRUECOLOR_DARK, detect(dark).palette)
        assertEquals(TerminalPalette.TRUECOLOR_DARK, detect(grey).palette)
    }

    @Test
    fun `NO_COLOR e TERM dumb desligam a cor`() {
        assertEquals(TerminalPalette.NONE, detect(mapOf("NO_COLOR" to "1", "COLORTERM" to "truecolor")).palette)
        assertEquals(TerminalPalette.NONE, detect(mapOf("TERM" to "dumb")).palette)
    }

    @Test
    fun `NO_COLOR vazio nao conta, como manda o no-color org`() {
        assertEquals(TerminalPalette.ANSI16, detect(mapOf("NO_COLOR" to "")).palette)
    }

    @Test
    fun `saida que nao e terminal fica sem cor, a nao ser com FORCE_COLOR`() {
        assertEquals(TerminalPalette.NONE, detect(terminal = false).palette)
        assertEquals(TerminalPalette.ANSI16, detect(mapOf("FORCE_COLOR" to "1"), terminal = false).palette)
        assertEquals(TerminalPalette.NONE, detect(mapOf("FORCE_COLOR" to "0"), terminal = false).palette)
    }

    @Test
    fun `NO_COLOR vence FORCE_COLOR`() {
        assertEquals(TerminalPalette.NONE, detect(mapOf("NO_COLOR" to "1", "FORCE_COLOR" to "1")).palette)
    }

    @Test
    fun `glifos Unicode so com saida em UTF-8`() {
        assertTrue(detect(encoding = "UTF-8").unicode)
        assertTrue(detect(encoding = "utf8").unicode)
        assertFalse(detect(encoding = "Cp850").unicode)
        assertFalse(detect(encoding = "ANSI_X3.4-1968").unicode)
        assertFalse(detect(encoding = null).unicode)
    }

    @Test
    fun `cor e glifo sao decisoes separadas`() {
        val windowsConsole = detect(mapOf("WT_SESSION" to "abc"), encoding = "Cp850")
        assertEquals(TerminalPalette.TRUECOLOR_DARK, windowsConsole.palette)
        assertEquals(TerminalGlyphs.ASCII, windowsConsole.glyphs)
    }

    @Test
    fun `anel em quartos so na coluna Unicode`() {
        assertEquals("○", TerminalGlyphs.UNICODE.ring(5))
        assertEquals("◑", TerminalGlyphs.UNICODE.ring(62))
        assertEquals("●", TerminalGlyphs.UNICODE.ring(91))
        assertEquals(null, TerminalGlyphs.ASCII.ring(91))
    }
}
