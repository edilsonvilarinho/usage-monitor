package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.DEFAULT_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MAX_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.MIN_UI_SCALE_PERCENT
import com.usagemonitor.domain.entity.UI_SCALE_STEP_PERCENT
import kotlin.math.roundToInt

private const val UI_SCALE_KEY = "uiScalePercent"

internal fun readPersistedUiScalePercent(settings: PreferencesSettings): Int {
    return clampUiScalePercent(
        settings.getInt(UI_SCALE_KEY, DEFAULT_UI_SCALE_PERCENT)
    )
}

internal fun persistUiScalePercent(settings: PreferencesSettings, percent: Int) {
    settings.putInt(UI_SCALE_KEY, clampUiScalePercent(percent))
}

/**
 * Distingue "nunca escolheu" de "escolheu o padrão".
 *
 * É o que permite corrigir a janela **uma vez** na primeira execução depois da
 * atualização: até esta versão a escala era 100, e a janela persistida passaria a
 * mostrar menos conteúdo com o padrão de 115.
 */
internal fun hasPersistedUiScale(settings: PreferencesSettings): Boolean {
    return settings.hasKey(UI_SCALE_KEY)
}

/**
 * Limita à faixa e prende à grade do passo.
 *
 * O valor gravado é o mesmo que o slider oferece: um número fora da grade —
 * vindo de uma versão anterior ou de edição manual do registro — voltaria para a
 * tela como uma posição que o controle não consegue reproduzir.
 */
internal fun clampUiScalePercent(percent: Int): Int {
    val snapped = (percent.toFloat() / UI_SCALE_STEP_PERCENT).roundToInt() * UI_SCALE_STEP_PERCENT
    return snapped.coerceIn(MIN_UI_SCALE_PERCENT, MAX_UI_SCALE_PERCENT)
}

/** Fator multiplicativo da escala, para tamanhos de janela em `Dp`. */
internal fun uiScaleFactor(percent: Int): Float {
    return clampUiScalePercent(percent) / 100f
}
