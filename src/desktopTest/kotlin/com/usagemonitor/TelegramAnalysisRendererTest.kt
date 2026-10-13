package com.usagemonitor

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.AnalysisFixture
import com.usagemonitor.presentation.ui.TelegramAnalysisImageLabels
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Os PNGs da análise da sessão (rodada AB): tamanho, fundo e a cor do dado desenhado. */
class TelegramAnalysisRendererTest {

    private fun image(view: AnalysisView): BufferedImage {
        val png = TelegramAnalysisRenderer.render(view, AnalysisFixture.analysis, AppLanguage.PT)!!
        return ImageIO.read(ByteArrayInputStream(png))
    }

    private fun hasColor(image: BufferedImage, rgb: Int, area: IntRange = 0 until image.height): Boolean =
        area.any { y -> (0 until image.width).any { x -> (image.getRGB(x, y) and 0xFFFFFF) == rgb } }

    /** AB6: ritmo 14 abaixo do seguro 25,3 — o arco da 5h é dourado, nunca crítico. */
    @Test
    fun `gauges draw the pace arc in gold below the safe pace`() {
        val image = image(AnalysisView.GAUGES)
        assertEquals(TelegramAnalysisRenderer.WIDTH, image.width)
        assertEquals(TelegramAnalysisRenderer.BACKGROUND and 0xFFFFFF, image.getRGB(2, 2) and 0xFFFFFF)
        assertTrue(hasColor(image, TelegramAnalysisRenderer.GOLD and 0xFFFFFF))
        assertTrue(!hasColor(image, TelegramAnalysisRenderer.CRITICAL and 0xFFFFFF))
        val (label, safe) = TelegramAnalysisImageLabels.gaugeLabel("5h", 62, 25.3, AppLanguage.EN)
        assertEquals("5h · 62%" to "safe up to 25.3", label to safe)
    }

    /** AB7: barras das horas recentes em ouro, as antigas em brasa, e a linha azul da vazão. */
    @Test
    fun `hours draw recent bars in gold, older ones in ember and the throughput line in blue`() {
        val image = image(AnalysisView.HOURS)
        assertEquals(420, image.height)
        assertTrue(hasColor(image, TelegramAnalysisRenderer.GOLD and 0xFFFFFF))
        assertTrue(hasColor(image, TelegramAnalysisRenderer.EMBER and 0xFFFFFF))
        assertTrue(hasColor(image, TelegramAnalysisRenderer.INFO and 0xFFFFFF, 70 until 360))
    }

    /** AB8: 7 linhas × 24 colunas; hora parada fica na cor vazia, a mais forte em ouro cheio. */
    @Test
    fun `heatmap leaves idle hours empty and paints the strongest in full gold`() {
        val image = image(AnalysisView.HEATMAP)
        assertEquals(64 + 7 * 44 + 40, image.height)
        assertTrue(hasColor(image, TelegramAnalysisRenderer.EMPTY_CELL and 0xFFFFFF))
        assertTrue(hasColor(image, TelegramAnalysisRenderer.GOLD and 0xFFFFFF))
    }

    /** AB9: curva e projeção em ouro; sem janela aberta, a imagem diz "sem leitura" em vez de inventar curva. */
    @Test
    fun `curve draws the window in gold and nothing without an open window`() {
        assertTrue(hasColor(image(AnalysisView.CURVE), TelegramAnalysisRenderer.GOLD and 0xFFFFFF))
        val closed = AnalysisFixture.analysis.copy(fiveHour = null)
        val png = TelegramAnalysisRenderer.render(AnalysisView.CURVE, closed, AppLanguage.PT)!!
        assertTrue(!hasColor(ImageIO.read(ByteArrayInputStream(png)), TelegramAnalysisRenderer.GOLD and 0xFFFFFF))
        assertEquals("≈83% at reset", TelegramAnalysisImageLabels.curveLabels(62, 83, AppLanguage.EN).atReset)
    }

    /** AB10: hoje em ouro, os outros dias em brasa; a média sai no idioma do app. */
    @Test
    fun `days paint today in gold and the other days in ember`() {
        val image = image(AnalysisView.DAYS)
        assertTrue(hasColor(image, TelegramAnalysisRenderer.GOLD and 0xFFFFFF))
        assertTrue(hasColor(image, TelegramAnalysisRenderer.EMBER and 0xFFFFFF))
        assertEquals("--- average 5.9 pp", TelegramAnalysisImageLabels.average(5.857, AppLanguage.EN))
    }

    /** AB11: seis blocos; ritmo abaixo da janela anterior sai verde, a vazão azul. */
    @Test
    fun `card shows six tiles and colors the pace drop in green`() {
        val tiles = TelegramAnalysisImageLabels.cardTiles(AnalysisFixture.analysis, AppLanguage.PT)
        assertEquals(listOf("62%", "14,0 pp/h", "41%", "48 tok/s", "↓ 13%", "15h"), tiles.map { tile -> tile.big })
        assertEquals("→ ≈83% no reinício", tiles.first().detail)
        val image = image(AnalysisView.CARD)
        assertTrue(hasColor(image, TelegramAnalysisRenderer.OK and 0xFFFFFF))
        assertTrue(hasColor(image, TelegramAnalysisRenderer.INFO and 0xFFFFFF))
        assertEquals("ter 06/10 · 12:30", TelegramAnalysisImageLabels.cardHeader(AnalysisFixture.analysis, AppLanguage.PT).second)
    }
}
