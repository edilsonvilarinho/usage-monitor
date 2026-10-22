package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TerminalPrimitivesTest {

    private val anthropic = TerminalColor.Source(ApiSource.ANTHROPIC)
    private val pt = TerminalLabels(AppLanguage.PT)
    private val en = TerminalLabels(AppLanguage.EN)
    private val color = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val mono = TerminalProfile(TerminalPalette.NONE, unicode = false)

    private fun text(spans: List<TerminalSpan>) = spans.joinToString("") { span -> span.text }

    @Test
    fun `barra Unicode em oitavos, pelo piso, com a largura fixa`() {
        assertEquals("████████████▍       ".replace(' ', '░'), text(TerminalPrimitives.quotaBar(62, 20, anthropic, TerminalGlyphs.UNICODE)))
        assertEquals("██████████████████▏░", text(TerminalPrimitives.quotaBar(91, 20, anthropic, TerminalGlyphs.UNICODE)))
        assertEquals(20, text(TerminalPrimitives.quotaBar(37, 20, anthropic, TerminalGlyphs.UNICODE)).length)
    }

    @Test
    fun `barra nunca passa do numero nem de 100 por cento`() {
        assertEquals("░".repeat(12), text(TerminalPrimitives.quotaBar(0, 12, anthropic, TerminalGlyphs.UNICODE)))
        assertEquals("█".repeat(12), text(TerminalPrimitives.quotaBar(140, 12, anthropic, TerminalGlyphs.UNICODE)))
        assertEquals("░".repeat(12), text(TerminalPrimitives.quotaBar(-5, 12, anthropic, TerminalGlyphs.UNICODE)))
    }

    @Test
    fun `barra ASCII tem colchetes e a mesma largura total`() {
        val bar = text(TerminalPrimitives.quotaBar(91, 20, anthropic, TerminalGlyphs.ASCII))
        assertEquals("[################--]", bar)
        assertEquals(20, bar.length)
    }

    @Test
    fun `cor da barra e da fonte e o vazio fica em Dim`() {
        val spans = TerminalPrimitives.quotaBar(50, 10, anthropic, TerminalGlyphs.UNICODE)
        assertEquals(listOf(anthropic, TerminalColor.Dim), spans.map { span -> span.color })
    }

    @Test
    fun `palavra de estado na cor dela e critico em maiusculas sem cor`() {
        assertEquals(TerminalSpan("crítico", TerminalColor.Crit), TerminalPrimitives.zoneWord(QuotaZone.CRITICAL, pt, color))
        assertEquals("CRÍTICO", TerminalPrimitives.zoneWord(QuotaZone.CRITICAL, pt, mono).text)
        assertEquals("atenção", TerminalPrimitives.zoneWord(QuotaZone.ATTENTION, pt, mono).text)
        assertEquals("warning", TerminalPrimitives.zoneWord(QuotaZone.ATTENTION, en, color).text)
    }

    @Test
    fun `marca da fonte some no ASCII, menos na linha critica`() {
        assertEquals("●", TerminalPrimitives.sourceMark(anthropic, QuotaZone.ON_TRACK, TerminalGlyphs.UNICODE).text)
        assertEquals(" ", TerminalPrimitives.sourceMark(anthropic, QuotaZone.ON_TRACK, TerminalGlyphs.ASCII).text)
        assertEquals("*", TerminalPrimitives.sourceMark(anthropic, QuotaZone.CRITICAL, TerminalGlyphs.ASCII).text)
    }

    @Test
    fun `rodape conta os estados na ordem critico, atencao, em dia`() {
        val zones = listOf(QuotaZone.ON_TRACK, QuotaZone.CRITICAL, QuotaZone.ATTENTION, QuotaZone.ATTENTION, null, QuotaZone.ON_TRACK)
        val footer = TerminalPrimitives.readingFooter(CollectorKind.APP, 38.seconds, zones, pt)
        assertEquals("lido há 38 s pelo app aberto · 6 cotas: 1 crítica, 2 em atenção, 2 em dia", footer.plainText)
        assertEquals(TerminalColor.Crit, footer.spans.first { span -> span.text == "1 crítica" }.color)
    }

    @Test
    fun `rodape em ingles, pelo servico e sem estado com contagem zero`() {
        val footer = TerminalPrimitives.readingFooter(CollectorKind.SERVICE, 18.minutes, listOf(QuotaZone.ON_TRACK, QuotaZone.ON_TRACK), en)
        assertEquals("read 18 min ago by the service · 2 quotas: 2 on track", footer.plainText)
    }

    @Test
    fun `contagem regressiva sem arredondar para cima`() {
        assertEquals("47 min", TerminalLabels.countdown(47.minutes + 59.seconds))
        assertEquals("2h17", TerminalLabels.countdown(2.hours + 17.minutes))
        assertEquals("4d18h", TerminalLabels.countdown(4.days + 18.hours + 37.minutes))
        assertEquals("0 min", TerminalLabels.countdown((-3).minutes))
        assertEquals("em 47 min", pt.inDuration(47.minutes))
        assertEquals("in 1d06h", en.inDuration(1.days + 6.hours))
    }

    @Test
    fun `coluna de estado cabe a palavra mais longa`() {
        assertEquals(7, pt.zoneColumnWidth)
        assertEquals(8, en.zoneColumnWidth)
    }
}
