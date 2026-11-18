package com.usagemonitor

/*
 * A posição da HUD solta (issue #619, A05). Funções puras, sem janela AWT: quem chama
 * (`HudWindow`, em A08) aplica o retângulo na janela. Medida só no Windows (#340).
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
    workArea: HudPxRect
): HudPxRect {
    val effectiveCenter = center ?: notchCenter
    val desiredX = effectiveCenter.xPx - windowWidthPx / 2
    val desiredY = effectiveCenter.yPx - windowHeightPx / 2

    val x = clampAxis(desiredX, workArea.xPx, workArea.widthPx, windowWidthPx)
    val y = clampAxis(desiredY, workArea.yPx, workArea.heightPx, windowHeightPx)

    return HudPxRect(xPx = x, yPx = y, widthPx = windowWidthPx, heightPx = windowHeightPx)
}

/** Prende um eixo entre a origem e a borda final da área; janela maior que o eixo fica na origem. */
private fun clampAxis(desired: Int, origin: Int, areaLength: Int, windowLength: Int): Int {
    val lastStart = origin + areaLength - windowLength
    if (lastStart < origin) {
        return origin
    }
    return desired.coerceIn(origin, lastStart)
}

/**
 * O modo flutuante só existe no Windows (#340): é a única plataforma em que o recorte e o
 * arrasto da janela foram medidos. Nos outros sistemas o botão nem aparece.
 */
internal fun hudFloatingSupported(platform: AutoStartManager.Platform): Boolean =
    platform == AutoStartManager.Platform.WINDOWS
