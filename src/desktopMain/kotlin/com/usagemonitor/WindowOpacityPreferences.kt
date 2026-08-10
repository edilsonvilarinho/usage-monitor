package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.MAX_WINDOW_OPACITY_PERCENT
import com.usagemonitor.domain.entity.MIN_WINDOW_OPACITY_PERCENT
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Window

private const val WINDOW_OPACITY_KEY = "windowOpacityPercent"

internal fun readPersistedWindowOpacityPercent(settings: PreferencesSettings): Int {
    return clampWindowOpacityPercent(
        settings.getInt(WINDOW_OPACITY_KEY, MAX_WINDOW_OPACITY_PERCENT)
    )
}

internal fun persistWindowOpacityPercent(settings: PreferencesSettings, percent: Int) {
    settings.putInt(WINDOW_OPACITY_KEY, clampWindowOpacityPercent(percent))
}

internal fun clampWindowOpacityPercent(percent: Int): Int {
    return percent.coerceIn(MIN_WINDOW_OPACITY_PERCENT, MAX_WINDOW_OPACITY_PERCENT)
}

/**
 * Translucidez uniforme depende do GraphicsDevice.
 * Em X11 sem compositor, por exemplo, TRANSLUCENT não é suportado.
 */
internal fun isWindowOpacitySupported(): Boolean {
    return runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)
    }.getOrDefault(false)
}

/** Não propaga falha: opacidade é cosmética e nunca deve derrubar a janela. */
internal fun applyWindowOpacity(window: Window, percent: Int) {
    runCatching {
        window.opacity = clampWindowOpacityPercent(percent) / 100f
    }
}
