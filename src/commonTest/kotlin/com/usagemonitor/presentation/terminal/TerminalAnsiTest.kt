package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalAnsiTest {

    private val anthropic = TerminalColor.Source(ApiSource.ANTHROPIC)
    private val line = terminalLine(terminalSpan("Claude", anthropic), terminalSpan(" crítico", TerminalColor.Crit, bold = true))

    @Test
    fun `truecolor escuro usa os hexes do terminal css`() {
        val text = TerminalAnsi.render(line, TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true))
        assertEquals("\u001B[38;2;79;140;255mClaude\u001B[0m\u001B[1;38;2;232;106;106m crítico\u001B[0m", text)
    }

    @Test
    fun `truecolor claro usa os acentos claros`() {
        val text = TerminalAnsi.render(line, TerminalProfile(TerminalPalette.TRUECOLOR_LIGHT, unicode = true))
        assertEquals("\u001B[38;2;21;101;192mClaude\u001B[0m\u001B[1;38;2;179;38;30m crítico\u001B[0m", text)
    }

    @Test
    fun `16 cores emite o indice ANSI`() {
        val text = TerminalAnsi.render(line, TerminalProfile(TerminalPalette.ANSI16, unicode = true))
        assertEquals("\u001B[34mClaude\u001B[0m\u001B[1;31m crítico\u001B[0m", text)
    }

    @Test
    fun `sem cor sai so o texto`() {
        assertEquals("Claude crítico", TerminalAnsi.render(line, TerminalProfile(TerminalPalette.NONE, unicode = true)))
    }

    @Test
    fun `sem Unicode o texto perde o acento`() {
        assertEquals("Claude critico", TerminalAnsi.render(line, TerminalProfile(TerminalPalette.NONE, unicode = false)))
    }

    @Test
    fun `selecao pinta o fundo, o unico fundo do modo terminal`() {
        val selected = TerminalLine(listOf(TerminalSpan("▸ Codex", selected = true)))
        val text = TerminalAnsi.render(selected, TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true))
        assertEquals("\u001B[48;2;42;38;38m▸ Codex\u001B[0m", text)
    }

    @Test
    fun `trecho sem estilo nao leva sequencia`() {
        val plain = terminalLine(terminalSpan("lido há 38 s"))
        assertEquals("lido há 38 s", TerminalAnsi.render(plain, TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)))
    }

    @Test
    fun `transliteracao cobre o portugues e a pontuacao dos textos`() {
        assertEquals("atencao, critico, reinicio, sessao - 47 min.", withoutAccents("atenção, crítico, reinício, sessão · 47 min…"))
        assertEquals("ACAO EM DIA", withoutAccents("AÇÃO EM DIA"))
        assertEquals("?", withoutAccents("●"))
    }

    @Test
    fun `linhas se juntam com quebra de linha`() {
        val text = TerminalAnsi.render(listOf(terminalLine(terminalSpan("a")), terminalLine(terminalSpan("b"))), TerminalProfile(TerminalPalette.NONE, unicode = true))
        assertEquals("a\nb", text)
    }

    @Test
    fun `padEnd completa ate a largura e nao corta linha mais larga`() {
        assertEquals(10, terminalLine(terminalSpan("Codex")).padEnd(10).width)
        assertEquals(5, terminalLine(terminalSpan("Codex")).padEnd(3).width)
    }
}
