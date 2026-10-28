package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.usagemonitor.presentation.ui.isCosmicDesktop
import java.awt.Dimension
import java.awt.Point
import kotlin.math.roundToInt

/*
 * As decisões da moldura da HUD, fora de `HudWindowFrame.kt`. Aquele arquivo é cola
 * de janela (janela AWT construída à mão, limites aplicados, descarte) e sai do Kover
 * por nome (onda 6.5 do plano `cobertura-95-execucao.md`); o que decide alguma coisa
 * mora aqui e segue contado e testado. `HudWindowRebirth` é classe própria e fica lá.
 */

/**
 * O tamanho que a janela AWT deve ter, em pixels; `null` se a HUD não está
 * flutuando ou se a medida não tem área. Função pura: a conta de arredondamento
 * e a regra de "não aplicar zero" ficam fora da janela.
 */
internal fun hudWindowTargetSize(floating: Boolean, size: DpSize): Dimension? {
    if (!floating) {
        return null
    }
    val dimension = Dimension(size.width.value.roundToInt(), size.height.value.roundToInt())
    return dimension.takeIf { it.width > 0 && it.height > 0 }
}

/**
 * A posição que a janela AWT deve ter, em pixels; `null` fora de HUD flutuante ou
 * se a posição não é absoluta — posição relativa não é pedida à janela.
 */
internal fun hudWindowTargetLocation(floating: Boolean, position: WindowPosition): Point? {
    if (!floating || position !is WindowPosition.Absolute) {
        return null
    }
    return Point(position.x.value.roundToInt(), position.y.value.roundToInt())
}

/**
 * Se a HUD renasce no monitor onde o **compositor** pôs a janela, em vez do
 * monitor gravado (issue #430).
 *
 * **Só no COSMIC**, que é onde o defeito foi visto: ele ignora a posição pedida
 * na abertura (`pedido 404,0 obtido 731,163` em 12 de 13 aberturas do relator da
 * #416). No KWin, medido na #426, a posição é respeitada e o renascimento no
 * monitor gravado é o certo; no GNOME e no Pantheon nada foi medido. Liberar
 * outro compositor é decisão com medição (regra do #340).
 *
 * [desktop] é o `XDG_CURRENT_DESKTOP` verbatim, uma lista separada por `:`.
 */
internal fun hudFollowsCompositorScreen(platform: AutoStartManager.Platform, desktop: String?): Boolean =
    platform == AutoStartManager.Platform.LINUX && isCosmicDesktop(desktop)

/**
 * O tipo da janela da HUD (issue #434).
 *
 * **No COSMIC, `POPUP`**, que o AWT do X11 cria *override-redirect*: o
 * compositor não a gerencia. Gerenciada, ela ignora toda posição pedida — abre
 * fora do topo e não arrasta —, ganha a borda de janela ativa em volta do
 * retângulo transparente e fica atrás das outras, porque o `alwaysOnTop` do AWT
 * não chega ao COSMIC (`_NET_WM_STATE` vazio). Medido no `cosmic-comp` 1.10.0
 * aninhado: a `POPUP` fica onde é pedida e onde é movida, por cima, sem borda,
 * com 32 bits, e recebe clique e tecla depois do clique.
 *
 * Fora do COSMIC, `NORMAL`, o de sempre: no KWin a janela gerenciada está
 * medida (#426), e nos outros nada foi medido (regra do #340).
 */
internal fun hudWindowType(platform: AutoStartManager.Platform, desktop: String?): java.awt.Window.Type =
    if (platform == AutoStartManager.Platform.LINUX && isCosmicDesktop(desktop)) {
        java.awt.Window.Type.POPUP
    } else {
        java.awt.Window.Type.NORMAL
    }

/**
 * O monitor em que a janela deve renascer quando não é o configurado; `null`
 * mantém o configurado (issue #430).
 *
 * É o monitor com a maior parte da janela ([screenForRect], a mesma regra de
 * "monitor de uma janela" do resto do app). Renascer ali com a configuração e a
 * posição encaixada **daquele** monitor é o que converge: renascer com a
 * configuração de um monitor e a posição de outro é a armadilha da A8 da #426.
 */
internal fun hudRebirthScreen(
    followsCompositor: Boolean,
    configuredScreenId: String?,
    actualBounds: java.awt.Rectangle?,
    screens: List<ScreenInfo>
): ScreenInfo? {
    if (!followsCompositor || actualBounds == null) {
        return null
    }
    val actual = screenForRect(
        screens,
        actualBounds.x.dp,
        actualBounds.y.dp,
        DpSize(actualBounds.width.dp, actualBounds.height.dp)
    ) ?: return null
    return actual.takeIf { screen -> screen.id != configuredScreenId }
}
