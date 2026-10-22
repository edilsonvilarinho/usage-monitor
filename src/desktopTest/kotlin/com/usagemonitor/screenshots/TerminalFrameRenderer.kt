package com.usagemonitor.screenshots

import com.usagemonitor.presentation.terminal.TerminalColors
import com.usagemonitor.presentation.terminal.TerminalLine
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Desenha as linhas do modo terminal ([TerminalLine]) numa imagem, célula a célula, com as
 * cores do [TerminalColors] na paleta truecolor escura e o fundo e o texto da prévia do
 * design system (`tokens/terminal.css`). A fonte é a Plex Mono do app; blocos, molduras e a
 * sparkline são desenhados à mão, para encostarem na célula vizinha como num terminal.
 */
internal class TerminalFrameRenderer(private val columns: Int, private val rows: Int) {
    private val regular = plex("IBMPlexMono-Regular.ttf")
    private val bold = plex("IBMPlexMono-SemiBold.ttf")
    private val fallback = Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE.toInt())
    private val cellWidth: Int
    private val cellHeight: Int
    private val ascent: Int

    init {
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics()
        val metrics = probe.getFontMetrics(regular)
        cellWidth = metrics.charWidth('M')
        cellHeight = metrics.height + 2
        ascent = metrics.ascent + 1
        probe.dispose()
    }

    fun render(lines: List<TerminalLine>): BufferedImage {
        val image = BufferedImage(columns * cellWidth + 2 * PADDING, rows * cellHeight + 2 * PADDING, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        graphics.color = Color(BACKGROUND)
        graphics.fillRect(0, 0, image.width, image.height)
        lines.take(rows).forEachIndexed { row, line ->
            var column = 0
            for (span in line.spans) {
                val foreground = Color(span.color?.let { color -> TerminalColors.hex(color, light = false) } ?: FOREGROUND)
                for (char in span.text) {
                    if (column >= columns) break
                    val left = PADDING + column * cellWidth
                    val top = PADDING + row * cellHeight
                    if (span.selected) {
                        graphics.color = Color(TerminalColors.SELECTION_DARK)
                        graphics.fillRect(left, top, cellWidth, cellHeight)
                    }
                    drawCell(graphics, char, left, top, foreground, span.bold)
                    column += 1
                }
            }
        }
        graphics.dispose()
        return image
    }

    private fun drawCell(graphics: Graphics2D, char: Char, left: Int, top: Int, color: Color, isBold: Boolean) {
        graphics.color = color
        val blockEighths = BLOCKS.indexOf(char)
        val sparkLevel = SPARK.indexOf(char)
        when {
            char == ' ' -> Unit
            blockEighths >= 0 -> graphics.fillRect(left, top + 3, cellWidth * (blockEighths + 1) / 8, cellHeight - 6)
            char == '░' -> {
                graphics.color = blend(color, Color(BACKGROUND), 0.22f)
                graphics.fillRect(left, top + 3, cellWidth, cellHeight - 6)
            }
            sparkLevel >= 0 -> {
                val height = (cellHeight - 6) * (sparkLevel + 1) / 8
                graphics.fillRect(left, top + cellHeight - 3 - height, cellWidth, height)
            }
            char in BOX -> drawBox(graphics, char, left, top)
            // O `✓` das fontes à mão lembra uma raiz quadrada; o desenhado tem o traço de um terminal comum.
            char == '✓' -> {
                graphics.stroke = BasicStroke(1.6f)
                val middle = top + cellHeight / 2
                graphics.drawLine(left + 1, middle, left + cellWidth / 3, middle + cellHeight / 4)
                graphics.drawLine(left + cellWidth / 3, middle + cellHeight / 4, left + cellWidth - 1, top + cellHeight / 4)
            }
            else -> {
                val font = if (isBold) bold else regular
                graphics.font = if (font.canDisplay(char)) font else fallback
                graphics.drawString(char.toString(), left, top + ascent)
            }
        }
    }

    private fun drawBox(graphics: Graphics2D, char: Char, left: Int, top: Int) {
        graphics.stroke = BasicStroke(1f)
        val centerX = left + cellWidth / 2
        val centerY = top + cellHeight / 2
        val right = left + cellWidth - 1
        val bottom = top + cellHeight - 1
        if (char in "─├┤┬┴╭╮╰╯") {
            graphics.drawLine(if (char in "├╭╰") centerX else left, centerY, if (char in "┤╮╯") centerX else right, centerY)
        }
        if (char in "│├┤┬┴╭╮╰╯┆") {
            val from = if (char in "┬╭╮") centerY else top
            val to = if (char in "┴╰╯") centerY else bottom
            if (char == '┆') {
                var y = top
                while (y < bottom) {
                    graphics.drawLine(centerX, y, centerX, minOf(y + 2, bottom))
                    y += 5
                }
            } else {
                graphics.drawLine(centerX, from, centerX, to)
            }
        }
    }

    private fun blend(a: Color, b: Color, amount: Float): Color =
        Color(
            (a.red * amount + b.red * (1 - amount)).toInt(),
            (a.green * amount + b.green * (1 - amount)).toInt(),
            (a.blue * amount + b.blue * (1 - amount)).toInt()
        )

    private fun plex(name: String): Font {
        val stream = TerminalFrameRenderer::class.java.getResourceAsStream("/fonts/$name") ?: error("fonte $name fora do classpath")
        return stream.use { input -> Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(FONT_SIZE) }
    }

    private companion object {
        const val FONT_SIZE = 14f
        const val PADDING = 16
        const val BACKGROUND = 0x131010
        const val FOREGROUND = 0xF2EDED
        const val BLOCKS = "▏▎▍▌▋▊▉█"
        const val SPARK = "▁▂▃▄▅▆▇"
        const val BOX = "─│╭╮╰╯├┤┬┴┆"
    }
}
