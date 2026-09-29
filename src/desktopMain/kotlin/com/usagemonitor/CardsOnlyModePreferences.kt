package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val CARDS_ONLY_MODE_KEY = "cardsOnlyMode"

/**
 * O modo "somente os cards" saiu do app: a barra HUD passou a ser a moldura
 * reduzida única. Quem o tinha ligado escolheu a janela mais discreta, e a
 * equivalente hoje é a HUD — cair na janela padrão desfaria a escolha.
 *
 * Roda antes da leitura de `hudMode` e apaga a chave, para a migração valer uma
 * vez só: desligar a HUD depois não pode ser revertido por ela no arranque seguinte.
 */
internal fun migrateCardsOnlyModeToHud(settings: PreferencesSettings) {
    if (!settings.hasKey(CARDS_ONLY_MODE_KEY)) return
    if (settings.getBoolean(CARDS_ONLY_MODE_KEY, false)) {
        persistHudMode(settings, true)
    }
    settings.remove(CARDS_ONLY_MODE_KEY)
}
