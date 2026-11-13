package com.usagemonitor.presentation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight

/**
 * Estilo de leitura da HUD por tema (A04, #590). Só o claro é reforçado; o escuro
 * devolve o que a HUD já pintava. Peso `null` = herda o da `TextStyle`.
 */
internal data class HudReadingStyle(
    val prefixColor: Color,
    val prefixWeight: FontWeight?,
    val valueWeight: FontWeight?,
    val debrisAlphaScale: Float,
)

/** Claro pela luminância da superfície, como o resto da HUD decide. */
internal fun hudIsLight(surface: Color): Boolean = surface.luminance() >= 0.5f

/**
 * Claro: rótulo em `onSurface` a 78% com Medium, valor em SemiBold, tracejado
 * sobe por 1,6. Escuro: rótulo em `onSurfaceVariant` com peso herdado, tracejado
 * como está.
 */
internal fun hudReadingStyle(surface: Color, onSurface: Color, onSurfaceVariant: Color): HudReadingStyle {
    if (!hudIsLight(surface)) {
        return HudReadingStyle(prefixColor = onSurfaceVariant, prefixWeight = null, valueWeight = null, debrisAlphaScale = 1f)
    }
    return HudReadingStyle(
        prefixColor = onSurface.copy(alpha = 0.78f),
        prefixWeight = FontWeight.Medium,
        valueWeight = FontWeight.SemiBold,
        debrisAlphaScale = 1.6f,
    )
}
