package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import kotlin.math.min
import com.usagemonitor.presentation.ui.components.HudStarfieldBox
import com.usagemonitor.presentation.ui.components.HudStarCapture
import com.usagemonitor.presentation.ui.components.HudStarCaptureFlash
import com.usagemonitor.presentation.ui.components.HudStarCaptureFlight
import com.usagemonitor.presentation.ui.components.HudStarfieldLayer
import com.usagemonitor.presentation.ui.components.HudStarfieldWell
import com.usagemonitor.presentation.ui.components.HUD_STARFIELD_GLINT_WIDTH
import com.usagemonitor.presentation.ui.components.hudStarfieldDrawAlpha
import com.usagemonitor.presentation.ui.components.hudStarfieldFrame
import com.usagemonitor.presentation.ui.components.hudStarfieldGlintHalfLength
import com.usagemonitor.presentation.ui.components.hudStarfieldInk
import com.usagemonitor.presentation.ui.components.hudStarfieldRemainder
import com.usagemonitor.presentation.ui.components.hudStarCaptures
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy

/** Nó do campo estelar do notch; só para o teste de tela. */
internal const val HUD_STARFIELD_TEST_TAG = "hudStarfield"

/** Poços que o canvas desenha, expostos à semântica só para o teste de tela (#586 A03). */
internal val HUD_STARFIELD_WELLS = SemanticsPropertyKey<List<HudStarfieldWell>>("HudStarfieldWells")

/** Contagem de capturas desenhadas no quadro; zero fora do movimento vivo (#590 A03). */
internal val HUD_STARFIELD_CAPTURE_COUNT = SemanticsPropertyKey<Int>("HudStarfieldCaptureCount")

/**
 * Campo estelar atrás dos anéis (#586). Tempo avança só com `continuous && !reduced`; senão quadro fixo em t = 0.
 * [wells] são os poços (um por anel medido) e [forbidden] as caixas proibidas, ambos lidos no desenho.
 * No claro o alfa é o da L1 por camada; no escuro é o da AT1. Fade de borda e caixa multiplicam nos dois.
 */
@Composable
internal fun HudStarfieldCanvas(
    wells: () -> List<HudStarfieldWell>,
    forbidden: () -> List<HudStarfieldBox>,
    modifier: Modifier = Modifier
) {
    val policy = LocalAppMotionPolicy.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val live = policy.continuous && !policy.reduced
    var timeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        if (!live) {
            timeMs = 0L
        } else {
            while (true) {
                withFrameMillis { frameTime -> timeMs = frameTime }
            }
        }
    }
    Spacer(
        modifier
            .testTag(HUD_STARFIELD_TEST_TAG)
            .semantics {
                set(HUD_STARFIELD_WELLS, wells())
                set(HUD_STARFIELD_CAPTURE_COUNT, if (live) hudStarCaptures(timeMs, wells(), forbidden()).size else 0)
            }
            .drawWithCache {
                val width = size.width
                val height = size.height
                onDrawBehind {
                    val boxes = forbidden()
                    val wellList = wells()
                    val captures = if (live) hudStarCaptures(timeMs, wellList, boxes) else emptyList()
                    val captureInk = hudStarfieldInk(dark, HudStarfieldLayer.NEAR, gold = false).color
                    for (capture in captures) drawStarCapture(capture, captureInk, dark)
                    val points = hudStarfieldFrame(timeMs, width, height, wellList, boxes)
                    for (point in points) {
                        val ink = hudStarfieldInk(dark, point.layer, point.gold)
                        val alpha = hudStarfieldDrawAlpha(dark, point, boxes)
                        if (alpha <= 0f) continue
                        val color = ink.color.copy(alpha = alpha)
                        val radius = point.radius * ink.radiusScale
                        val center = Offset(point.x, point.y)
                        if (point.trailX != 0f || point.trailY != 0f) {
                            val tail = Offset(point.x - point.trailX, point.y - point.trailY)
                            drawLine(color, center, tail, strokeWidth = radius)
                        }
                        drawCircle(color, radius, center)
                        if (point.glint) {
                            // Brilho em cruz: duas linhas, alfa meio da estrela, centradas no ponto.
                            val half = hudStarfieldGlintHalfLength(radius)
                            val glint = ink.color.copy(alpha = alpha * 0.5f)
                            drawLine(glint, Offset(point.x - half, point.y), Offset(point.x + half, point.y), strokeWidth = HUD_STARFIELD_GLINT_WIDTH)
                            drawLine(glint, Offset(point.x, point.y - half), Offset(point.x, point.y + half), strokeWidth = HUD_STARFIELD_GLINT_WIDTH)
                        }
                    }
                }
            }
    )
}

/** Poços: um por anel medido, no centro do Rect e com raio = metade da largura (#586 A03). */
internal fun hudStarfieldWells(rings: Map<Int, Rect>): List<HudStarfieldWell> =
    rings.toSortedMap().values.map { ring -> HudStarfieldWell(ring.center.x, ring.center.y, ring.width / 2f) }

/** Caixas proibidas: a caixa do item menos o recorte do anel dele; item sem anel medido fica inteiro. */
internal fun hudStarfieldForbidden(items: Map<Int, Rect>, rings: Map<Int, Rect>): List<HudStarfieldBox> =
    items.toSortedMap().flatMap { (index, item) ->
        val box = HudStarfieldBox(item.left, item.top, item.right, item.bottom)
        val ring = rings[index]
        if (ring == null) {
            listOf(box)
        } else {
            hudStarfieldRemainder(box, HudStarfieldBox(ring.left, ring.top, ring.right, ring.bottom))
        }
    }

/** Raio da cabeça em voo, em dp. */
private const val HUD_STAR_CAPTURE_HEAD_RADIUS = 1.2f

/** Teto do alfa do lampejo: branco sumiria no branco, então o claro fica em .35 (#590 A03). */
private const val HUD_STAR_CAPTURE_FLASH_MAX_DARK = 0.6f
private const val HUD_STAR_CAPTURE_FLASH_MAX_LIGHT = 0.35f

/** Captura em tinta: rastro como segmentos de largura decrescente, cabeça e lampejo como círculos. */
private fun DrawScope.drawStarCapture(capture: HudStarCapture, color: Color, dark: Boolean) {
    when (capture) {
        is HudStarCaptureFlight -> {
            var previous = Offset(capture.headX, capture.headY)
            for (point in capture.trail) {
                val next = Offset(point.x, point.y)
                drawLine(color.copy(alpha = point.alpha), previous, next, strokeWidth = point.width)
                previous = next
            }
            drawCircle(color.copy(alpha = capture.headAlpha), HUD_STAR_CAPTURE_HEAD_RADIUS, Offset(capture.headX, capture.headY))
        }
        is HudStarCaptureFlash -> {
            val cap = if (dark) HUD_STAR_CAPTURE_FLASH_MAX_DARK else HUD_STAR_CAPTURE_FLASH_MAX_LIGHT
            drawCircle(color.copy(alpha = min(capture.alpha, cap)), capture.radius, Offset(capture.x, capture.y))
        }
    }
}
