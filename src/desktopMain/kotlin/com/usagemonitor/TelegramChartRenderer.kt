package com.usagemonitor

import com.usagemonitor.presentation.ui.TelegramChart
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import kotlin.time.Instant

/**
 * PNG do `/grafico` (#398, direção Y6), desenhado com o Skia do próprio Compose
 * Desktop — sem dependência nova. Fundo e cores do tema escuro do app; o gráfico
 * vai para o Telegram, que não segue o tema do usuário.
 *
 * Eixo Y fixo de 0 a 100 (uso da cota), X do início ao fim do intervalo, linhas
 * tracejadas douradas nos reinícios. A legenda escreve rótulo **e** último
 * percentual: cor nunca informa sozinha.
 */
internal object TelegramChartRenderer {

    fun render(chart: TelegramChart, width: Int = WIDTH, height: Int = HEIGHT): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas
        canvas.clear(BACKGROUND)

        val legendRows = (chart.lines.size + 1) / 2
        val plot = Rect.makeLTRB(PAD_LEFT, PAD_TOP, width - PAD_RIGHT, height - PAD_BOTTOM - legendRows * LEGEND_ROW)
        val font = Font(typeface, 13f)
        val small = Font(typeface, 11f)

        // Grade a cada 25% com o valor escrito à esquerda.
        val grid = Paint().apply { color = GRID; strokeWidth = 1f; mode = PaintMode.STROKE }
        val label = Paint().apply { color = MUTED }
        for (step in 0..4) {
            val y = plot.bottom - plot.height * step / 4f
            canvas.drawLine(plot.left, y, plot.right, y, grid)
            canvas.drawString("${step * 25}%", 8f, y + 4f, small, label)
        }

        val span = (chart.endMillis - chart.startMillis).coerceAtLeast(1L).toFloat()
        fun x(millis: Long): Float = plot.left + plot.width * ((millis - chart.startMillis) / span)
        fun y(percent: Float): Float = plot.bottom - plot.height * (percent.coerceIn(0f, 100f) / 100f)

        // Três marcas de hora no eixo X: começo, meio e fim, em BRT.
        listOf(chart.startMillis, (chart.startMillis + chart.endMillis) / 2, chart.endMillis).forEachIndexed { index, millis ->
            val text = timeLabel(millis, chart.endMillis - chart.startMillis)
            val width = small.measureTextWidth(text)
            val anchor = when (index) {
                0 -> plot.left
                1 -> x(millis) - width / 2f
                else -> plot.right - width
            }
            canvas.drawString(text, anchor, plot.bottom + 16f, small, label)
        }

        val reset = Paint().apply {
            color = GOLD
            strokeWidth = 1f
            mode = PaintMode.STROKE
            pathEffect = PathEffect.makeDash(floatArrayOf(4f, 4f), 0f)
        }
        chart.lines.flatMap { line -> line.resets }.distinct().forEach { millis ->
            canvas.drawLine(x(millis), plot.top, x(millis), plot.bottom, reset)
        }

        chart.lines.forEachIndexed { index, line ->
            val stroke = Paint().apply {
                color = PALETTE[index % PALETTE.size]
                strokeWidth = 2.5f
                mode = PaintMode.STROKE
                isAntiAlias = true
            }
            // Segmento a segmento: ponto isolado (uma leitura só) vira um traço curto.
            if (line.points.size == 1) {
                val (millis, percent) = line.points.single()
                canvas.drawLine(x(millis) - 2f, y(percent), x(millis) + 2f, y(percent), stroke)
            }
            // O segmento que atravessa um reinício não é desenhado: ligar o fim de
            // uma janela ao começo da outra desenharia uma queda que ninguém consumiu.
            val resets = line.resets.toSet()
            line.points.zipWithNext().forEach { (from, to) ->
                if (to.first !in resets) canvas.drawLine(x(from.first), y(from.second), x(to.first), y(to.second), stroke)
            }
        }

        // Legenda em duas colunas embaixo do gráfico.
        val text = Paint().apply { color = FOREGROUND }
        chart.lines.forEachIndexed { index, line ->
            val column = index % 2
            val row = index / 2
            val left = PAD_LEFT + column * (width - PAD_LEFT - PAD_RIGHT) / 2f
            val top = plot.bottom + 34f + row * LEGEND_ROW
            canvas.drawRect(Rect.makeXYWH(left, top - 9f, 10f, 10f), Paint().apply { color = PALETTE[index % PALETTE.size] })
            canvas.drawString("${line.label}  ${line.lastPercent}%", left + 16f, top, font, text)
        }

        val image = surface.makeImageSnapshot()
        return image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    }

    private fun timeLabel(millis: Long, spanMillis: Long): String {
        val local = Instant.fromEpochMilliseconds(millis).toLocalDateTime(SAO_PAULO)
        val clock = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        if (spanMillis <= DAY_MILLIS) return clock
        return "${local.date.dayOfMonth.toString().padStart(2, '0')}/${local.date.monthNumber.toString().padStart(2, '0')} $clock"
    }

    /** Plex Mono do classpath, como o app; sem o recurso, a fonte padrão do sistema. */
    private val typeface: Typeface? by lazy {
        val bytes = TelegramChartRenderer::class.java.getResourceAsStream("/fonts/IBMPlexMono-Regular.ttf")?.use { stream -> stream.readBytes() }
        bytes?.let { data -> FontMgr.default.makeFromData(Data.makeFromBytes(data)) }
    }

    private const val WIDTH = 960
    private const val HEIGHT = 540
    private const val PAD_LEFT = 52f
    private const val PAD_RIGHT = 24f
    private const val PAD_TOP = 20f
    private const val PAD_BOTTOM = 32f
    private const val LEGEND_ROW = 22f
    private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    private const val BACKGROUND = 0xFF131010.toInt()
    private const val GRID = 0xFF2A2626.toInt()
    private const val MUTED = 0xFFB8B2B2.toInt()
    private const val FOREGROUND = 0xFFF2EDED.toInt()
    private const val GOLD = 0xFFE8AE63.toInt()

    /** Oito tons distintos sobre o fundo escuro; a ordem é a das linhas. */
    private val PALETTE = intArrayOf(
        0xFF4F8CFF.toInt(), 0xFF27BFA3.toInt(), 0xFFFFA726.toInt(), 0xFFE86A6A.toInt(),
        0xFFB388FF.toInt(), 0xFF66BB6A.toInt(), 0xFFF06292.toInt(), 0xFF4DD0E1.toInt()
    )
}
