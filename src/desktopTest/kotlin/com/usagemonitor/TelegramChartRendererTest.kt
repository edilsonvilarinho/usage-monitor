package com.usagemonitor

import com.usagemonitor.presentation.ui.TelegramChart
import com.usagemonitor.presentation.ui.TelegramChartLine
import com.usagemonitor.presentation.ui.TelegramHeatmap
import com.usagemonitor.presentation.ui.TelegramHeatmapCell
import com.usagemonitor.presentation.ui.TelegramHeatmapRow
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** O PNG do `/grafico` (#398, Y6): tamanho, fundo e a linha desenhada na cor dela. */
class TelegramChartRendererTest {

    @Test
    fun `chart renders a png with the background and the first line color`() {
        val start = 0L
        val end = 24 * 60 * 60 * 1_000L
        val chart = TelegramChart(
            startMillis = start,
            endMillis = end,
            lines = listOf(
                TelegramChartLine("Anthropic · Sessão 5h", listOf(start to 50f, end to 50f), resets = listOf(end / 2), lastPercent = 50)
            )
        )

        val png = TelegramChartRenderer.render(chart)
        val image = ImageIO.read(ByteArrayInputStream(png))

        assertEquals(960, image.width)
        assertEquals(540, image.height)
        assertEquals(0x131010, image.getRGB(2, 2) and 0xFFFFFF)
        // A linha horizontal a 50% cruza o meio do gráfico: algum pixel daquela
        // altura tem o azul da primeira cor da paleta.
        val blue = 0x4F8CFF
        val hasLine = (0 until image.height).any { y -> (100 until 800 step 50).any { x -> (image.getRGB(x, y) and 0xFFFFFF) == blue } }
        assertTrue(hasLine)
    }

    @Test
    fun `heatmap renders one row per model with the background color`() {
        val heatmap = TelegramHeatmap(
            columns = listOf("Tokens", "Custo"),
            rows = listOf(
                TelegramHeatmapRow("Anthropic · claude-opus-5-5", listOf(TelegramHeatmapCell("2,0 M", 1.0), TelegramHeatmapCell("—", null))),
                TelegramHeatmapRow("Codex · gpt-5-codex", listOf(TelegramHeatmapCell("1,0 M", 0.5), TelegramHeatmapCell("sem tarifa", null)))
            ),
            note = "Cor: valor sobre o maior da coluna."
        )

        val image = ImageIO.read(ByteArrayInputStream(TelegramHeatmapRenderer.render(heatmap)))

        assertEquals(960, image.width)
        // Margens, cabeçalho, duas linhas de 34 px e a nota.
        assertEquals((20 + 34 + 2 * 34 + 24 + 20), image.height)
        assertEquals(0x131010, image.getRGB(2, 2) and 0xFFFFFF)
    }
}
