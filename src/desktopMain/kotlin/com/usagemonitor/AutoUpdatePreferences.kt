package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val AUTO_UPDATE_ENABLED_KEY = "autoUpdateEnabled"

/**
 * Atualização automática: baixar a versão nova em segundo plano e aplicá-la ao
 * fechar o app.
 *
 * Mora no mesmo armazenamento das demais preferências (registro no Windows, via
 * `PreferencesSettings`) e **não** em `~/.usage-monitor/`: ali ficam a chave do
 * servidor de time e o token de administração, que são segredos; um interruptor
 * de comportamento não é.
 *
 * **Default `false`.** São ~120 MB por versão, sem atualização delta, e a
 * substituição dos arquivos acontece sem confirmação no momento em que o usuário
 * fecha a janela. Nada disso pode começar a acontecer porque o app foi atualizado
 * — quem liga é quem aceita.
 */
internal fun readPersistedAutoUpdateEnabled(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(AUTO_UPDATE_ENABLED_KEY, false)
}

internal fun persistAutoUpdateEnabled(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(AUTO_UPDATE_ENABLED_KEY, enabled)
}
