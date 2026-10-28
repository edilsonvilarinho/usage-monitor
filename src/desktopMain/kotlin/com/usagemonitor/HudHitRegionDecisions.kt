package com.usagemonitor

import androidx.compose.ui.unit.DpRect
import kotlin.math.ceil
import kotlin.math.floor

/*
 * As decisões do recorte de clique da HUD, fora de `HudWindowEffects.kt`. Aquele
 * arquivo é cola de janela (efeitos da composição e o aplicador de `Window.shape`) e
 * sai do Kover por nome (onda 6.5 do plano `cobertura-95-execucao.md`); o que decide
 * alguma coisa mora aqui e segue contado e testado.
 */

/** Quando a HUD recorta a área de clique da janela parada por `Window.shape`. */
internal enum class HudHitRegionMode {
    /** Windows: medido na #294 (C11), é o comportamento normal da janela parada. */
    ALWAYS,

    /**
     * Linux: **só** quando a janela está sem alfa. Com a transparência
     * funcionando — o caso normal depois do reparo da #426 — `Window.shape` não
     * é tocado, e o balão cortado da #340 não tem como voltar. Sem alfa, a
     * janela pintaria 442×557 de preto sobre tudo, engolindo clique; recortada
     * ao notch, o pior caso vira um notch sozinho.
     */
    ONLY_WITHOUT_ALPHA,

    /** macOS e o resto: nunca foi medido, e fica do lado seguro. */
    NEVER
}

/**
 * O recorte por plataforma. Era um booleano "só Windows" (#340) e virou três
 * estados quando o Linux ganhou o recorte de emergência (#426).
 */
internal fun hudHitRegionMode(platform: AutoStartManager.Platform): HudHitRegionMode = when (platform) {
    AutoStartManager.Platform.WINDOWS -> HudHitRegionMode.ALWAYS
    AutoStartManager.Platform.LINUX -> HudHitRegionMode.ONLY_WITHOUT_ALPHA
    AutoStartManager.Platform.MACOS, AutoStartManager.Platform.OTHER -> HudHitRegionMode.NEVER
}

/** O recorte vale **agora**, com o que se sabe do alfa da janela. */
internal fun hudHitRegionActive(mode: HudHitRegionMode, hasAlpha: Boolean): Boolean = when (mode) {
    HudHitRegionMode.ALWAYS -> true
    HudHitRegionMode.ONLY_WITHOUT_ALPHA -> !hasAlpha
    HudHitRegionMode.NEVER -> false
}

/** Pixels da AWT a partir do retângulo em dp da composição. */
internal fun DpRect.toAwtRectangle(scale: Float): java.awt.Rectangle {
    val left = floor(this.left.value * scale).toInt()
    val top = floor(this.top.value * scale).toInt()
    val right = ceil(this.right.value * scale).toInt()
    val bottom = ceil(this.bottom.value * scale).toInt()
    return java.awt.Rectangle(left, top, right - left, bottom - top)
}

/**
 * O que fazer com `window.shape`: [touch] falso é **não mexer**, que é diferente
 * de mexer passando `null`.
 */
internal data class HudHitRegionTarget(
    val touch: Boolean,
    val shape: java.awt.Rectangle?
)

/**
 * A decisão do recorte, separada da janela para caber num teste.
 *
 * Três casos. Com [requested], aplica — é o recorte do notch parado.
 * Sem [requested] e sem nada aplicado antes, **não toca**: é o Linux com alfa,
 * onde o recorte nunca valeu. Sem [requested] mas com algo aplicado, limpa — e
 * aí [clearWithFullBounds] decide entre o `null` (Windows, onde ele limpa) e o
 * retângulo do tamanho da janela (X11, onde o `null` não limpa, #340).
 *
 * O caso do meio custou uma regressão medida na #426: limpar o que nunca foi
 * aplicado punha um retângulo do tamanho da janela **na primeira composição**,
 * antes de as contas chegarem e a janela crescer. O retângulo ficava congelado
 * naquele tamanho e cortava a pintura: a barra aparecia com o anel de cima e
 * nada embaixo.
 */
internal fun hudHitRegionTarget(
    requested: java.awt.Rectangle?,
    hasApplied: Boolean,
    clearWithFullBounds: Boolean,
    windowWidth: Int,
    windowHeight: Int
): HudHitRegionTarget {
    if (requested != null) {
        return HudHitRegionTarget(touch = true, shape = requested)
    }
    if (!hasApplied) {
        return HudHitRegionTarget(touch = false, shape = null)
    }
    if (!clearWithFullBounds || windowWidth <= 0 || windowHeight <= 0) {
        return HudHitRegionTarget(touch = true, shape = null)
    }
    return HudHitRegionTarget(touch = true, shape = java.awt.Rectangle(0, 0, windowWidth, windowHeight))
}
