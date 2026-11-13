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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.usagemonitor.presentation.ui.components.HudStarfieldBox
import com.usagemonitor.presentation.ui.components.HudStarfieldWell
import com.usagemonitor.presentation.ui.components.hudStarfieldFrame
import com.usagemonitor.presentation.ui.components.hudStarfieldInk
import com.usagemonitor.presentation.ui.components.hudStarfieldRemainder
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy

/** Nó do campo estelar do notch; só para o teste de tela. */
internal const val HUD_STARFIELD_TEST_TAG = "hudStarfield"

/** Poços que o canvas desenha, expostos à semântica só para o teste de tela (#586 A03). */
internal val HUD_STARFIELD_WELLS = SemanticsPropertyKey<List<HudStarfieldWell>>("HudStarfieldWells")

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
            .semantics { set(HUD_STARFIELD_WELLS, wells()) }
            .drawWithCache {
                val width = size.width
                val height = size.height
                onDrawBehind {
                    val points = hudStarfieldFrame(timeMs, width, height, wells(), forbidden())
                    for (point in points) {
                        val ink = hudStarfieldInk(dark, point.layer, point.gold)
                        val alpha = if (dark) point.alpha * ink.alpha else ink.alpha * point.fade
                        if (alpha <= 0f) continue
                        val color = ink.color.copy(alpha = alpha)
                        val radius = point.radius * ink.radiusScale
                        val center = Offset(point.x, point.y)
                        if (point.trailX != 0f || point.trailY != 0f) {
                            val tail = Offset(point.x - point.trailX, point.y - point.trailY)
                            drawLine(color, center, tail, strokeWidth = radius)
                        }
                        drawCircle(color, radius, center)
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
