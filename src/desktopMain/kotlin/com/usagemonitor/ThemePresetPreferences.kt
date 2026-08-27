package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.presentation.ui.theme.AppThemePreset

internal const val THEME_PRESET_KEY = "themePreset"
internal const val LEGACY_IS_DARK_KEY = "isDark"

/** Lê o preset novo e cai no tema equivalente das versões anteriores. */
internal fun readPersistedThemePreset(settings: PreferencesSettings): AppThemePreset {
    return AppThemePreset.fromStorage(settings.getStringOrNull(THEME_PRESET_KEY))
        ?: AppThemePreset.fromLegacyMode(settings.getBoolean(LEGACY_IS_DARK_KEY, true))
}

/** Grava o preset e mantém a preferência booleana para downgrade seguro. */
internal fun persistThemePreset(settings: PreferencesSettings, preset: AppThemePreset) {
    settings.putString(THEME_PRESET_KEY, preset.storageName)
    settings.putBoolean(LEGACY_IS_DARK_KEY, preset.isDark)
}
