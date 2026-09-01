package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val HUD_MODE_KEY = "hudMode"

/**
 * Barra HUD (issue #164): terceiro chrome da janela principal, ainda mais
 * discreto que o modo somente cards — uma faixa de 24dp ancorada no topo da
 * tela, sem título, sem cards.
 *
 * Mesmo armazenamento das demais preferências de moldura (registro no
 * Windows, plist no macOS, via `PreferencesSettings`) e **não**
 * `~/.usage-monitor/`: ali ficam os segredos do time, e a moldura da janela
 * não é.
 *
 * Default `false`: o modo esconde toda a janela normal — inclusive o botão
 * de fechar e a engrenagem —, e um app que abre pela primeira vez direto
 * nessa faixa não tem como ser explicado.
 */
internal fun readPersistedHudMode(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(HUD_MODE_KEY, false)
}

internal fun persistHudMode(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(HUD_MODE_KEY, enabled)
}
