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

/**
 * Retângulos do modo coluna (A11b): buraco no centro da janela, coluna de anéis ao lado dele
 * e balão além da coluna. Não há campo circular.
 */
internal data class HudFloatingColumnParts(
    /** Buraco negro central: sempre visível e nunca se move dentro da janela. */
    val hole: DpRect,
    /** Coluna de anéis ao lado do buraco; `null` quando minimizado. */
    val column: DpRect?,
    /** Balão aberto, além da coluna; `null` quando fechado ou minimizado. */
    val balloon: DpRect?,
    /** União do que está visível: o recorte da janela. */
    val hitRect: DpRect
)

/**
 * Tamanho fixo da janela do modo coluna: largura = buraco + 4 vãos + coluna dos dois lados
 * + balão dos dois lados; altura = a maior entre o dobro da coluna (o deslize vertical) e o balão.
 */
internal fun hudFloatingColumnWindowSize(columnSize: DpSize, balloonSize: DpSize, holeDiameter: Dp, gap: Dp): DpSize {
    val width = holeDiameter + gap * 4 + columnSize.width * 2 + balloonSize.width * 2
    val height = maxOf(columnSize.height * 2, balloonSize.height)
    return DpSize(width, height)
}

/**
 * Retângulos do modo coluna. Buraco sempre no centro da janela. Coluna ao lado do buraco, no
 * lado de [balloonSide] (LEFT = direita, RIGHT = esquerda), centrada no Y do buraco e deslizada
 * para caber na área de trabalho e na janela. Balão além da coluna, centrado na vertical.
 */
internal fun hudFloatingColumnParts(
    windowSize: DpSize,
    expanded: Boolean,
    columnSize: DpSize,
    holeDiameter: Dp,
    holeCenterYPx: Int,
    workArea: HudPxRect,
    balloonOpen: Boolean,
    balloonSide: HudEdge,
    balloonSize: DpSize,
    gap: Dp
): HudFloatingColumnParts {
    val centerX = windowSize.width / 2f
    val centerY = windowSize.height / 2f
    val hole = centeredRect(centerX, centerY, holeDiameter, holeDiameter)
    if (!expanded) {
        return HudFloatingColumnParts(hole = hole, column = null, balloon = null, hitRect = hole)
    }

    val columnTop = columnTopDp(windowSize, columnSize, holeCenterYPx, workArea)
    val columnLeft = if (balloonSide == HudEdge.LEFT) {
        centerX + holeDiameter / 2f + gap
    } else {
        centerX - holeDiameter / 2f - gap - columnSize.width
    }
    val column = DpRect(columnLeft, columnTop, columnLeft + columnSize.width, columnTop + columnSize.height)

    val balloon = if (balloonOpen) {
        balloonRect(windowSize, balloonSide, balloonSize.width, balloonSize.height)
    } else {
        null
    }

    val visible = listOfNotNull(hole, column, balloon)
    return HudFloatingColumnParts(
        hole = hole,
        column = column,
        balloon = balloon,
        hitRect = unionOf(visible)
    )
}

/**
 * Topo da coluna em relação à janela. Ideal: centrada no buraco (metade da janela menos metade
 * da coluna). Limites: a área de trabalho, convertida para a janela, e a própria janela. O buraco
 * não se move; só a coluna desliza. Janela fora da área cai no limite da janela.
 */
private fun columnTopDp(windowSize: DpSize, columnSize: DpSize, holeCenterYPx: Int, workArea: HudPxRect): Dp {
    val windowTopPx = holeCenterYPx - windowSize.height.value / 2f
    val areaTopInWindow = workArea.yPx - windowTopPx
    val areaBottomInWindow = workArea.yPx + workArea.heightPx - windowTopPx
    val windowHeight = windowSize.height.value
    val columnHeight = columnSize.height.value
    val ideal = (windowHeight - columnHeight) / 2f
    val low = maxOf(0f, areaTopInWindow)
    val high = minOf(windowHeight - columnHeight, areaBottomInWindow - columnHeight)
    val top = maxOf(0f, minOf(maxOf(ideal, low), high))
    return top.dp
}

/**
 * Topo do balão do modo coluna: centrado no anel em foco ([ringCenterY], em dp de janela) e preso
 * dentro da janela.
 */
internal fun hudFloatingBalloonTop(ringCenterY: Dp, balloonHeight: Dp, windowHeight: Dp): Dp {
    val ideal = ringCenterY - balloonHeight / 2f
    return ideal.coerceIn(0.dp, (windowHeight - balloonHeight).coerceAtLeast(0.dp))
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
