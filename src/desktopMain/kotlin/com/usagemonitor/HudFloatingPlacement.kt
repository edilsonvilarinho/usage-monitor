package com.usagemonitor

/*
 * A posição da HUD solta (issue #619, A05). Funções puras, sem janela AWT: quem chama
 * (`HudWindow`, em A08) aplica o retângulo na janela. Medida no Windows (#340); Linux sem medição (#623).
 */

/** Retângulo em pixels de tela: `xPx`/`yPx` é o canto superior esquerdo. */
internal data class HudPxRect(val xPx: Int, val yPx: Int, val widthPx: Int, val heightPx: Int)

/**
 * Canto superior esquerdo da janela solta. Com [center] salvo, a janela fica centrada nele.
 * Sem [center] (nunca foi posta solta), nasce centrada em [notchCenter], o notch atual.
 *
 * O resultado é prendido dentro de [workArea] e **não encaixa numa borda por conta própria**:
 * uma janela a 20 px da borda fica a 20 px. Janela maior que a área fica no canto superior
 * esquerdo da área.
 */
internal fun hudFloatingBounds(
    center: HudFloatCenter?,
    notchCenter: HudFloatCenter,
    windowWidthPx: Int,
    windowHeightPx: Int,
    workArea: HudPxRect,
    anchorRadiusPx: Int
): HudPxRect {
    val effectiveCenter = center ?: notchCenter
    val anchorX = clampAnchor(effectiveCenter.xPx, workArea.xPx, workArea.widthPx, anchorRadiusPx)
    val anchorY = clampAnchor(effectiveCenter.yPx, workArea.yPx, workArea.heightPx, anchorRadiusPx)

    return HudPxRect(
        xPx = anchorX - windowWidthPx / 2,
        yPx = anchorY - windowHeightPx / 2,
        widthPx = windowWidthPx,
        heightPx = windowHeightPx
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
 * O modo flutuante existe no Windows e no Linux. No Windows o recorte e o arrasto foram medidos
 * (#340, #619). O Linux foi liberado por decisão do dono na #623, **sem medição**: o recorte só
 * vale sem alfa (#426) e a área transparente da janela solta pode engolir cliques. Medir no beta.
 * No macOS o botão não aparece.
 */
internal fun hudFloatingSupported(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.WINDOWS || platform == AutoStartManager.Platform.LINUX
