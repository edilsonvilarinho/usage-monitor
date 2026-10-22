package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiFrameTest {

    private val pt = TuiLabels(AppLanguage.PT)
    private val en = TuiLabels(AppLanguage.EN)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val mono = TerminalProfile(TerminalPalette.NONE, unicode = false)

    // Quarta, 21/10/2026, 14:23 BRT: o horário dos exemplos do protótipo.
    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val allTabs = TuiTab.entries

    private fun frame(size: TuiSize, body: List<TerminalLine> = emptyList(), profile: TerminalProfile = color, tabs: List<TuiTab> = allTabs) =
        TuiFrame.render(size, tabs, TuiTab.OVERVIEW, TerminalSpan("14:23 BRT · ao vivo", TerminalColor.Muted), body, pt.commonShortcuts, pt, profile)

    @Test
    fun `a moldura de 96 colunas tem a largura e a altura exatas do terminal`() {
        val lines = frame(TuiSize(96, 30))
        assertEquals(30, lines.size)
        assertTrue(lines.all { line -> line.width == 96 }, lines.map { line -> line.width }.toString())
    }

    @Test
    fun `terminal mais largo que 96 nao alarga a moldura`() {
        assertTrue(frame(TuiSize(140, 24)).all { line -> line.width == 96 })
        assertTrue(frame(TuiSize(80, 24)).all { line -> line.width == 80 })
    }

    @Test
    fun `borda de cima tem o titulo em ouro a esquerda e o status a direita`() {
        val top = frame(TuiSize(96, 20)).first()
        assertTrue(top.plainText.startsWith("╭─ usage monitor ─"))
        assertTrue(top.plainText.endsWith("─ 14:23 BRT · ao vivo ─╮"))
        val title = top.spans.first { span -> span.text.contains("usage monitor") }
        assertEquals(TerminalColor.Gold, title.color)
        assertTrue(title.bold)
    }

    @Test
    fun `abas numeradas com a ativa no fundo de selecao e as outras em muted`() {
        val tabs = frame(TuiSize(96, 20))[1]
        assertTrue(tabs.plainText.startsWith("│ 1 Visão geral   2 Sessões   3 Histórico   4 Time   5 Configurações "))
        val active = tabs.spans.first { span -> span.text.contains("Visão geral") }
        assertTrue(active.selected && active.bold)
        assertEquals(TerminalColor.Muted, tabs.spans.first { span -> span.text.contains("Sessões") }.color)
    }

    @Test
    fun `sem time a aba 4 some e as outras mantem o numero`() {
        val tabs = frame(TuiSize(96, 20), tabs = TuiContext(teamAvailable = false, stacked = false).tabs)[1].plainText
        assertTrue("4 Time" !in tabs)
        assertTrue("5 Configurações" in tabs)
    }

    @Test
    fun `sem cor a aba ativa ganha colchetes com a mesma largura e a moldura vira ASCII`() {
        val lines = frame(TuiSize(96, 20), profile = mono)
        assertTrue(lines[1].plainText.startsWith("|[1 Visão geral]  2 Sessões "))
        assertTrue(lines.first().plainText.startsWith("+- usage monitor -"))
        assertTrue(lines.last().plainText.matches(Regex("\\+-+\\+")))
        assertTrue(lines.all { line -> line.width == 96 })
    }

    @Test
    fun `rodape de atalhos tem a tecla em negrito e o rotulo em muted`() {
        val keys = frame(TuiSize(96, 20)).let { lines -> lines[lines.size - 2] }
        assertTrue(keys.plainText.startsWith("│ 1–5 aba  r atualizar  ? ajuda  q sair"))
        assertTrue(keys.spans.first { span -> span.text == "q" }.bold)
        assertEquals(TerminalColor.Muted, keys.spans.first { span -> span.text.contains("sair") }.color)
    }

    @Test
    fun `corpo maior que a tela e cortado na altura e na largura`() {
        val body = List(50) { index -> terminalLine(TerminalSpan("linha $index " + "x".repeat(200))) }
        val lines = frame(TuiSize(90, 16), body)
        assertEquals(16, lines.size)
        assertTrue(lines.all { line -> line.width == 90 })
        assertTrue(lines[3].plainText.startsWith("│ linha 0 "))
        assertTrue(lines[3].plainText.endsWith("x│"))
    }

    @Test
    fun `lista e detalhe lado a lado e empilhados abaixo de 80 colunas`() {
        val left = listOf(terminalLine(TerminalSpan("FONTES")))
        val right = listOf(terminalLine(TerminalSpan("detalhe")))
        val wide = TuiFrame.listDetail(left, right, TuiSize(96, 30), detailOpen = false, TerminalBoxGlyphs.ROUNDED)
        assertEquals("FONTES" + " ".repeat(15) + "│ detalhe", wide.single().plainText)
        val narrow = TuiSize(70, 30)
        assertEquals("FONTES", TuiFrame.listDetail(left, right, narrow, detailOpen = false, TerminalBoxGlyphs.ROUNDED).single().plainText)
        assertEquals("detalhe", TuiFrame.listDetail(left, right, narrow, detailOpen = true, TerminalBoxGlyphs.ROUNDED).single().plainText)
    }

    @Test
    fun `item selecionado leva a marca em ouro e o fundo ate uma celula antes do divisor`() {
        val item = TuiFrame.listItem(terminalLine(TerminalSpan("Claude Team")), selected = true, width = 21, TerminalGlyphs.UNICODE)
        assertEquals("▸ Claude Team" + " ".repeat(7), item.plainText)
        assertTrue(item.spans.all { span -> span.selected })
        assertEquals("  Claude Team", TuiFrame.listItem(terminalLine(TerminalSpan("Claude Team")), selected = false, width = 21, TerminalGlyphs.UNICODE).plainText)
    }

    @Test
    fun `a rolagem mantem a selecao a vista`() {
        assertEquals(0, TuiFrame.scrollStart(selected = 3, total = 5, visible = 10))
        assertEquals(0, TuiFrame.scrollStart(selected = 3, total = 20, visible = 10))
        assertEquals(6, TuiFrame.scrollStart(selected = 15, total = 20, visible = 10))
        assertEquals(10, TuiFrame.scrollStart(selected = 19, total = 20, visible = 10))
    }

    @Test
    fun `terminal pequeno demais mostra so a frase`() {
        val lines = TuiScreen.render(TuiState(), data(), TuiSize(30, 8), pt, color)
        assertEquals("O terminal tem 30x8; o modo interativo precisa de pelo menos 40x12.".take(30), lines.single().plainText)
    }

    @Test
    fun `status ao vivo so com coleta de pe e a ultima leitura certa`() {
        assertEquals("14:23 BRT · ao vivo", TuiScreen.status(data(), pt).text)
        assertEquals(TerminalColor.Muted, TuiScreen.status(data(), pt).color)
        val failed = TuiScreen.status(data(failedAt = now - 11.minutes), pt)
        assertEquals("leitura de 14:12", failed.text)
        assertEquals(TerminalColor.Warn, failed.color)
        assertEquals("leitura de 14:12", TuiScreen.status(data(collector = null), pt).text)
        assertEquals("reading from 14:12", TuiScreen.status(data(collector = null), en).text)
        assertEquals("sem leitura", TuiScreen.status(data(lastCollectedAt = null), pt).text)
    }

    @Test
    fun `a ajuda e uma caixa centrada com todos os atalhos`() {
        val lines = TuiScreen.render(TuiState(helpOpen = true), data(), TuiSize(96, 24), pt, color)
        val text = lines.joinToString("\n") { line -> line.plainText }
        assertTrue("╭─ Ajuda ─" in text)
        assertTrue("1 a 5    troca de aba        ↑ ↓      move a seleção" in text)
        assertTrue("q        sai                 espaço   liga e desliga" in text)
        assertTrue("esc fecha a ajuda  1–5 aba" in text)
        assertTrue(lines.all { line -> line.width == 96 })
        val boxTop = lines.first { line -> "Ajuda" in line.plainText }.plainText
        assertEquals("│ " + " ".repeat(15) + "╭─ Ajuda ", boxTop.take(26))
    }

    private fun data(
        collector: CollectorKind? = CollectorKind.SERVICE,
        lastCollectedAt: Instant? = now - 11.minutes,
        failedAt: Instant? = null
    ): TuiData {
        // A conta que falhou mostra os números da leitura anterior, e é dela que o status fala.
        val failing = failedAt?.let { fetchedAt ->
            TuiAccount(TerminalAccount(ApiSource.ANTHROPIC, "ANTHROPIC:work", "Anthropic", "trabalho", fetchedAt, listOf(70, 90), emptyList()), TuiReadFailure("limite da API", null, null))
        }
        return TuiData(now = now, collector = collector, lastCollectedAt = lastCollectedAt, accounts = listOfNotNull(failing))
    }
}
