package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val TRAY_USAGE_RING_KEY = "trayUsageRing"

/**
 * "Anel de uso no ícone da bandeja" (issue #328). Default `false`: o ícone de
 * sempre continua sendo o que o usuário reconhece, e o anel é escolha dele.
 */
internal fun readPersistedTrayUsageRing(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(TRAY_USAGE_RING_KEY, false)
}

internal fun persistTrayUsageRing(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(TRAY_USAGE_RING_KEY, enabled)
}
