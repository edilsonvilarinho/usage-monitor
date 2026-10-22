package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A tela 2.4 do protótipo: o `status --watch` às 14:23 BRT. */
class TerminalWatchViewTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")

    @Test
    fun `o cabecalho diz a hora, o intervalo e como sair, nos dois idiomas`() {
        assertEquals(
            " usage monitor  14:23 BRT · relê a cada 15 s · Ctrl+C sai",
            TerminalWatchView.header(now, 15.seconds, TerminalLabels(AppLanguage.PT)).plainText
        )
        assertEquals(
            " usage monitor  14:23 BRT · rereads every 2 min · Ctrl+C quits",
            TerminalWatchView.header(now, 2.minutes, TerminalLabels(AppLanguage.EN)).plainText
        )
    }

    @Test
    fun `o quadro e o cabecalho, uma linha em branco e a saida do status`() {
        val status = TerminalOutput(listOf(terminalLine(TerminalSpan(" tabela"))), exitCode = 0)
        val lines = TerminalWatchView.lines(status, now, 15.seconds, TerminalLabels(AppLanguage.PT)).map { line -> line.plainText }
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith(" usage monitor"))
        assertEquals("", lines[1])
        assertEquals(" tabela", lines[2])
    }

    @Test
    fun `o titulo vai em ouro e negrito, e o resto do cabecalho em muted`() {
        val spans = TerminalWatchView.header(now, 15.seconds, TerminalLabels(AppLanguage.PT)).spans
        assertEquals(TerminalColor.Gold, spans[0].color)
        assertTrue(spans[0].bold)
        assertEquals(TerminalColor.Muted, spans[1].color)
    }

    @Test
    fun `o quadro volta ao canto, apaga cada fim de linha e o resto, sem quebra no fim`() {
        val frame = TerminalWatchView.frame(listOf("a", "b"))
        assertEquals("\u001B[Ha\u001B[K\nb\u001B[K\u001B[J", frame)
        assertFalse(frame.endsWith("\n"))
    }
}
