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

/** Dono do modo solto: borda virtual, fração ao longo dela e área que o notch enxerga (A10a). */
internal data class HudFloatingDock(
    val edge: HudEdge,
    val offsetFraction: Float,
    val virtualArea: ScreenWorkArea
)

/** Lado pela metade da área (mesma regra do balão); a borda virtual vai até a face externa do buraco. */
internal fun hudFloatingDock(holeCenter: HudFloatCenter, holeD: Dp, area: ScreenWorkArea): HudFloatingDock {
    val edge = hudFloatingBalloonSide(holeCenter.xPx, area.toPxRect())
    val radius = holeD / 2
    val holeCenterX = holeCenter.xPx.dp
    val virtualArea = if (edge == HudEdge.RIGHT) {
        val faceX = holeCenterX + radius
        ScreenWorkArea(area.x, area.y, DpSize(faceX - area.x, area.size.height))
    } else {
        val faceX = holeCenterX - radius
        ScreenWorkArea(faceX, area.y, DpSize(area.x + area.size.width - faceX, area.size.height))
    }
    val offsetFraction = ((holeCenter.yPx.toFloat() - area.y.value) / area.size.height.value).coerceIn(0f, 1f)
    return HudFloatingDock(edge, offsetFraction, virtualArea)
}

/** Modo solto: só o buraco muda de tamanho; expandido, balão e alças são os de antes. */
internal fun hudFloatingSizes(sizes: HudNotchSizes, holeD: Dp): HudNotchSizes =
    sizes.copy(collapsed = DpSize(holeD, holeD))

/**
 * Deslocamento da coluna aberta em relação ao centro do buraco, ao longo da borda. O buraco não
 * se move: a coluna desliza só o necessário para caber inteira na área. Coluna maior que a área
 * se alinha ao início.
 */
internal fun hudFloatingAlongShift(holeAlong: Dp, expandedAlong: Dp, areaStart: Dp, areaEnd: Dp): Dp {
    val columnStart = holeAlong - expandedAlong / 2
    if (expandedAlong >= areaEnd - areaStart) return areaStart - columnStart
    val low = areaStart - columnStart
    val high = areaEnd - (columnStart + expandedAlong)
    return minOf(maxOf(0.dp, low), high)
}

/** Borda, tamanhos, janela, buraco em coordenadas da janela e deslocamento da coluna do modo solto (A10c). */
internal data class HudDetachedWindow(
    val edge: HudEdge,
    val placement: HudPlacement,
    val sizes: HudNotchSizes,
    val settledSizes: HudNotchSizes,
    val bounds: HudWindowBounds,
    val alongShift: Dp,
    val holeRect: DpRect
)

/**
 * Buraco encostado no lado da borda da janela, centrado em [notchCenterInWindow] ao longo dela
 * (A10c). Coordenadas da janela em dp de composição.
 */
internal fun hudDetachedHoleRect(edge: HudEdge, windowSize: DpSize, notchCenterInWindow: Dp, holeD: Dp): DpRect {
    val top = notchCenterInWindow - holeD / 2
    val left = if (edge == HudEdge.RIGHT) windowSize.width - holeD else 0.dp
    return DpRect(left, top, left + holeD, top + holeD)
}

/**
 * Modo solto (A10c): o centro do buraco é preso aos limites inteiros do monitor ([monitorBounds]),
 * e a borda, a fração e a área virtual saem de [hudFloatingDock] sobre a área útil. [holeD] é em
 * dp de composição; a borda virtual usa o diâmetro em dp de tela (`holeD * scale`). A coluna
 * aberta desliza para dentro da área útil ([hudFloatingAlongShift]).
 */
internal fun hudDetachedWindow(
    holeCenter: HudFloatCenter,
    holeD: Dp,
    screenArea: ScreenWorkArea,
    monitorBounds: ScreenWorkArea,
    scale: Float,
    sizesOf: (HudPlacement, ScreenWorkArea) -> Pair<HudNotchSizes, HudNotchSizes>,
    dragging: Boolean
): HudDetachedWindow {
    val monitor = monitorBounds.toPxRect()
    val clamped = HudFloatCenter(
        xPx = holeCenter.xPx.coerceIn(monitor.xPx, monitor.xPx + monitor.widthPx),
        yPx = holeCenter.yPx.coerceIn(monitor.yPx, monitor.yPx + monitor.heightPx)
    )
    val dock = hudFloatingDock(clamped, holeD * scale, screenArea)
    val placement = HudPlacement(dock.edge, dock.offsetFraction)
    val built = sizesOf(placement, dock.virtualArea)
    val sizes = hudFloatingSizes(built.first, holeD)
    val settledSizes = hudFloatingSizes(built.second, holeD)
    val composed = dock.virtualArea.inCompositionDp(scale)
    val bounds = hudHostWindowBounds(dragging, placement, sizes, composed)
    val holeRect = hudDetachedHoleRect(dock.edge, bounds.size, bounds.notchCenterInWindow, holeD)
    val usable = screenArea.inCompositionDp(scale)
    val horizontal = dock.edge.isHorizontal
    val windowStart = if (horizontal) bounds.x else bounds.y
    val holeAlong = windowStart + bounds.notchCenterInWindow
    val expanded = settledSizes.expanded
    val expandedAlong = if (horizontal) expanded.width else expanded.height
    val areaStart = if (horizontal) usable.x else usable.y
    val areaEnd = areaStart + if (horizontal) usable.size.width else usable.size.height
    val alongShift = hudFloatingAlongShift(holeAlong, expandedAlong, areaStart, areaEnd)
    return HudDetachedWindow(
        edge = dock.edge,
        placement = placement,
        sizes = sizes,
        settledSizes = settledSizes,
        bounds = bounds,
        alongShift = alongShift,
        holeRect = holeRect
    )
}
