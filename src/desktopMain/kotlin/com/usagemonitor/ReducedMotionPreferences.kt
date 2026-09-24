package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val REDUCED_MOTION_KEY = "reducedMotion"

/**
 * "Reduzir animações": transições trocam de uma vez e nada gira nem pulsa.
 *
 * Mora em `PreferencesSettings` pela mesma razão do modo somente cards: não é
 * segredo, e `~/.usage-monitor/` é onde ficam as chaves.
 *
 * Default `false`. O sistema operacional tem a própria preferência, mas o JVM
 * não a expõe de forma portátil — ler o registro do Windows, o `gsettings` do
 * GNOME e o `defaults` do macOS seriam três caminhos para uma decisão que o
 * usuário toma em um clique aqui.
 */
internal fun readPersistedReducedMotion(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(REDUCED_MOTION_KEY, false)
}

internal fun persistReducedMotion(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(REDUCED_MOTION_KEY, enabled)
}
