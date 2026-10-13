package com.usagemonitor

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AnalysisWindowView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.TelegramAnalysisImageLabels
import com.usagemonitor.presentation.ui.TelegramAnalysisMessages
import com.usagemonitor.presentation.ui.TelegramBotMessages
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
import kotlin.time.Instant

/**
 * PNGs da análise da sessão (rodada AB), no mesmo Skia, fonte e cores escuras do `/grafico`
 * e do `/comparar`. Dado nunca anima nem passa do valor: o comprimento do arco é o número,
 * e o número está sempre escrito ao lado. `null` para vista que ainda não tem imagem.
 */
internal object TelegramAnalysisRenderer {

    fun render(view: AnalysisView, analysis: SessionAnalysis, language: AppLanguage): ByteArray? = when (view) {
        AnalysisView.GAUGES -> draw(WIDTH, 480) { canvas -> gauges(canvas, analysis, language) }
        AnalysisView.HOURS -> draw(WIDTH, CHART_HEIGHT) { canvas -> hours(canvas, analysis, language) }
        AnalysisView.HEATMAP -> draw(WIDTH, HEAT_HEIGHT) { canvas -> heatmap(canvas, analysis, language) }
        else -> null
    }

    /**
     * AB8 · mapa de calor: 7 dias × 24 horas (BRT), o dourado na proporção do maior valor da
     * semana; a célula forte tem o número escrito, para a cor não informar sozinha.
     */
    private fun heatmap(canvas: Canvas, analysis: SessionAnalysis, language: AppLanguage) {
        title(canvas, TelegramAnalysisImageLabels.title(AnalysisView.HEATMAP, analysis, language))
        val left = PAD + 52f
        val top = 64f
        val cellWidth = (WIDTH - left - PAD) / 24f
        val max = analysis.heat.flatten().maxOrNull()?.takeIf { value -> value > 0.0 } ?: 1.0
        val pt = language == AppLanguage.PT
        analysis.heat.forEachIndexed { row, values ->
            val y = top + row * HEAT_ROW
            val day = analysis.heatDays.getOrNull(row)?.let { date -> TelegramBotMessages.weekday(date.dayOfWeek, pt) }.orEmpty()
            canvas.drawString(day, left - 10f - font(15f).measureTextWidth(day), y + HEAT_ROW / 2f + 5f, font(15f), Paint().apply { color = MUTED })
            values.forEachIndexed { hour, value ->
                val x = left + hour * cellWidth
                val box = Rect.makeLTRB(x + 1.5f, y + 1.5f, x + cellWidth - 1.5f, y + HEAT_ROW - 1.5f)
                val color = if (value <= 0.0) EMPTY_CELL else blend(EMPTY_CELL, GOLD, 0.25 + 0.75 * value / max)
                canvas.drawRect(box, Paint().apply { this.color = color })
                if (value >= max * STRONG_CELL) centered(canvas, value.toInt().toString(), box.left + box.width / 2f, box.bottom - 13f, font(13f), CORE)
            }
        }
        listOf(0, 6, 12, 18, 23).forEach { hour ->
            centered(canvas, hour.toString().padStart(2, '0'), left + hour * cellWidth + cellWidth / 2f, top + 7 * HEAT_ROW + 22f, font(13f), MUTED)
        }
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

    /**
     * AB7 · ritmo por hora: barras de pontos da 5h em cada uma das 24 horas, as 4 mais novas em
     * ouro, e a vazão como linha azul no eixo da direita. Hora sem leitura não ganha barra.
     */
    private fun hours(canvas: Canvas, analysis: SessionAnalysis, language: AppLanguage) {
        title(canvas, TelegramAnalysisImageLabels.title(AnalysisView.HOURS, analysis, language))
        val area = Rect.makeLTRB(PAD + 40f, 70f, WIDTH - PAD - 46f, CHART_HEIGHT - 60f)
        val maxPoints = niceCeiling(analysis.hours.mapNotNull { bucket -> bucket.percentPoints }.maxOrNull() ?: 0.0)
        val maxSpeed = niceCeiling((analysis.hours.mapNotNull { bucket -> bucket.tokensPerSecond }.maxOrNull() ?: 0.0) * 1.2)
        grid(canvas, area, maxPoints, MUTED)
        val step = area.width / analysis.hours.size.coerceAtLeast(1)
        analysis.hours.forEachIndexed { index, bucket ->
            val left = area.left + index * step
            bucket.percentPoints?.let { points ->
                val top = area.bottom - (points / maxPoints).toFloat() * area.height
                canvas.drawRect(Rect.makeLTRB(left + 3f, top, left + step - 3f, area.bottom), Paint().apply { color = if (index >= analysis.hours.size - RECENT_HOURS) GOLD else EMBER })
            }
            if (index % 3 == 0) centered(canvas, hourOf(bucket.start),left + step / 2f, area.bottom + 22f, font(14f), MUTED)
        }
        var previous: Pair<Float, Float>? = null
        analysis.hours.forEachIndexed { index, bucket ->
            val speed = bucket.tokensPerSecond
            if (speed == null) {
                previous = null
                return@forEachIndexed
            }
            val point = (area.left + index * step + step / 2f) to (area.bottom - (speed / maxSpeed).toFloat() * area.height)
            previous?.let { (x, y) -> canvas.drawLine(x, y, point.first, point.second, stroke(INFO, 3f)) }
            canvas.drawCircle(point.first, point.second, 3.5f, Paint().apply { color = INFO; isAntiAlias = true })
            previous = point
        }
        canvas.drawString("${maxSpeed.toInt()}", area.right + 8f, area.top + 5f, font(13f), Paint().apply { color = INFO })
        canvas.drawString("0", area.right + 8f, area.bottom + 5f, font(13f), Paint().apply { color = INFO })
        val legend = TelegramAnalysisImageLabels.speedLegend(language)
        canvas.drawString(legend, area.right - font(14f).measureTextWidth(legend), CHART_HEIGHT - 12f, font(14f), Paint().apply { color = INFO })
    }

    /** A hora local (BRT), "HH", dos eixos. */
    private fun hourOf(instant: Instant): String = TelegramBotMessages.clock(instant).substring(0, 2)

    /** Linhas de 0, meio e topo com o valor escrito à esquerda. */
    private fun grid(canvas: Canvas, area: Rect, max: Double, color: Int) {
        listOf(0.0, max / 2, max).forEach { value ->
            val y = area.bottom - (value / max).toFloat() * area.height
            canvas.drawLine(area.left, y, area.right, y, Paint().apply { this.color = GRID; strokeWidth = 1f })
            val text = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
            canvas.drawString(text, area.left - 8f - font(13f).measureTextWidth(text), y + 5f, font(13f), Paint().apply { this.color = color })
        }
    }

    /** Topo redondo do eixo: o próximo múltiplo de 2, 5 ou 10 que cabe o maior valor; nunca zero. */
    private fun niceCeiling(value: Double): Double {
        if (value <= 0.0) return 1.0
        val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(value)))
        val step = listOf(1.0, 2.0, 5.0, 10.0).first { factor -> factor * magnitude >= value }
        return step * magnitude
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
    private const val CHART_HEIGHT = 420
    private const val PAD = 24f
    private const val RECENT_HOURS = 4
    private const val HEAT_ROW = 44f
    private const val HEAT_HEIGHT = 64 + 7 * 44 + 40

    /** A partir desta fração do maior valor a célula ganha o número escrito. */
    private const val STRONG_CELL = 0.7

    const val BACKGROUND = 0xFF131010.toInt()
    private const val TRACK = 0xFF2A2626.toInt()
    private const val MUTED = 0xFFB8B2B2.toInt()
    private const val FOREGROUND = 0xFFF2EDED.toInt()
    const val GOLD = 0xFFE8AE63.toInt()
    private const val HOT = 0xFFFFF0CB.toInt()
    const val CRITICAL = 0xFFE86A6A.toInt()
    const val INFO = 0xFF4C8DFF.toInt()
    const val EMBER = 0xFF8C643F.toInt()
    private const val GRID = 0xFF2A2626.toInt()
    const val EMPTY_CELL = 0xFF141B24.toInt()
    private const val CORE = 0xFF030508.toInt()
}
