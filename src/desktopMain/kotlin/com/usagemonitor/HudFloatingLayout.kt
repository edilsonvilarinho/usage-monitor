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
    /** Engrenagem e encaixar, logo abaixo da coluna; `null` quando minimizado. */
    val buttons: DpRect?,
    /** Balão aberto, além da coluna; `null` quando fechado ou minimizado. */
    val balloon: DpRect?,
    /** União do que está visível: o recorte da janela. */
    val hitRect: DpRect,
    /** Retângulo da coluna, sempre calculado (minimizado ou não); a íris é que a revela. */
    val columnSlot: DpRect,
    /** Retângulo das alças, sempre calculado (minimizado ou não). */
    val buttonsSlot: DpRect
)

/**
 * Tamanho fixo da janela do modo coluna: largura = buraco + 4 vãos + coluna dos dois lados
 * + balão dos dois lados; altura = a maior entre o dobro da coluna mais as alças dos dois
 * extremos (`2·(2·handleSize + 2·gap)`) e o dobro do balão (`2·balão`, A11l). O buraco fica
 * no centro da janela, então metade do balão cai para cima e metade para baixo; com só o
 * buraco à vista, um balão de altura `h` só cabe inteiro se a janela tiver `2·h`. Sem as
 * alças na conta, a coluna colada no extremo do deslize empurra engrenagem e encaixar para
 * fora da janela.
 */
internal fun hudFloatingColumnWindowSize(
    columnSize: DpSize,
    balloonSize: DpSize,
    holeDiameter: Dp,
    handleSize: Dp,
    gap: Dp
): DpSize {
    val width = holeDiameter + gap * 4 + columnSize.width * 2 + balloonSize.width * 2
    val handlesBothEnds = (handleSize * 2 + gap * 2) * 2
    val height = maxOf(columnSize.height * 2 + handlesBothEnds, balloonSize.height * 2)
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
    handleSize: Dp,
    gap: Dp
): HudFloatingColumnParts {
    val centerX = windowSize.width / 2f
    val centerY = windowSize.height / 2f
    val hole = centeredRect(centerX, centerY, holeDiameter, holeDiameter)
    val slide = columnSlide(windowSize, columnSize, handleSize, gap, holeCenterYPx, workArea)
    val columnTop = slide.top
    val columnLeft = if (balloonSide == HudEdge.LEFT) {
        centerX + holeDiameter / 2f + gap
    } else {
        centerX - holeDiameter / 2f - gap - columnSize.width
    }
    val column = DpRect(columnLeft, columnTop, columnLeft + columnSize.width, columnTop + columnSize.height)

    // Slots sempre calculados: minimizado, a coluna e as alças existem no mesmo retângulo, e a íris é que as revela (A11e).
    val buttonsSlot = buttonsRect(column, windowSize, handleSize, gap, slide.stackAbove)
    if (!expanded) {
        return HudFloatingColumnParts(
            hole = hole,
            column = null,
            buttons = null,
            balloon = null,
            hitRect = hole,
            columnSlot = column,
            buttonsSlot = buttonsSlot
        )
    }

    val balloon = if (balloonOpen) {
        balloonRect(windowSize, balloonSide, balloonSize.width, balloonSize.height)
    } else {
        null
    }

    val visible = listOfNotNull(hole, column, buttonsSlot, balloon)
    return HudFloatingColumnParts(
        hole = hole,
        column = column,
        buttons = buttonsSlot,
        balloon = balloon,
        hitRect = unionOf(visible),
        columnSlot = column,
        buttonsSlot = buttonsSlot
    )
}

/**
 * Engrenagem e encaixar: duas alças empilhadas, de altura `2·handleSize + gap`, centradas no X da
 * coluna. [stackAbove] `true` põe a pilha logo acima da coluna, `false` logo abaixo; `null` usa a
 * regra pela janela (abaixo se couber, senão acima), que só vale quando nenhum lado cabe na área.
 */
private fun buttonsRect(column: DpRect, windowSize: DpSize, handleSize: Dp, gap: Dp, stackAbove: Boolean?): DpRect {
    val height = handleSize * 2 + gap
    val centerX = (column.left + column.right) / 2f
    val below = column.bottom + gap
    val top = when (stackAbove) {
        true -> column.top - gap - height
        false -> below
        null -> if (below + height <= windowSize.height) below else column.top - gap - height
    }
    return DpRect(centerX - handleSize / 2f, top, centerX + handleSize / 2f, top + height)
}

/** Topo da coluna e lado da pilha de alças; [stackAbove] `null` quando nenhum lado cabe na área útil. */
private data class HudColumnSlide(val top: Dp, val stackAbove: Boolean?)

/**
 * Coluna e pilha de alças como um bloco só, dentro da área útil. Ideal: coluna centrada no buraco,
 * presa à área e à janela. Passo 1: a coluna fica no ideal e a pilha vai embaixo, senão em cima.
 * Passo 2: nenhum lado cabe no ideal, então a coluna desliza até o bloco caber, embaixo antes de em
 * cima. Passo 3: nenhuma posição cabe na área (buraco fora dela): sem lado, regra pela janela. O
 * buraco não se move; só o bloco desliza.
 */
private fun columnSlide(
    windowSize: DpSize,
    columnSize: DpSize,
    handleSize: Dp,
    gap: Dp,
    holeCenterYPx: Int,
    workArea: HudPxRect
): HudColumnSlide {
    val windowHeight = windowSize.height.value
    val columnHeight = columnSize.height.value
    val gapValue = gap.value
    val stackHeight = handleSize.value * 2 + gapValue
    val windowTopPx = holeCenterYPx - windowHeight / 2f
    val areaTop = maxOf(0f, workArea.yPx - windowTopPx)
    val areaBottom = minOf(windowHeight, workArea.yPx + workArea.heightPx - windowTopPx)
    val ideal = (windowHeight - columnHeight) / 2f
    val high = minOf(windowHeight - columnHeight, areaBottom - columnHeight)
    val start = maxOf(0f, minOf(maxOf(ideal, areaTop), high))

    if (start + columnHeight + gapValue + stackHeight <= areaBottom) return HudColumnSlide(start.dp, false)
    if (start - gapValue - stackHeight >= areaTop) return HudColumnSlide(start.dp, true)

    val belowHigh = areaBottom - columnHeight - gapValue - stackHeight
    if (areaTop <= belowHigh) {
        return HudColumnSlide(minOf(maxOf(start, areaTop), belowHigh).dp, false)
    }
    val aboveLow = areaTop + gapValue + stackHeight
    val aboveHigh = areaBottom - columnHeight
    if (aboveLow <= aboveHigh) {
        return HudColumnSlide(minOf(maxOf(start, aboveLow), aboveHigh).dp, true)
    }
    return HudColumnSlide(start.dp, null)
}

/**
 * Topo do balão do modo coluna: centrado no anel em foco ([ringCenterY], em dp de janela) e preso
 * à área útil da tela ([areaTop]..[areaBottom], em dp de janela) e à própria janela (A11k). Se o
 * balão for mais alto que a área, o topo fica em [areaTop] (o `coerceIn` não aceita `min > max`).
 */
internal fun hudFloatingBalloonTop(
    ringCenterY: Dp,
    balloonHeight: Dp,
    windowHeight: Dp,
    areaTop: Dp,
    areaBottom: Dp
): Dp {
    val ideal = ringCenterY - balloonHeight / 2f
    val low = maxOf(0.dp, areaTop)
    val high = (minOf(windowHeight, areaBottom) - balloonHeight).coerceAtLeast(low)
    return ideal.coerceIn(low, high)
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
