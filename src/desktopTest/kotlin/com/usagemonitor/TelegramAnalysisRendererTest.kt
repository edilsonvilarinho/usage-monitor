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
}
