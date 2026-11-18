package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudFloatingBalloon
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Ligação pura do modo flutuante da HUD (issue #619, A08c). Sem janela AWT: `HudWindow`
 * aplica os resultados. Unidades: px lógicos (dp de janela), inteiros em [HudFloatCenter].
 */

/** Sentinela de "sem limite" por eixo quando a área é desconhecida: longe de qualquer tela real. */
private const val UNBOUNDED_PX = 1 shl 20

/**
 * Geometria da janela flutuante num instante. [size] e [position] são dp de janela (já
 * escalados); [hitRect] é dp de composição, sem escala, porque `HudWindowEffects` escala o
 * recorte ao aplicá-lo.
 */
internal data class HudFloatingWindowGeometry(
    val size: DpSize,
    val position: DpOffset,
    val balloonSide: HudEdge,
    val hitRect: DpRect
)

/**
 * Tamanho, posição, lado do balão e recorte da janela flutuante. O tamanho é multiplicado
 * pela escala; as partes são calculadas sem escala, na unidade em que o recorte é aplicado.
 */
internal fun hudFloatingWindowGeometry(
    center: HudFloatCenter?,
    notchCenter: HudFloatCenter,
    area: ScreenWorkArea,
    scale: Float,
    maxFieldDiameter: Dp,
    holeDiameter: Dp,
    balloonSize: DpSize,
    handleSize: Dp,
    gap: Dp,
    expanded: Boolean,
    balloonOpen: Boolean
): HudFloatingWindowGeometry {
    val unscaled = hudFloatingWindowSize(maxFieldDiameter, balloonSize, handleSize, gap)
    val size = DpSize(unscaled.width * scale, unscaled.height * scale)
    val anchorDiameter = if (expanded) maxFieldDiameter * scale else holeDiameter * scale
    val position = hudFloatingWindowPosition(center, notchCenter, size, area, anchorDiameter)
    val windowCenterXPx = (position.x.value + size.width.value / 2f).roundToInt()
    val balloonSide = hudFloatingBalloonSide(windowCenterXPx, area.toPxRect())
    val parts = hudFloatingParts(
        windowSize = unscaled,
        expanded = expanded,
        fieldDiameter = maxFieldDiameter,
        holeDiameter = holeDiameter,
        balloonOpen = balloonOpen,
        balloonSide = balloonSide,
        balloonWidth = balloonSize.width,
        balloonHeight = balloonSize.height,
        handleSize = handleSize,
        gap = gap
    )
    return HudFloatingWindowGeometry(
        size = size,
        position = position,
        balloonSide = balloonSide,
        hitRect = parts.hitRect
    )
}

/** Canto superior esquerdo da janela solta, em dp de janela. */
internal fun hudFloatingWindowPosition(
    center: HudFloatCenter?,
    notchCenter: HudFloatCenter,
    windowSize: DpSize,
    area: ScreenWorkArea,
    anchorDiameter: Dp
): DpOffset {
    val rect = hudFloatingBounds(
        center = center,
        notchCenter = notchCenter,
        windowWidthPx = windowSize.width.value.roundToInt(),
        windowHeightPx = windowSize.height.value.roundToInt(),
        workArea = area.toPxRect(),
        anchorRadiusPx = ceil(anchorDiameter.value / 2f).toInt()
    )
    return DpOffset(rect.xPx.dp, rect.yPx.dp)
}

/**
 * Centro da janela solta a partir do canto superior esquerdo. `floor` e não `roundToInt`:
 * `hudFloatingBounds` usa divisão inteira (`largura / 2`), então só o `floor` devolve o
 * mesmo centro na ida e na volta com largura ímpar.
 */
internal fun hudFloatCenterOf(position: DpOffset, windowSize: DpSize): HudFloatCenter =
    HudFloatCenter(
        xPx = floor(position.x.value + windowSize.width.value / 2f).toInt(),
        yPx = floor(position.y.value + windowSize.height.value / 2f).toInt()
    )

/** Clicar no balão aberto o fecha; clicar em outro o troca. */
internal fun hudFloatingBalloonToggle(
    current: HudFloatingBalloon?,
    clicked: HudFloatingBalloon
): HudFloatingBalloon? = if (current == clicked) null else clicked

/** Área em px lógicos inteiros; área desconhecida (NaN) vira retângulo sem limite. */
internal fun ScreenWorkArea.toPxRect(): HudPxRect {
    val unknown = x.value.isNaN() || y.value.isNaN() || size.width.value.isNaN() || size.height.value.isNaN()
    if (unknown) {
        return HudPxRect(-UNBOUNDED_PX, -UNBOUNDED_PX, 2 * UNBOUNDED_PX, 2 * UNBOUNDED_PX)
    }
    return HudPxRect(
        xPx = x.value.roundToInt(),
        yPx = y.value.roundToInt(),
        widthPx = size.width.value.roundToInt(),
        heightPx = size.height.value.roundToInt()
    )
}
