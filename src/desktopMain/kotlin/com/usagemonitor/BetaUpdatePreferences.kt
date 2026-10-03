package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val RECEIVE_BETA_UPDATES_KEY = "receiveBetaUpdates"

/**
 * Canal beta (issue #355): consultar também as releases marcadas como
 * prerelease no GitHub.
 *
 * Mesmo armazenamento de [readPersistedAutoUpdateEnabled] — interruptor de
 * comportamento, não segredo.
 *
 * **Default `false`.** Beta é versão ainda em teste; ninguém entra no canal
 * porque o app foi atualizado. Desligar não faz downgrade: quem está numa beta
 * fica nela até sair uma estável maior.
 */
internal fun readPersistedReceiveBetaUpdates(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(RECEIVE_BETA_UPDATES_KEY, false)
}

internal fun persistReceiveBetaUpdates(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(RECEIVE_BETA_UPDATES_KEY, enabled)
}
