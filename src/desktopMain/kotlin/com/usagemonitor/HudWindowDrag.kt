package com.usagemonitor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.MouseInfo

/**
 * A matemática do arrasto da HUD, fora do host.
 *
 * Saiu de `HudWindow.kt` quando `HudWindowHost` encostou no limite de 300 linhas
 * (#426). O ganho não é só o tamanho: aqui as duas contas do gesto — para onde a
 * janela anda a cada tique e em que borda ela encaixa ao soltar — viram funções
 * puras, e passam a caber num teste. Antes elas só existiam dentro de lambdas da
 * composição, onde nenhum teste as alcançava.
 */

/**
 * A posição nova da janela de arrasto depois de um tique do mouse, ou `null`
 * quando falta medida (o ponteiro não foi lido, o gesto não começou).
 *
 * **Incremental, nunca acumulado:** somar o deslocamento total desde o início
 * deixaria o notch preso na borda, porque o excedente de um arrasto para fora da
 * tela nunca seria descartado (issue #288).
 */
internal fun hudDragStep(
    previous: java.awt.Point?,
    current: java.awt.Point?,
    position: DpOffset?,
    size: DpSize,
    /** A tela **inteira** do monitor sob o ponteiro: presa ao primário, o notch nunca cruza a divisa. */
    workArea: ScreenWorkArea,
    /** Retângulo, relativo à janela, que tem de ficar na tela. Sem ele, a janela inteira é presa. */
    anchor: DpRect? = null
): DpOffset? {
    if (previous == null || current == null || position == null) {
        return null
    }
    val x = position.x + (current.x - previous.x).dp
    val y = position.y + (current.y - previous.y).dp
    if (anchor != null) {
        // Modo flutuante (#619, A08f1): o buraco é que fica na tela; a janela inteira pode passar da borda.
        return DpOffset(
            x = anchoredWindowOrigin(x, anchor.left, anchor.right - anchor.left, workArea.x, workArea.size.width),
            y = anchoredWindowOrigin(y, anchor.top, anchor.bottom - anchor.top, workArea.y, workArea.size.height)
        )
    }
    val moved = fitWindowPosition(x = x, y = y, size = size, workArea = workArea)
    return DpOffset(moved.x, moved.y)
}

/**
 * Origem da janela que deixa o trecho `[anchorStart, anchorStart + anchorSize]` (relativo à janela) dentro do
 * eixo `[origin, origin + available]`. Eixo sem medida fica livre; trecho maior que o eixo prende no começo dele,
 * como em `fitWindowPosition`.
 */
private fun anchoredWindowOrigin(windowOrigin: Dp, anchorStart: Dp, anchorSize: Dp, origin: Dp, available: Dp): Dp {
    if (!windowOrigin.value.isFinite() || !anchorStart.value.isFinite() || !anchorSize.value.isFinite() ||
        !origin.value.isFinite() || !available.value.isFinite()
    ) {
        return windowOrigin
    }
    val maxAnchor = maxOf(origin, origin + available - anchorSize)
    val anchorNow = windowOrigin + anchorStart
    return maxOf(origin, minOf(anchorNow, maxAnchor)) - anchorStart
}

/**
 * Retângulo do buraco minimizado, em dp de janela, centrado na janela de arrasto (modo flutuante, #619).
 */
internal fun hudFloatingHoleAnchor(windowSize: DpSize, holeDiameter: Dp): DpRect {
    val left = (windowSize.width - holeDiameter) / 2f
    val top = (windowSize.height - holeDiameter) / 2f
    return DpRect(left, top, left + holeDiameter, top + holeDiameter)
}

/**
 * Onde o notch encaixa quando o gesto termina: a borda mais próxima do
 * **centro** dele, no monitor em que foi solto.
 *
 * O encaixe é na área **útil** do monitor, e não nos limites: a barra de tarefas
 * também é *topmost* e voltaria para cima do notch a cada clique nela (#288).
 */
internal fun hudDragLanding(
    position: DpOffset,
    size: DpSize,
    /** Usada quando o ponteiro não caiu em monitor nenhum. */
    fallbackArea: ScreenWorkArea,
    target: ScreenInfo?
): HudPlacement {
    return nearestHudPlacement(
        centerX = position.x + size.width / 2,
        centerY = position.y + size.height / 2,
        area = target?.workArea ?: fallbackArea
    )
}

/**
 * O gesto de arrastar a HUD: se está em curso, onde a janela de arrasto está e
 * onde o ponteiro estava no último tique.
 *
 * Estado e contas moram juntos aqui desde que `HudWindowHost` passou do limite
 * de 300 linhas (#426). O ponteiro **não** é estado de composição: ninguém o
 * desenha, e guardá-lo num `mutableStateOf` recomporia a tela a cada pixel do
 * mouse.
 */
@Stable
internal class HudDragState(
    /** O ponteiro na tela; `null` quando a leitura falha. Parâmetro para o teste. */
    private val readPointer: () -> java.awt.Point? = { runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() }
) {
    /** Em curso: o notch sai da borda e anda livre. */
    var dragging by mutableStateOf(false)
        private set

    /** A posição da janela de arrasto, em dp de janela. Nula fora do gesto. */
    var windowPosition by mutableStateOf<DpOffset?>(null)
        private set

    private var pointer: java.awt.Point? = null

    /**
     * O gesto parte da janela de arrasto já encaixada, com o notch no ponto da
     * tela em que ele está — nunca da origem da janela aberta.
     */
    fun begin(start: DpOffset) {
        dragging = true
        windowPosition = start
        pointer = readPointer()
        HudDragProbe.begin()
    }

    /** Um tique do mouse. [workArea] é a tela **inteira** do monitor sob ele. */
    fun moveTo(size: DpSize, workArea: ScreenWorkArea, anchor: DpRect? = null) {
        val current = readPointer()
        val moved = hudDragStep(pointer, current, windowPosition, size, workArea, anchor)
        if (moved != null) {
            pointer = current
            windowPosition = moved
        }
    }

    /** Encerra e devolve onde soltou; `null` quando o gesto não tinha posição. */
    fun finish(): DpOffset? {
        val position = windowPosition
        HudDragProbe.finish(position)
        pointer = null
        windowPosition = null
        dragging = false
        return position
    }
}
