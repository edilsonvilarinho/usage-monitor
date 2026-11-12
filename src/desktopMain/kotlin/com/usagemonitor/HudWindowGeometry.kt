package com.usagemonitor

import com.usagemonitor.presentation.ui.hudMinimizeHeldSize

import com.usagemonitor.presentation.ui.HudAccount

/*
 * Medidas da janela da HUD derivadas do lugar dela na tela. Funções puras que saíram de
 * `HudWindowHost` (que estava no teto de 300 linhas do `ArchitectureRulesTest`); nenhuma
 * toca a janela AWT.
 */

/**
 * Tamanhos do notch para a janela e para depois da saída de quem está saindo.
 *
 * A janela mede a lista com quem está saindo, até a vaga fechar; o notch recolhe até a
 * lista sem ela por dentro da janela (K1). Mais que [hudMaxAlongFraction] da borda e a
 * faixa fica compacta (anel + percentual).
 */
internal fun hudWindowSizes(
    accounts: List<HudAccount>,
    placement: HudPlacement,
    screenArea: ScreenWorkArea,
    scale: Float,
    fallbackLabel: String,
    hasUpdateIndicator: Boolean,
    hasUpdateAction: Boolean
): Pair<HudNotchSizes, HudNotchSizes> {
    val edge = placement.edge
    return hudNotchSizesWithDeparture(
        accounts = accounts,
        edge = edge,
        fallbackLabel = fallbackLabel,
        hasUpdateIndicator = hasUpdateIndicator,
        maxAlong = (if (edge.isHorizontal) screenArea.size.width else screenArea.size.height) / scale *
            hudMaxAlongFraction(edge),
        hasUpdateAction = hasUpdateAction,
        maxWindowHeight = screenArea.size.height / scale
    )
}

/**
 * Onde a janela fica. Parada e aberta a janela é a mesma, com o notch no mesmo ponto da
 * tela; quem se ajusta a um canto é o balão. Carregando, a janela é o notch com as alças —
 * simétrico ao longo da borda, então o centro dela continua sendo o do notch, que é o que
 * o encaixe lê.
 */
internal fun hudHostWindowBounds(
    dragging: Boolean,
    placement: HudPlacement,
    sizes: HudNotchSizes,
    composedArea: ScreenWorkArea
): HudWindowBounds {
    return if (dragging) {
        hudDragWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    } else {
        hudDockedWindowBounds(placement.edge, placement.offsetFraction, sizes, composedArea)
    }
}

/**
 * Tamanhos da janela durante a transição de minimizar (#576, A05b): enquanto [moving],
 * segura o maior entre [before] e [after] em todos os campos, igual ao notch; parada, é [after].
 */
internal fun hudHeldNotchSizes(before: HudNotchSizes, after: HudNotchSizes, moving: Boolean): HudNotchSizes {
    if (!moving) return after
    return after.copy(
        collapsed = hudMinimizeHeldSize(before.collapsed, after.collapsed, moving = true),
        expanded = hudMinimizeHeldSize(before.expanded, after.expanded, moving = true),
        balloon = hudMinimizeHeldSize(before.balloon, after.balloon, moving = true),
        withHandles = hudMinimizeHeldSize(before.withHandles, after.withHandles, moving = true),
    )
}
