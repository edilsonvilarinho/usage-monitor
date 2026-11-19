package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.presentation.ui.components.STATUS_DOT_SIZE
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.cos

/** Raio do núcleo escuro em fração do raio do buraco (fórmula do disco, A07a). */
private const val CORE_FACTOR = 0.62f

/** Largura da borda clara do núcleo em fração do raio do buraco. */
private const val CORE_BORDER_FACTOR = 0.022f

/** Raio horizontal do disco em fração do raio do buraco. */
private const val DISK_RX_FACTOR = 0.95f

/** Deslocamento vertical do disco, para baixo, em fração do raio do buraco. */
private const val DISK_OFFSET_FACTOR = 0.08f

/** Rotação do disco, em graus. */
private const val DISK_ROTATION_DEGREES = -12f

/** Traço da metade de trás, em fração do traço da frente. */
private const val DISK_BACK_STROKE_FACTOR = 0.85f

/** Alfa máximo da metade de trás, em fração da frente. */
private const val DISK_BACK_ALPHA = 0.6f

/** Alfa mínimo de um segmento apagado, em fração do máximo. */
private const val DISK_MIN_ALPHA = 0.25f

/** Segmentos por metade do disco. */
private const val DISK_SEGMENTS = 36

/**
 * Ângulo do disco de acreção em graus, em [0, 360). Função pura: o tempo corre em ms e uma volta dura
 * [spinSeconds]. Velocidade zero ou negativa deixa o disco parado em zero.
 */
internal fun hudDiskAngleDegrees(elapsedMillis: Long, spinSeconds: Float): Float {
    if (spinSeconds <= 0f) return 0f
    val periodMs = spinSeconds.toDouble() * 1000.0
    val phaseMs = ((elapsedMillis.toDouble() % periodMs) + periodMs) % periodMs
    val degrees = (phaseMs / periodMs * 360.0).toFloat()
    return if (degrees >= 360f) 0f else degrees
}

/**
 * Buraco negro do modo minimizado. Com [update] presente, o ponto de atualização usa o tom do indicador e
 * a frase dele entra na descrição semântica, como na barra do notch.
 */
@Composable
internal fun HudFloatingHole(
    params: HudOrbitParams,
    update: HudUpdateIndicator?,
    description: String,
    modifier: Modifier = Modifier
) {
    val policy = LocalAppMotionPolicy.current
    val clock = rememberHudFloatingHoleClock(live = policy.continuous && !policy.reduced)
    val dotColor = if (update != null) update.tone.color() else null
    val fullDescription = if (update == null) description else "$description · ${update.description}"
    Canvas(
        modifier = modifier
            .size(params.holeD.dp)
            .semantics { contentDescription = fullDescription }
    ) {
        drawFloatingHole(params, hudDiskAngleDegrees(clock.value, params.diskSpinSeconds), dotColor)
    }
}

/** Relógio por quadro só quando a animação é permitida; parado em zero caso contrário. */
@Composable
private fun rememberHudFloatingHoleClock(live: Boolean): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        if (!live) {
            clock.longValue = 0L
        } else {
            while (true) {
                withFrameMillis { frame -> clock.longValue = frame }
            }
        }
    }
    return clock
}

/**
 * Ordem de desenho: metade de trás do disco, núcleo com borda, metade da frente. A metade de trás fica
 * sob o núcleo e a da frente sobre ele.
 */
private fun DrawScope.drawFloatingHole(params: HudOrbitParams, diskAngle: Float, dotColor: Color?) {
    val centerPoint = center
    val room = size.minDimension / 2f
    val coreRadius = room * CORE_FACTOR
    drawDiskHalf(params, room, diskAngle, front = false)
    drawCircle(AppGargantuaTokens.core, coreRadius, centerPoint)
    drawCircle(AppGargantuaTokens.lens, coreRadius, centerPoint, style = Stroke(width = room * CORE_BORDER_FACTOR))
    drawDiskHalf(params, room, diskAngle, front = true)
    if (dotColor != null) {
        val dotOffset = room * 0.7f
        drawCircle(
            dotColor,
            STATUS_DOT_SIZE.toPx() / 2f,
            Offset(centerPoint.x + dotOffset, centerPoint.y - dotOffset)
        )
    }
}

/**
 * Uma metade da elipse, em [DISK_SEGMENTS] segmentos. Brilho por segmento:
 * `b = 0.5 + 0.5·cos(ângulo_médio − ângulo_do_disco)`; cor ember → hot por `b`; alfa `(frente ? 1 : 0.6)·(0.25 + 0.75·b)`.
 */
private fun DrawScope.drawDiskHalf(params: HudOrbitParams, room: Float, diskAngle: Float, front: Boolean) {
    val rx = room * DISK_RX_FACTOR
    val ry = rx * params.tiltPercent / 100f
    val diskCenter = Offset(center.x, center.y + room * DISK_OFFSET_FACTOR)
    val thickness = room * params.diskWidthPercent / 100f * (if (front) 1f else DISK_BACK_STROKE_FACTOR)
    val strokeStyle = Stroke(width = thickness)
    val start = if (front) 0f else 180f
    val step = 180f / DISK_SEGMENTS
    val diskRad = Math.toRadians(diskAngle.toDouble())
    val baseAlpha = if (front) 1f else DISK_BACK_ALPHA
    rotate(DISK_ROTATION_DEGREES, pivot = diskCenter) {
        for (index in 0 until DISK_SEGMENTS) {
            val midRad = Math.toRadians((start + step * (index + 0.5f)).toDouble())
            val brightness = (0.5 + 0.5 * cos(midRad - diskRad)).toFloat()
            val alpha = baseAlpha * (DISK_MIN_ALPHA + (1f - DISK_MIN_ALPHA) * brightness)
            drawArc(
                color = lerp(AppGargantuaTokens.ember, AppGargantuaTokens.hot, brightness).copy(alpha = alpha),
                startAngle = start + step * index,
                sweepAngle = step,
                useCenter = false,
                topLeft = Offset(diskCenter.x - rx, diskCenter.y - ry),
                size = Size(rx * 2f, ry * 2f),
                style = strokeStyle
            )
        }
    }
}
