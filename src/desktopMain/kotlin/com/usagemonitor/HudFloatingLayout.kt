package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/*
 * Geometria pura da janela flutuante da HUD (issue #619, A08a). Sem janela AWT: quem
 * compõe (`HudFloatingHost`, em A08c) aplica os retângulos. Layout horizontal da janela:
 * balão | vão | campo | vão | coluna de botões | vão | balão. O tamanho é fixo; o que
 * muda entre minimizado e expandido é só o que aparece dentro dele.
 */

/** Retângulos de cada parte da janela flutuante, em dp relativos à janela. */
internal data class HudFloatingParts(
    /** Buraco negro central: sempre visível. */
    val hole: DpRect,
    /** Campo das contas; `null` quando minimizado. */
    val field: DpRect?,
    /** Coluna de botões à direita do campo; `null` quando minimizado. */
    val buttons: DpRect?,
    /** Balão aberto; `null` quando fechado ou minimizado. */
    val balloon: DpRect?,
    /** União do que está visível: o recorte da janela. */
    val hitRect: DpRect
)

/**
 * Tamanho fixo da janela: largura = balão + vão + campo máximo + vão + coluna + vão + balão
 * (o balão cabe dos dois lados); altura = a maior entre o campo máximo e o balão.
 */
internal fun hudFloatingWindowSize(maxFieldDiameter: Dp, balloonSize: DpSize, handleSize: Dp, gap: Dp): DpSize {
    val width = balloonSize.width * 2 + gap * 3 + maxFieldDiameter + handleSize
    val height = maxOf(maxFieldDiameter, balloonSize.height)
    return DpSize(width, height)
}

/**
 * Lado do balão: abre para o lado com mais espaço livre na área de trabalho. Centro na
 * metade direita devolve [HudEdge.RIGHT] (balão à esquerda de quem o abre). Empate exato
 * no meio devolve [HudEdge.LEFT], para que a decisão seja sempre uma só.
 */
internal fun hudFloatingBalloonSide(windowCenterXPx: Int, workArea: HudPxRect): HudEdge {
    val freeLeft = windowCenterXPx - workArea.xPx
    val freeRight = workArea.xPx + workArea.widthPx - windowCenterXPx
    return if (freeLeft > freeRight) HudEdge.RIGHT else HudEdge.LEFT
}

/**
 * Retângulos das partes. Buraco e campo centrados na janela; coluna de botões à direita
 * do campo, centrada na vertical (engrenagem em cima, encaixar embaixo: altura de duas
 * alturas de botão mais o vão); balão encostado na borda indicada por [balloonSide].
 * `hitRect` é a união do que está visível.
 */
internal fun hudFloatingParts(
    windowSize: DpSize,
    expanded: Boolean,
    fieldDiameter: Dp,
    holeDiameter: Dp,
    balloonOpen: Boolean,
    balloonSide: HudEdge,
    balloonWidth: Dp,
    balloonHeight: Dp,
    handleSize: Dp,
    gap: Dp
): HudFloatingParts {
    val centerX = windowSize.width / 2f
    val centerY = windowSize.height / 2f
    val hole = centeredRect(centerX, centerY, holeDiameter, holeDiameter)
    if (!expanded) {
        return HudFloatingParts(hole = hole, field = null, buttons = null, balloon = null, hitRect = hole)
    }

    val field = centeredRect(centerX, centerY, fieldDiameter, fieldDiameter)
    val columnHeight = handleSize * 2 + gap
    val buttonsLeft = centerX + fieldDiameter / 2f + gap
    val buttons = centeredRect(buttonsLeft + handleSize / 2f, centerY, handleSize, columnHeight)

    val balloon = if (balloonOpen) balloonRect(windowSize, balloonSide, balloonWidth, balloonHeight) else null

    val visible = listOfNotNull(hole, field, buttons, balloon)
    return HudFloatingParts(
        hole = hole,
        field = field,
        buttons = buttons,
        balloon = balloon,
        hitRect = unionOf(visible)
    )
}

/** Retângulo de [width] x [height] centrado em ([centerX], [centerY]). */
private fun centeredRect(centerX: Dp, centerY: Dp, width: Dp, height: Dp): DpRect =
    DpRect(
        left = centerX - width / 2f,
        top = centerY - height / 2f,
        right = centerX + width / 2f,
        bottom = centerY + height / 2f
    )

/** Balão encostado na borda da janela: [HudEdge.RIGHT] à esquerda, [HudEdge.LEFT] à direita. */
private fun balloonRect(windowSize: DpSize, balloonSide: HudEdge, balloonWidth: Dp, balloonHeight: Dp): DpRect {
    val top = ((windowSize.height - balloonHeight) / 2f).coerceAtLeast(0.dp)
    val left = if (balloonSide == HudEdge.RIGHT) 0.dp else windowSize.width - balloonWidth
    return DpRect(left, top, left + balloonWidth, top + balloonHeight)
}

/** Menor retângulo que contém todos; a lista é sempre não vazia (o buraco está nela). */
private fun unionOf(rects: List<DpRect>): DpRect {
    var left = rects[0].left
    var top = rects[0].top
    var right = rects[0].right
    var bottom = rects[0].bottom
    for (rect in rects) {
        left = minOf(left, rect.left)
        top = minOf(top, rect.top)
        right = maxOf(right, rect.right)
        bottom = maxOf(bottom, rect.bottom)
    }
    return DpRect(left, top, right, bottom)
}
