package com.usagemonitor

/*
 * A posição da HUD solta (issue #619, A05). Funções puras, sem janela AWT: quem chama
 * (`HudWindow`, em A08) aplica o retângulo na janela. Medida no Windows (#340); Linux sem medição (#623).
 */

/** Retângulo em pixels de tela: `xPx`/`yPx` é o canto superior esquerdo. */
internal data class HudPxRect(val xPx: Int, val yPx: Int, val widthPx: Int, val heightPx: Int)

/**
 * Posição da janela solta em px de tela. [window] é a origem real (já presa dentro da área útil);
 * [holeCenterXPx]/[holeCenterYPx] são o centro do buraco **dentro da janela**, em px relativos à
 * origem real: `âncora na tela − origem real`.
 */
internal data class HudFloatingPlacementPx(
    val window: HudPxRect,
    val holeCenterXPx: Int,
    val holeCenterYPx: Int
)

/**
 * Posição da janela solta (#625). A âncora (buraco ou campo, pelo raio) é presa na área útil. A
 * origem da janela é a âncora menos metade do tamanho, presa em `[área.origem, área.origem + área.tamanho − janela]`:
 * com a janela perto da borda ela fica deslocada do centro pedido e o buraco anda dentro dela.
 * Janela maior que o eixo da área: a origem vai à origem da área.
 */
internal fun hudFloatingBounds(
    center: HudFloatCenter?,
    notchCenter: HudFloatCenter,
    windowWidthPx: Int,
    windowHeightPx: Int,
    workArea: HudPxRect,
    anchorRadiusPx: Int
): HudFloatingPlacementPx {
    val effectiveCenter = center ?: notchCenter
    val anchorX = clampAnchor(effectiveCenter.xPx, workArea.xPx, workArea.widthPx, anchorRadiusPx)
    val anchorY = clampAnchor(effectiveCenter.yPx, workArea.yPx, workArea.heightPx, anchorRadiusPx)
    val originX = clampWindowOrigin(anchorX - windowWidthPx / 2, workArea.xPx, workArea.widthPx, windowWidthPx)
    val originY = clampWindowOrigin(anchorY - windowHeightPx / 2, workArea.yPx, workArea.heightPx, windowHeightPx)
    return HudFloatingPlacementPx(
        window = HudPxRect(originX, originY, windowWidthPx, windowHeightPx),
        holeCenterXPx = anchorX - originX,
        holeCenterYPx = anchorY - originY
    )
}

/**
 * Prende o centro de um eixo para que a âncora (raio [radius]) caiba entre a origem e a borda
 * final da área. Âncora maior que o eixo: sem intervalo válido, o centro vai a origem + raio.
 */
private fun clampAnchor(center: Int, origin: Int, areaLength: Int, radius: Int): Int {
    val lowest = origin + radius
    val highest = origin + areaLength - radius
    if (highest < lowest) {
        return lowest
    }
    return center.coerceIn(lowest, highest)
}

/**
 * Prende a origem da janela em um eixo para que ela caiba entre a origem da área e a borda final
 * dela. Janela maior que o eixo: sem intervalo válido, a origem vai à origem da área.
 */
private fun clampWindowOrigin(ideal: Int, origin: Int, areaLength: Int, windowLength: Int): Int {
    val lowest = origin
    val highest = origin + areaLength - windowLength
    if (highest < lowest) {
        return lowest
    }
    return ideal.coerceIn(lowest, highest)
}

/**
 * O modo flutuante existe no Windows e no Linux. No Windows o recorte e o arrasto foram medidos
 * (#340, #619). O Linux foi liberado por decisão do dono na #623, **sem medição**: o recorte só
 * vale sem alfa (#426) e a área transparente da janela solta pode engolir cliques. Medir no beta.
 * No macOS o botão não aparece.
 */
internal fun hudFloatingSupported(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.WINDOWS || platform == AutoStartManager.Platform.LINUX
