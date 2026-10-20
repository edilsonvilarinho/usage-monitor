package com.usagemonitor

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

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
    workArea: ScreenWorkArea
): DpOffset? {
    if (previous == null || current == null || position == null) {
        return null
    }
    val moved = fitWindowPosition(
        x = position.x + (current.x - previous.x).dp,
        y = position.y + (current.y - previous.y).dp,
        size = size,
        workArea = workArea
    )
    return DpOffset(moved.x, moved.y)
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
