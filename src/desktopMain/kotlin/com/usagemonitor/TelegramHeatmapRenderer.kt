package com.usagemonitor

import com.usagemonitor.presentation.ui.TelegramHeatmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface

/**
 * PNG do `/comparar` (#408, AA6): o mapa de calor da comparação entre modelos, no
 * mesmo Skia e nas mesmas cores escuras do `/grafico` ([TelegramChartRenderer]).
 * A célula ganha dourado na proporção do valor sobre o maior da coluna e o número
 * está sempre escrito: cor nunca informa sozinha.
 */
internal object TelegramHeatmapRenderer {

    fun render(heatmap: TelegramHeatmap, width: Int = WIDTH): ByteArray {
        val height = (PAD + HEADER + heatmap.rows.size * ROW + NOTE + PAD).toInt()
        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas
        canvas.clear(BACKGROUND)
        val font = Font(typeface, 13f)
        val small = Font(typeface, 11f)
        val text = Paint().apply { color = FOREGROUND }
        val muted = Paint().apply { color = MUTED }
        val columnWidth = (width - PAD * 2 - LABEL_WIDTH) / heatmap.columns.size.coerceAtLeast(1)

        heatmap.columns.forEachIndexed { index, column ->
            val right = PAD + LABEL_WIDTH + columnWidth * (index + 1) - CELL_INSET
            canvas.drawString(column, right - small.measureTextWidth(column), PAD + HEADER - 12f, small, muted)
        }
        heatmap.rows.forEachIndexed { rowIndex, row ->
            val top = PAD + HEADER + rowIndex * ROW
            canvas.drawString(fit(row.label, font, LABEL_WIDTH - CELL_INSET), PAD, top + ROW / 2f + 5f, font, text)
            row.cells.forEachIndexed { index, cell ->
                val left = PAD + LABEL_WIDTH + columnWidth * index
                val box = Rect.makeLTRB(left + 2f, top + 2f, left + columnWidth - 2f, top + ROW - 2f)
                canvas.drawRect(box, Paint().apply { color = blend(SURFACE, GOLD, (cell.intensity ?: 0.0) * MAX_TINT) })
                canvas.drawString(cell.text, box.right - CELL_INSET - font.measureTextWidth(cell.text), top + ROW / 2f + 5f, font, text)
            }
        }
        canvas.drawString(heatmap.note, PAD, height - PAD - 4f, small, muted)
        val image = surface.makeImageSnapshot()
        return image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    }

    /** Corta o rótulo com "…" para caber na coluna; o modelo inteiro está na mensagem do app. */
    private fun fit(label: String, font: Font, maxWidth: Float): String {
        if (font.measureTextWidth(label) <= maxWidth) return label
        var cut = label
        while (cut.isNotEmpty() && font.measureTextWidth("$cut…") > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }

    private fun blend(from: Int, to: Int, amount: Double): Int {
        val t = amount.coerceIn(0.0, 1.0)
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt() and 0xFF
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private val typeface: Typeface? by lazy {
        val bytes = TelegramHeatmapRenderer::class.java.getResourceAsStream("/fonts/IBMPlexMono-Regular.ttf")?.use { stream -> stream.readBytes() }
        bytes?.let { data -> FontMgr.default.makeFromData(Data.makeFromBytes(data)) }
    }

    private const val WIDTH = 960
    private const val PAD = 20f
    private const val HEADER = 34f
    private const val ROW = 34f
    private const val NOTE = 24f
    private const val LABEL_WIDTH = 300f
    private const val CELL_INSET = 8f

    /** Teto do dourado: acima disso o texto claro perde contraste sobre a célula. */
    private const val MAX_TINT = 0.55

    private const val BACKGROUND = 0xFF131010.toInt()
    private const val SURFACE = 0xFF1B1818.toInt()
    private const val MUTED = 0xFFB8B2B2.toInt()
    private const val FOREGROUND = 0xFFF2EDED.toInt()
    private const val GOLD = 0xFFE8AE63.toInt()
}
