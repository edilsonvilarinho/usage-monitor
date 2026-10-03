package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens as Space
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Filamentos que atravessam o disco; poucos, para o cenário não disputar com o dado. */
private const val DISK_STREAKS = 3

/**
 * Horizonte, anel de fótons, lente fina e um disco translúcido: camadas
 * geométricas, sem bitmap, blur de GPU ou partículas aleatórias. O cenário é
 * discreto de propósito — quem informa são os arcos e o cometa de sessão.
 * A fase é determinística para captura; tudo cabe em `room`.
 */
internal fun DrawScope.drawGargantuaCore(room: Float, phase: Float, scene: GargantuaScene = GargantuaScene.Dark) {
    if (room <= 0f) return
    val horizon = room * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            0f to Space.gold.copy(alpha = 0.16f),
            0.56f to Space.gold.copy(alpha = 0.16f),
            1f to Color.Transparent,
            center = center, radius = room
        ),
        radius = room
    )
    drawGargantuaLens(room, horizon, scene.lens)
    drawAccretionDisk(room, phase, front = false, scene)
    // O horizonte oculta o lado de trás do disco. A marca é composta depois.
    drawCircle(Space.core, radius = horizon)
    drawCircle(Space.hot.copy(alpha = 0.8f), horizon + room * 0.01f, style = Stroke(room * 0.022f))
    drawAccretionDisk(room, phase, front = true, scene)
}

/** Anel de Einstein fino: o lado de trás do disco dobrado por cima e por baixo. */
private fun DrawScope.drawGargantuaLens(room: Float, horizon: Float, light: Color) {
    val upper = horizon + room * 0.10f
    drawArc(
        light.copy(alpha = 0.35f), 194f, 152f, false,
        center - Offset(upper, upper), Size(upper * 2, upper * 2),
        style = Stroke(room * 0.05f)
    )
    val lower = horizon + room * 0.08f
    drawArc(
        light.copy(alpha = 0.18f), 27f, 126f, false,
        center - Offset(lower, lower), Size(lower * 2, lower * 2),
        style = Stroke(room * 0.025f)
    )
}

/**
 * Uma faixa translúcida no plano do disco, mais clara no lado que se aproxima
 * (Doppler). Velocidades inteiras por ciclo mantêm o laço sem salto.
 */
private fun DrawScope.drawAccretionDisk(room: Float, phase: Float, front: Boolean, scene: GargantuaScene) {
    rotate(Space.diskTilt, pivot = center) {
        val diskCenter = center + Offset(0f, room * 0.08f)
        val radiusX = room * 0.95f
        val radiusY = radiusX * 0.16f
        val bounds = Size(radiusX * 2, radiusY * 2)
        val origin = diskCenter - Offset(radiusX, radiusY)
        drawArc(
            brush = Brush.horizontalGradient(
                0f to scene.diskNear,
                0.5f to scene.diskMiddle,
                1f to scene.diskFar,
                startX = origin.x, endX = origin.x + bounds.width
            ),
            startAngle = if (front) 0f else 180f, sweepAngle = 180f, useCenter = false,
            topLeft = origin, size = bounds,
            style = Stroke(room * 0.07f)
        )
        for (streak in 0 until DISK_STREAKS) {
            val turns = 1 + streak % 2
            val angle = (phase * 360f * turns + streak * 120f) % 360f
            if (front != angle < 180f) continue
            // Some ao atravessar a borda, evitando um salto entre as metades.
            val edge = abs(sin(angle * PI / 180).toFloat())
            val visibleSweep = minOf(20f, if (front) 180f - angle else 360f - angle)
            drawArc(
                scene.streak.copy(alpha = scene.streak.alpha * edge),
                angle, visibleSweep, false, origin, bounds,
                style = Stroke(room * 0.035f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Luz do nascimento e do colapso: clarão central e duas ondas de choque. As
 * ondas param 3dp além do anel (proporção do tamanho), dentro do respiro que o
 * notch já reserva para a órbita de sessão.
 */
internal fun DrawScope.drawGargantuaTransitionLight(frame: GargantuaFrame) {
    val ring = size.minDimension / 2f
    if (frame.flash > 0f) {
        val radius = ring * frame.flashRadius
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = frame.flash),
                0.3f to Space.hot.copy(alpha = frame.flash * 0.7f),
                1f to Color.Transparent,
                center = center, radius = radius
            ),
            radius = radius
        )
    }
    drawShock(frame.nearShock, ring * 1.1f, ring * 0.09f, Color(0xFFFFE6BE))
    drawShock(frame.farShock, ring * 0.95f, ring * 0.06f, Space.gold)
}

/**
 * R1 · ondas gravitacionais. Coletando, três ondas finas defasadas saem do anel
 * ([ripple] é a fase, `null` fora da coleta); ao concluir, uma onda mais forte
 * ([completion] em `0..1`). Ficam no respiro que a órbita de sessão já reserva.
 */
internal fun DrawScope.drawGargantuaRefreshLight(ripple: Float?, completion: Float) {
    val ring = size.minDimension / 2f
    if (ripple != null) {
        for (wave in 0 until REFRESH_RIPPLES) {
            drawShock((ripple + wave / REFRESH_RIPPLES.toFloat()) % 1f, ring * 1.06f, ring * 0.034f, Color(0xFFFFE6BE))
        }
    }
    drawShock(completion, ring * 1.16f, ring * 0.0625f, Space.hot)
}

private const val REFRESH_RIPPLES = 3

/**
 * B3 · o feixe do jato relativístico, de [from] (centro do anel) até [to]:
 * branco na origem, quente no meio e dourado sumindo na ponta, com um halo largo
 * e fraco no lugar do blur (sem `BlurEffect`) e um clarão pequeno na origem.
 */
internal fun DrawScope.drawGargantuaJet(from: Offset, to: Offset, alpha: Float, width: Float, flashRadius: Float) {
    if (alpha <= 0f || from == to) return
    val beam = Brush.linearGradient(
        0f to Color.White.copy(alpha = alpha),
        0.3f to Space.hot.copy(alpha = alpha * 0.9f),
        1f to Space.gold.copy(alpha = 0f),
        start = from, end = to
    )
    val halo = Brush.linearGradient(
        0f to Space.gold.copy(alpha = alpha * 0.35f),
        1f to Space.gold.copy(alpha = 0f),
        start = from, end = to
    )
    drawLine(halo, from, to, strokeWidth = width * 4f, cap = StrokeCap.Round)
    drawLine(beam, from, to, strokeWidth = width, cap = StrokeCap.Round)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = alpha * 0.6f),
            0.3f to Space.hot.copy(alpha = alpha * 0.42f),
            1f to Color.Transparent,
            center = from, radius = flashRadius
        ),
        radius = flashRadius,
        center = from
    )
}

