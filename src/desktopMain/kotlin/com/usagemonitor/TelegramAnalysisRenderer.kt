package com.usagemonitor

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.TelegramAnalysisImageLabels
import com.usagemonitor.presentation.ui.TelegramAnalysisMessages
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * PNGs da análise da sessão (rodada AB), no mesmo Skia, fonte e cores escuras do `/grafico`
 * e do `/comparar`. Dado nunca anima nem passa do valor: o comprimento do arco é o número,
 * e o número está sempre escrito ao lado. `null` para vista que ainda não tem imagem.
 */
internal object TelegramAnalysisRenderer {

    fun render(view: AnalysisView, analysis: SessionAnalysis, language: AppLanguage): ByteArray? = when (view) {
        AnalysisView.GAUGES -> draw(WIDTH, 480) { canvas -> gauges(canvas, analysis, language) }
        else -> null
    }

    /** AB6 · velocímetros: o arco é o ritmo agora; a marca clara, o ritmo seguro; acima dela, o arco fica crítico. */
    private fun gauges(canvas: Canvas, analysis: SessionAnalysis, language: AppLanguage) {
        title(canvas, TelegramAnalysisImageLabels.title(AnalysisView.GAUGES, analysis, language))
        gauge(canvas, WIDTH * 0.27f, 270f, analysis.fiveHour, TelegramAnalysisImageLabels.window5h(language), language)
        gauge(canvas, WIDTH * 0.73f, 270f, analysis.weekly, TelegramAnalysisImageLabels.windowWeekly(language), language)
        footer(canvas, 480, TelegramAnalysisImageLabels.speeds(analysis, language), INFO)
    }

    private fun gauge(canvas: Canvas, cx: Float, cy: Float, window: AnalysisWindowView?, name: String, language: AppLanguage) {
        val radius = 150f
        val oval = Rect.makeLTRB(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(oval.left, oval.top, oval.right, oval.bottom, 180f, 180f, false, stroke(TRACK, 26f))
        if (window == null) {
            centered(canvas, TelegramAnalysisImageLabels.notMeasured(language), cx, cy - 20f, font(22f), MUTED)
            return
        }
        val pace = window.paceNow ?: window.paceAverage
        val safe = window.safePace
        val max = listOfNotNull(pace, safe).maxOrNull()?.times(GAUGE_HEADROOM)?.takeIf { value -> value > 0.0 } ?: 1.0
        if (pace != null) {
            val sweep = (pace / max).coerceIn(0.0, 1.0).toFloat() * 180f
            canvas.drawArc(oval.left, oval.top, oval.right, oval.bottom, 180f, sweep, false, stroke(if (safe != null && pace > safe) CRITICAL else GOLD, 26f))
        }
        if (safe != null) {
            val angle = Math.toRadians(180.0 + (safe / max).coerceIn(0.0, 1.0) * 180.0)
            val inner = radius - 22f
            val outer = radius + 24f
            canvas.drawLine(
                cx + (inner * cos(angle)).toFloat(), cy + (inner * sin(angle)).toFloat(),
                cx + (outer * cos(angle)).toFloat(), cy + (outer * sin(angle)).toFloat(),
                stroke(HOT, 5f)
            )
        }
        centered(canvas, TelegramAnalysisMessages.pace(pace, language == AppLanguage.PT).trim(), cx, cy - 26f, font(52f), HOT)
        centered(canvas, "pp/h", cx, cy + 4f, font(18f), MUTED)
        val (label, safeText) = TelegramAnalysisImageLabels.gaugeLabel(name, window.percent, safe, language)
        centered(canvas, label, cx, cy + 56f, font(24f), FOREGROUND)
        centered(canvas, safeText, cx, cy + 86f, font(18f), MUTED)
    }

    // ---- desenho comum ------------------------------------------------------------------

    private fun draw(width: Int, height: Int, body: (Canvas) -> Unit): ByteArray {
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.clear(BACKGROUND)
        body(surface.canvas)
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)
    }

    private fun title(canvas: Canvas, text: String) {
        canvas.drawString(text, PAD, PAD + 18f, font(20f), Paint().apply { color = FOREGROUND })
    }

    private fun footer(canvas: Canvas, height: Int, text: String, color: Int) {
        canvas.drawString(text, PAD, height - PAD, font(16f), Paint().apply { this.color = color })
    }

    private fun centered(canvas: Canvas, text: String, cx: Float, baseline: Float, font: Font, color: Int) {
        canvas.drawString(text, cx - font.measureTextWidth(text) / 2f, baseline, font, Paint().apply { this.color = color })
    }

    private fun stroke(color: Int, width: Float) = Paint().apply {
        this.color = color
        mode = PaintMode.STROKE
        strokeWidth = width
        strokeCap = PaintStrokeCap.ROUND
        isAntiAlias = true
    }

    private fun font(size: Float) = Font(typeface, size)

    private val typeface: Typeface? by lazy {
        val bytes = TelegramAnalysisRenderer::class.java.getResourceAsStream("/fonts/IBMPlexMono-Regular.ttf")?.use { stream -> stream.readBytes() }
        bytes?.let { data -> FontMgr.default.makeFromData(Data.makeFromBytes(data)) }
    }

    /** Folga do mostrador sobre o maior entre o ritmo e o seguro: nenhum dos dois encosta no fim. */
    private const val GAUGE_HEADROOM = 1.6

    const val WIDTH = 960
    private const val PAD = 24f

    const val BACKGROUND = 0xFF131010.toInt()
    private const val TRACK = 0xFF2A2626.toInt()
    private const val MUTED = 0xFFB8B2B2.toInt()
    private const val FOREGROUND = 0xFFF2EDED.toInt()
    const val GOLD = 0xFFE8AE63.toInt()
    private const val HOT = 0xFFFFF0CB.toInt()
    const val CRITICAL = 0xFFE86A6A.toInt()
    private const val INFO = 0xFF4C8DFF.toInt()
}
