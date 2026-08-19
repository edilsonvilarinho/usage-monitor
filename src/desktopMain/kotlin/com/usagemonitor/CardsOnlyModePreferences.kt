package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val CARDS_ONLY_MODE_KEY = "cardsOnlyMode"

/**
 * Modo "somente os cards": a janela principal sem barra de título e sem rodapé.
 *
 * Mora no mesmo armazenamento das demais preferências (registro no Windows, plist
 * no macOS, via `PreferencesSettings`) e **não** em `~/.usage-monitor/`: ali ficam
 * a chave do servidor de time e o token de administração, que são segredos; a
 * moldura da janela não é.
 *
 * Default `false`: o modo esconde os controles de fechar e de configurações, e um
 * app que abre pela primeira vez sem eles não tem como ser explicado.
 */
internal fun readPersistedCardsOnlyMode(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(CARDS_ONLY_MODE_KEY, false)
}

internal fun persistCardsOnlyMode(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(CARDS_ONLY_MODE_KEY, enabled)
}
