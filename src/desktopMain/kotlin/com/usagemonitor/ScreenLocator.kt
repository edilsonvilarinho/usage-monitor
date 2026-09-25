package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Toolkit

/**
 * Um monitor: identificador, limites físicos e área útil (sem a barra de tarefas).
 *
 * Existe por causa da issue #273. Toda medida de tela do app lia o **monitor
 * padrão** (`defaultScreenDevice`, `maximumWindowBounds`): a HUD era presa a ele
 * no arrasto e no encaixe, e as janelas com posição salva num secundário voltavam
 * para o primário ao reabrir. Aqui o monitor passa a ser escolhido pela posição.
 *
 * [id] é o `getIDstring()` do AWT (`\Display1` no Windows). Ele muda quando o Windows
 * renumera os monitores ao reconectar, e por isso a posição salva também guarda os
 * [bounds]: [resolveScreen] tenta o id, depois os limites, e só então o primário.
 */
internal data class ScreenInfo(
    val id: String,
    val bounds: ScreenWorkArea,
    val workArea: ScreenWorkArea
)

/**
 * Os monitores ligados, **o padrão primeiro** — é ele o fallback de todas as
 * funções abaixo. Falha na consulta devolve lista vazia, que todo mundo aqui trata
 * como "sem medida".
 */
internal fun availableScreens(): List<ScreenInfo> {
    return runCatching {
        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val primary = environment.defaultScreenDevice
        val devices = environment.screenDevices.sortedBy { device -> if (device == primary) 0 else 1 }
        devices.map { device -> device.toScreenInfo() }
    }.getOrDefault(emptyList())
}

/** O monitor de um dispositivo AWT — o do ponteiro, o de uma janela. */
internal fun GraphicsDevice.toScreenInfo(): ScreenInfo = defaultConfiguration.toScreenInfo(getIDstring())

internal fun GraphicsConfiguration.toScreenInfo(id: String = device.getIDstring()): ScreenInfo {
    val bounds = bounds
    // Os insets vêm em pixel do mesmo espaço dos limites; a área útil é o que sobra.
    val insets = runCatching { Toolkit.getDefaultToolkit().getScreenInsets(this) }.getOrNull()
    val left = insets?.left ?: 0
    val top = insets?.top ?: 0
    val right = insets?.right ?: 0
    val bottom = insets?.bottom ?: 0
    return ScreenInfo(
        id = id,
        bounds = ScreenWorkArea(
            x = bounds.x.dp,
            y = bounds.y.dp,
            size = DpSize(bounds.width.dp, bounds.height.dp)
        ),
        workArea = ScreenWorkArea(
            x = (bounds.x + left).dp,
            y = (bounds.y + top).dp,
            size = DpSize((bounds.width - left - right).dp, (bounds.height - top - bottom).dp)
        )
    )
}

/** O monitor que contém o ponto; `null` quando nenhum contém. */
internal fun screenAt(screens: List<ScreenInfo>, x: Dp, y: Dp): ScreenInfo? {
    return screens.firstOrNull { screen -> screen.bounds.contains(x, y) }
}

/**
 * O monitor com a maior área em comum com o retângulo. Janela atravessando dois
 * monitores fica no que tem mais dela; retângulo fora de todos devolve `null`, e
 * quem chama cai no primário.
 */
internal fun screenForRect(screens: List<ScreenInfo>, x: Dp, y: Dp, size: DpSize): ScreenInfo? {
    val width = size.width.value.takeIf { value -> value.isFinite() && value > 0f } ?: 1f
    val height = size.height.value.takeIf { value -> value.isFinite() && value > 0f } ?: 1f
    return screens
        .map { screen -> screen to screen.bounds.overlap(x.value, y.value, width, height) }
        .filter { (_, area) -> area > 0f }
        .maxByOrNull { (_, area) -> area }
        ?.first
}

/**
 * O monitor gravado, de volta: pelo id; se o id não existe mais ou aponta para
 * outros limites, pelos limites — o Windows renumera ao reconectar; e sem nenhum
 * dos dois, `null`, para quem chama cair no primário **sem apagar** o que estava
 * gravado. O monitor que sumiu pode voltar.
 */
internal fun resolveScreen(screens: List<ScreenInfo>, savedId: String?, savedBounds: ScreenWorkArea?): ScreenInfo? {
    val byId = savedId?.let { id -> screens.firstOrNull { screen -> screen.id == id } }
    if (byId != null && (savedBounds == null || byId.bounds == savedBounds)) {
        return byId
    }
    val byBounds = savedBounds?.let { bounds -> screens.firstOrNull { screen -> screen.bounds == bounds } }
    return byBounds ?: byId
}

/**
 * A área útil onde uma janela com posição salva deve ser encaixada: a do monitor
 * que contém o retângulo, e não a do primário. Sem posição (janela que nunca foi
 * movida), ou com o retângulo fora de todos os monitores, vale o [fallback].
 */
internal fun workAreaForPosition(
    x: Dp?,
    y: Dp?,
    size: DpSize,
    fallback: ScreenWorkArea,
    screens: List<ScreenInfo> = availableScreens()
): ScreenWorkArea {
    if (x == null || y == null) {
        return fallback
    }
    return screenForRect(screens, x, y, size)?.workArea ?: fallback
}

private fun ScreenWorkArea.contains(x: Dp, y: Dp): Boolean {
    if (!this.x.value.isFinite() || !this.y.value.isFinite()) {
        return false
    }
    return x >= this.x && x < this.x + size.width && y >= this.y && y < this.y + size.height
}

private fun ScreenWorkArea.overlap(x: Float, y: Float, width: Float, height: Float): Float {
    val left = this.x.value
    val top = this.y.value
    val right = left + size.width.value
    val bottom = top + size.height.value
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
        return 0f
    }
    val overlapWidth = minOf(right, x + width) - maxOf(left, x)
    val overlapHeight = minOf(bottom, y + height) - maxOf(top, y)
    return if (overlapWidth > 0f && overlapHeight > 0f) overlapWidth * overlapHeight else 0f
}