private fun DrawScope.drawShock(progress: Float, reach: Float, width: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val grown = 1f - (1f - progress) * (1f - progress) * (1f - progress)
    drawCircle(
        color.copy(alpha = (1f - progress) * 0.8f),
        radius = reach * grown,
        style = Stroke(width * (1f - progress) + width * 0.1f)
    )
}

/** Mesma margem externa contratada por appUsageRingOrbitReach (5,3dp na HUD). */
internal fun DrawScope.drawGargantuaActivity(phase: Float, stroke: Float, gap: Float, color: Color) {
    val orbitStroke = stroke * 0.8f
    val outset = gap + orbitStroke / 2f
    val radius = size.minDimension / 2f + outset
    drawCircle(color.copy(alpha = 0.14f), radius, style = Stroke(orbitStroke * 0.5f))
    rotate(phase * 360f - 90f) {
        drawArc(
            brush = Brush.sweepGradient(
                0f to Color.Transparent, 0.36f to color, 1f to Color.Transparent, center = center
            ),
            startAngle = 0f, sweepAngle = 130f, useCenter = false,
            topLeft = Offset(-outset, -outset), size = Size(radius * 2, radius * 2),
            style = Stroke(orbitStroke)
        )
        val angle = 130 * PI / 180
        val point = center + Offset(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
        val glow = orbitStroke * 1.4f
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), point, glow),
            glow, point
        )
        drawGargantuaIonTail(point, angle, radius, orbitStroke, color)
        drawCircle(color, orbitStroke * 0.75f, point)
        drawCircle(Color.White.copy(alpha = 0.7f), orbitStroke * 0.34f, point)
    }
}

/**
 * F10 · a cauda de íons: um traço reto e fino que sai da cabeça para trás, um
 * pouco inclinado para fora, como a cauda de íons de um cometa de verdade ao
 * lado da de poeira (o arco curvo). Lê "em movimento" sem o cometa ficar mais
 * forte. O comprimento é o que cabe: a ponta não passa do halo da cabeça, então
 * a órbita continua dentro de `appUsageRingOrbitReach`.
 */
private fun DrawScope.drawGargantuaIonTail(head: Offset, angle: Double, radius: Float, orbitStroke: Float, color: Color) {
    val tilt = ION_TAIL_TILT_DEGREES * PI / 180
    val length = gargantuaIonTailLength(radius, orbitStroke * ION_TAIL_ALLOWANCE, ION_TAIL_TILT_DEGREES)
        .coerceAtMost(orbitStroke * ION_TAIL_MAX)
    if (length <= 0f) return
    // O cometa anda no sentido horário: para trás é a tangente oposta; para fora, o raio.
    val back = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
    val out = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val direction = back * cos(tilt).toFloat() + out * sin(tilt).toFloat()
    val end = head + direction * length
    drawLine(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.9f),
            0.4f to color.copy(alpha = 0.75f),
            1f to Color.Transparent,
            start = head,
            end = end
        ),
        start = head,
        end = end,
        strokeWidth = orbitStroke * ION_TAIL_WIDTH,
        cap = StrokeCap.Round
    )
}

/**
 * Quanto a cauda de íons pode ter sem a ponta passar de [radius] + [allowance]
 * do centro: saindo de um ponto no raio [radius], inclinada [tiltDegrees] para
 * fora da tangente. Raiz da equação |cabeça + L·direção| = [radius] + [allowance].
 */
internal fun gargantuaIonTailLength(radius: Float, allowance: Float, tiltDegrees: Float): Float {
    if (radius <= 0f || allowance <= 0f) return 0f
    val outward = radius * sin(tiltDegrees * PI / 180).toFloat()
    val limit = radius + allowance
    return -outward + kotlin.math.sqrt(outward * outward + limit * limit - radius * radius)
}

private const val ION_TAIL_TILT_DEGREES = 18f
/** A folga além da cabeça, dentro do halo dela (1,4 do traço da órbita). */
private const val ION_TAIL_ALLOWANCE = 1.2f
private const val ION_TAIL_MAX = 4f
private const val ION_TAIL_WIDTH = 0.5f
