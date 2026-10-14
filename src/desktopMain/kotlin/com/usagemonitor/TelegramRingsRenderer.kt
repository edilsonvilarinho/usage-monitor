package com.usagemonitor

import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.presentation.ui.TelegramRings
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface

/**
 * PNG do `/status img` (#408, AA14): um anel por conta no vocabulário Gargantua da
 * HUD — núcleo escuro, brilho dourado discreto, arcos de cota por cima da trilha —
 * no mesmo Skia do `/grafico`. Desenho estático: o Telegram não anima, e o arco
 * nunca passa do percentual escrito no centro.
 */
internal object TelegramRingsRenderer {

    fun render(rings: TelegramRings, width: Int = WIDTH): ByteArray {
        val columns = rings.rings.size.coerceIn(1, MAX_COLUMNS)
        val rows = (rings.rings.size + columns - 1) / columns
        val height = (PAD * 2 + rows.coerceAtLeast(1) * CELL_HEIGHT).toInt()
        val surface = Surface.makeRasterN32Premul(width, height)
        val canvas = surface.canvas
        canvas.clear(BACKGROUND)
        val cellWidth = (width - PAD * 2) / columns
        val center = Font(typeface, 22f)
        val label = Font(typeface, 13f)
        val small = Font(typeface, 11f)
        val text = Paint().apply { color = FOREGROUND; isAntiAlias = true }
        val muted = Paint().apply { color = MUTED; isAntiAlias = true }

        rings.rings.forEachIndexed { index, ring ->
            val cx = PAD + cellWidth * (index % columns) + cellWidth / 2f
            val cy = PAD + CELL_HEIGHT * (index / columns) + RADIUS + 16f
            // Brilho do disco: um halo dourado fraco atrás do anel.
            canvas.drawCircle(cx, cy, RADIUS + 10f, Paint().apply { color = GLOW; isAntiAlias = true })
            ring.arcs.forEachIndexed { arcIndex, arc ->
                val r = RADIUS - arcIndex * ARC_STEP
                val track = Paint().apply { color = TRACK; mode = PaintMode.STROKE; strokeWidth = ARC_WIDTH; isAntiAlias = true }
                canvas.drawCircle(cx, cy, r, track)
                val sweep = 360f * arc.percent.coerceIn(0, 100) / 100f
                if (sweep > 0f) {
                    val stroke = Paint().apply {
                        color = riskColor(arc.risk)
                        mode = PaintMode.STROKE
                        strokeWidth = ARC_WIDTH
                        strokeCap = PaintStrokeCap.ROUND
                        isAntiAlias = true
                    }
                    canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90f, sweep, false, stroke)
                }
            }
            val coreRadius = RADIUS - (ring.arcs.size - 1).coerceAtLeast(0) * ARC_STEP - ARC_WIDTH
            canvas.drawCircle(cx, cy, coreRadius, Paint().apply { color = CORE; isAntiAlias = true })
            val percent = "${ring.centerPercent}%"
            canvas.drawString(percent, cx - center.measureTextWidth(percent) / 2f, cy + 8f, center, Paint().apply { color = HOT; isAntiAlias = true })
            val name = fit(ring.label, label, cellWidth - 12f)
            canvas.drawString(name, cx - label.measureTextWidth(name) / 2f, cy + RADIUS + 30f, label, text)
            ring.riskWord?.let { word ->
                canvas.drawString(word, cx - small.measureTextWidth(word) / 2f, cy + RADIUS + 48f, small, muted)
            }
        }
        val image = surface.makeImageSnapshot()
        return image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    }

    private fun riskColor(risk: UsageRiskLevel?): Int = when (risk) {
        UsageRiskLevel.ON_TRACK -> OK
        UsageRiskLevel.AT_RISK -> WARN
        UsageRiskLevel.WILL_EXCEED -> CRIT
        null -> NEUTRAL
    }

    private fun fit(text: String, font: Font, maxWidth: Float): String {
        if (font.measureTextWidth(text) <= maxWidth) return text
        var cut = text
        while (cut.isNotEmpty() && font.measureTextWidth("$cut…") > maxWidth) cut = cut.dropLast(1)
        return "$cut…"
    }

    private val typeface: Typeface? by lazy {
        val bytes = TelegramRingsRenderer::class.java.getResourceAsStream("/fonts/IBMPlexMono-Regular.ttf")?.use { stream -> stream.readBytes() }
        bytes?.let { data -> FontMgr.default.makeFromData(Data.makeFromBytes(data)) }
    }

    private const val WIDTH = 960
    private const val MAX_COLUMNS = 4
    private const val PAD = 24f
    private const val CELL_HEIGHT = 200f
    private const val RADIUS = 64f
    private const val ARC_WIDTH = 7f
    private const val ARC_STEP = 11f

    private const val BACKGROUND = 0xFF131010.toInt()
    private const val TRACK = 0xFF2A2626.toInt()
    private const val CORE = 0xFF030508.toInt()
    private const val GLOW = 0x22E8AE63
    private const val HOT = 0xFFFFF0CB.toInt()
    private const val MUTED = 0xFFB8B2B2.toInt()
    private const val FOREGROUND = 0xFFF2EDED.toInt()
    private const val OK = 0xFF4CAF50.toInt()
    private const val WARN = 0xFFFFA726.toInt()
    private const val CRIT = 0xFFE86A6A.toInt()
    private const val NEUTRAL = 0xFF8A8484.toInt()
}
