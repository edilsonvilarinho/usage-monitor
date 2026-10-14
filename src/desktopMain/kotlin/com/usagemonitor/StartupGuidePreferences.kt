package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import kotlinx.datetime.LocalDate

private const val ONBOARDING_COMPLETED_VERSION_KEY = "onboardingCompletedVersion"
private const val STARTUP_TIPS_ENABLED_KEY = "startupTipsEnabled"
private const val STARTUP_TIP_LAST_SHOWN_DATE_KEY = "startupTipLastShownDate"
private const val STARTUP_TIP_NEXT_INDEX_KEY = "startupTipNextIndex"

/**
 * Marcas do guia de introdução e da dica do dia (#411).
 *
 * Em `PreferencesSettings`, como a marca das Novidades, e não em
 * `~/.usage-monitor/`: nenhuma é segredo. O armazenamento muda por sistema
 * (registro no Windows, `prefs.xml` no Linux, plist no macOS), o código não.
 *
 * A versão do guia é guardada, e não um booleano, pelo mesmo motivo da marca das
 * Novidades: diz **em que versão** o guia foi visto, e um guia novo no futuro
 * pode comparar sem que alguém precise limpar a marca.
 */
internal fun readOnboardingCompletedVersion(settings: PreferencesSettings): String? {
    return settings.getStringOrNull(ONBOARDING_COMPLETED_VERSION_KEY)?.takeIf { it.isNotBlank() }
}

internal fun persistOnboardingCompletedVersion(settings: PreferencesSettings, version: String) {
    settings.putString(ONBOARDING_COMPLETED_VERSION_KEY, version)
}

/** Ligadas por padrão: quem nunca abriu as Configurações é quem mais precisa delas. */
internal fun readStartupTipsEnabled(settings: PreferencesSettings): Boolean {
    return settings.getBoolean(STARTUP_TIPS_ENABLED_KEY, true)
}

internal fun persistStartupTipsEnabled(settings: PreferencesSettings, enabled: Boolean) {
    settings.putBoolean(STARTUP_TIPS_ENABLED_KEY, enabled)
}

/** Data em ISO (`2026-10-13`). Valor ilegível conta como nunca mostrada. */
internal fun readStartupTipLastShownDate(settings: PreferencesSettings): LocalDate? {
    val stored = settings.getStringOrNull(STARTUP_TIP_LAST_SHOWN_DATE_KEY) ?: return null
    return runCatching { LocalDate.parse(stored) }.getOrNull()
}

internal fun persistStartupTipLastShownDate(settings: PreferencesSettings, date: LocalDate) {
    settings.putString(STARTUP_TIP_LAST_SHOWN_DATE_KEY, date.toString())
}

internal fun readStartupTipNextIndex(settings: PreferencesSettings): Int? {
    return settings.getIntOrNull(STARTUP_TIP_NEXT_INDEX_KEY)
}

internal fun persistStartupTipNextIndex(settings: PreferencesSettings, index: Int) {
    settings.putInt(STARTUP_TIP_NEXT_INDEX_KEY, index)
}
