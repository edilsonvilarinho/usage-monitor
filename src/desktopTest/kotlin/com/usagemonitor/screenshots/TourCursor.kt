package com.usagemonitor.screenshots

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Onde está o cursor sintético do tour e se ele está no meio de um clique.
 *
 * @param clickProgress `null` fora do clique; 0..1 durante a onda.
 */
internal data class TourCursorPose(
    val x: Dp,
    val y: Dp,
    val visible: Boolean = true,
    val clickProgress: Float? = null
)

private val CURSOR_SIZE = 22.dp
private val RIPPLE_MIN_RADIUS = 6.dp
private val RIPPLE_MAX_RADIUS = 26.dp

/**
 * Ponteiro desenhado por cima da cena.
 *
 * O tour muda de tela mudando estado, não clicando de verdade: sem este
 * ponteiro o espectador veria telas trocando sozinhas, sem saber o que foi
 * acionado. As coordenadas são fixas em dp — se o layout mudar, o ponteiro sai
 * do lugar, mas o conteúdo continua certo porque vem do estado.
 */
@Composable
internal fun TourCursorOverlay(pose: TourCursorPose, modifier: Modifier = Modifier) {
    if (!pose.visible) {
        return
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val tipX = pose.x.toPx()
        val tipY = pose.y.toPx()

        val progress = pose.clickProgress
        if (progress != null) {
            val radius = RIPPLE_MIN_RADIUS.toPx() +
                (RIPPLE_MAX_RADIUS.toPx() - RIPPLE_MIN_RADIUS.toPx()) * progress
            drawCircle(
                color = Color.White.copy(alpha = 0.28f * (1f - progress)),
                radius = radius,
                center = Offset(tipX, tipY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.55f * (1f - progress)),
                radius = radius,
                center = Offset(tipX, tipY),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        val size = CURSOR_SIZE.toPx()
        val pointer = Path().apply {
            moveTo(0f, 0f)
            lineTo(0f, 0.72f * size)
            lineTo(0.19f * size, 0.56f * size)
            lineTo(0.32f * size, 0.87f * size)
            lineTo(0.46f * size, 0.81f * size)
            lineTo(0.33f * size, 0.52f * size)
            lineTo(0.55f * size, 0.52f * size)
            close()
        }

        // Sombra primeiro: o ponteiro é branco e some sobre os cartões claros do
        // gráfico se não tiver contorno e queda.
        translate(left = tipX + 1.5f, top = tipY + 2f) {
            drawPath(path = pointer, color = Color.Black.copy(alpha = 0.45f))
        }
        translate(left = tipX, top = tipY) {
            drawPath(path = pointer, color = Color.White)
            drawPath(
                path = pointer,
                color = Color(0xFF10141A),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}
