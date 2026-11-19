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
    /** Origem, em dp de janela, da janela com o buraco no centro: é o que o arrasto move e grava (#625). */
    val virtualPosition: DpOffset,
    /** Centro do buraco dentro da janela real, em dp de composição (#625). */
    val holeCenter: DpOffset,
    val balloonSide: HudEdge,
    val hitRect: DpRect,
    /** Y do buraco na tela, em dp de composição: a coluna desliza em torno dele. */
    val holeScreenY: Dp,
    /** Topo e base da área de trabalho, em dp de composição. */
    val areaTop: Dp,
    val areaBottom: Dp
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
    columnSize: DpSize,
    holeDiameter: Dp,
    balloonSize: DpSize,
    handleSize: Dp,
    gap: Dp,
    expanded: Boolean,
    balloonOpen: Boolean
): HudFloatingWindowGeometry {
    val unscaled = hudFloatingColumnWindowSize(columnSize, balloonSize, holeDiameter, handleSize, gap)
    val size = DpSize(unscaled.width * scale, unscaled.height * scale)
    // A âncora é sempre o buraco. A janela fica presa na área útil e o buraco anda dentro dela perto da borda (#625).
    val placement = hudFloatingWindowPlacement(center, notchCenter, size, area, holeDiameter * scale)
    val position = DpOffset(placement.window.xPx.dp, placement.window.yPx.dp)
    val holeInWindow = DpOffset(placement.holeCenterXPx.dp, placement.holeCenterYPx.dp)
    val holeCenter = DpOffset(holeInWindow.x / scale, holeInWindow.y / scale)
    val virtualPosition = DpOffset(
        x = position.x + holeInWindow.x - size.width / 2f,
        y = position.y + holeInWindow.y - size.height / 2f
    )
    val holeScreenX = placement.window.xPx + placement.holeCenterXPx
    val balloonSide = hudFloatingBalloonSide(holeScreenX, area.toPxRect())
    // Buraco e área em dp de composição: é a unidade em que a coluna e o recorte são calculados.
    val holeScreenY = (position.y + holeInWindow.y) / scale
    val composedArea = area.inCompositionDp(scale)
    val parts = hudFloatingColumnParts(
        windowSize = unscaled,
        expanded = expanded,
        columnSize = columnSize,
        holeDiameter = holeDiameter,
        holeCenterYPx = holeScreenY.value.roundToInt(),
        workArea = composedArea.toPxRect(),
        balloonOpen = balloonOpen,
        balloonSide = balloonSide,
        balloonSize = balloonSize,
        handleSize = handleSize,
        gap = gap,
        holeCenter = holeCenter
    )
    return HudFloatingWindowGeometry(
        size = size,
        position = position,
        virtualPosition = virtualPosition,
        holeCenter = holeCenter,
        balloonSide = balloonSide,
        hitRect = parts.hitRect,
        holeScreenY = holeScreenY,
        areaTop = composedArea.y,
        areaBottom = composedArea.y + composedArea.size.height
    )
}

/** Posicionamento da janela solta em px: origem real presa na área e centro do buraco dentro dela (#625). */
internal fun hudFloatingWindowPlacement(
    center: HudFloatCenter?,
    notchCenter: HudFloatCenter,
    windowSize: DpSize,
    area: ScreenWorkArea,
    anchorDiameter: Dp
): HudFloatingPlacementPx = hudFloatingBounds(
    center = center,
    notchCenter = notchCenter,
    windowWidthPx = windowSize.width.value.roundToInt(),
    windowHeightPx = windowSize.height.value.roundToInt(),
    workArea = area.toPxRect(),
    anchorRadiusPx = ceil(anchorDiameter.value / 2f).toInt()
)

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

/**
 * Centro da janela flutuante enquanto há arrasto: o do ponteiro quando [dragPosition] existe, senão o
 * centro salvo. Com arrasto, a geometria segue o ponteiro, não o centro gravado (#625, A04a).
 */
internal fun hudFloatingLiveCenter(
    floatCenter: HudFloatCenter?,
    dragPosition: DpOffset?,
    windowSize: DpSize
): HudFloatCenter? =
    if (dragPosition != null) hudFloatCenterOf(dragPosition, windowSize) else floatCenter

/** Clicar no balão aberto o fecha; clicar em outro o troca. */
internal fun hudFloatingBalloonToggle(
    current: HudFloatingBalloon?,
    clicked: HudFloatingBalloon
): HudFloatingBalloon? = if (current == clicked) null else clicked

/** Hover no buraco abre a lista das minimizadas; sobre a lista ou a conta escolhida, mantém o estado. */
internal fun hudFloatingHoleHovered(current: HudFloatingBalloon?): HudFloatingBalloon? =
    if (current is HudFloatingBalloon.Hole) current else HudFloatingBalloon.Hole(null)

/** Clique no buraco alterna a lista; com a conta escolhida, fecha. */
internal fun hudFloatingHoleClicked(current: HudFloatingBalloon?): HudFloatingBalloon? =
    if (current is HudFloatingBalloon.Hole) null else HudFloatingBalloon.Hole(null)

/** Escolher uma conta da lista abre o balão dela. */
internal fun hudFloatingHoleSelected(index: Int): HudFloatingBalloon = HudFloatingBalloon.Hole(index)

/** Voltar da conta escolhida para a lista. Fora do buraco, não muda nada. */
internal fun hudFloatingHoleBack(current: HudFloatingBalloon?): HudFloatingBalloon? =
    if (current is HudFloatingBalloon.Hole) HudFloatingBalloon.Hole(null) else current

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

/** Altura do balão do modo flutuante: a janela reserva a maior entre a da engrenagem e a da conta (A11i). */
internal fun hudFloatingBalloonHeight(appHeight: Dp, accountHeight: Dp): Dp =
    if (appHeight > accountHeight) appHeight else accountHeight
