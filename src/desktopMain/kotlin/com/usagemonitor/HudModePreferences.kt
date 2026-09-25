package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings

private const val HUD_MODE_KEY = "hudMode"
private const val HUD_DEFAULT_PENDING_KEY = "hudDefaultPending"

/** A chave que o coletor da janela principal grava na primeira execução. */
private const val MAIN_WINDOW_PLACEMENT_KEY = "windowPlacement"

/**
 * Barra HUD (issue #164): terceiro chrome da janela principal, ainda mais
 * discreto que o modo somente cards — uma notch colado numa borda da tela da
 * tela, sem título, sem cards.
 *
 * Mesmo armazenamento das demais preferências de moldura (registro no
 * Windows, plist no macOS, via `PreferencesSettings`) e **não**
 * `~/.usage-monitor/`: ali ficam os segredos do time, e a moldura da janela
 * não é.
 *
 * Default `false` na leitura, e isso não mudou com a issue #277: quem já usa o
 * app e nunca mexeu no modo continua na janela padrão. A HUD vira padrão só na
 * **instalação nova**, e só depois da primeira coleta — ver
 * [markHudDefaultPendingOnFreshInstall]. Uma instalação nova sobe sem API
 * habilitada, e abrir direto no notch mostraria "Carregando" para sempre, sem
 * ter o que configurar.
 */
internal fun readPersistedHudMode(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(HUD_MODE_KEY, false)
}

internal fun persistHudMode(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(HUD_MODE_KEY, enabled)
}

/**
 * Instalação nova (issue #277): nem o modo nem a geometria da janela principal
 * foram gravados, e não há recibo de atualização. O recibo é o que separa quem
 * atualizou de uma versão antiga — que pode nunca ter gravado `hudMode` — de
 * quem acabou de instalar; é a mesma regra de `ReleaseNotesDecision`.
 *
 * Lida **antes** de qualquer gravação: o coletor da janela grava
 * `windowPlacement` 250ms depois da primeira composição, e a partir daí toda
 * execução parece antiga.
 */
internal fun isFreshInstall(settings: PreferencesSettings, hasUpdateReceipt: Boolean): Boolean {
    return !settings.hasKey(HUD_MODE_KEY) && !settings.hasKey(MAIN_WINDOW_PLACEMENT_KEY) && !hasUpdateReceipt
}

/**
 * Na instalação nova, grava o modo padrão e marca a HUD como pendente; a troca
 * sai na primeira coleta com alguma conta ([hudDefaultShouldSwitch]). Devolve se
 * a troca está pendente — de antes ou de agora. Gravar `hudMode` fecha a porta:
 * a execução seguinte já não é instalação nova.
 */
internal fun markHudDefaultPendingOnFreshInstall(settings: PreferencesSettings, hasUpdateReceipt: Boolean): Boolean {
    if (isFreshInstall(settings, hasUpdateReceipt)) {
        settings.putBoolean(HUD_MODE_KEY, false)
        settings.putBoolean(HUD_DEFAULT_PENDING_KEY, true)
        return true
    }
    return settings.getBoolean(HUD_DEFAULT_PENDING_KEY, false)
}

/** A troca aconteceu, ou o usuário escolheu um modo antes dela: a escolha dele vence. */
internal fun clearHudDefaultPending(settings: PreferencesSettings) {
    settings.remove(HUD_DEFAULT_PENDING_KEY)
}
