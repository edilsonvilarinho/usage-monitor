package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppThemePreset

/**
 * Aba Aparência (issue #399, direção X1): o que a janela mostra. Saiu da antiga
 * aba Geral, onde as 26 paletas empurravam Sistema e Diagnóstico para fora da
 * primeira tela.
 */
@Composable
internal fun AppearanceSettingsTab(
    currentTheme: AppThemePreset,
    currentLanguage: AppLanguage,
    windowOpacityPercent: Int,
    windowOpacityEnabled: Boolean,
    uiScalePercent: Int,
    reducedMotion: Boolean,
    onThemeChange: (AppThemePreset) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onWindowOpacityChange: (Int) -> Unit,
    onUiScaleChange: (Int) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit
) {
    val isPt = currentLanguage == AppLanguage.PT

    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Tema e idioma" else "Theme and language") }
    ) {
        AppDataRow {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isPt) "Tema" else "Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isPt) {
                        "Escolha uma das treze paletas claras ou treze escuras."
                    } else {
                        "Choose one of thirteen light or thirteen dark palettes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ThemePresetPicker(
                    selected = currentTheme,
                    language = currentLanguage,
                    onSelect = onThemeChange,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
            }
        }
        SettingsOptionRow(label = if (isPt) "Idioma" else "Language", showDivider = false) {
            LanguageSelector(
                currentLanguage = currentLanguage,
                onLanguageChange = onLanguageChange
            )
        }
    }

    // Painel separado do tema: estes três mudam o desenho de todas as janelas, e
    // não a paleta.
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = if (isPt) "Janela" else "Window") }
    ) {
        WindowOpacitySlider(
            percent = windowOpacityPercent,
            language = currentLanguage,
            enabled = windowOpacityEnabled,
            onPercentChange = onWindowOpacityChange
        )
        UiScaleSlider(
            percent = uiScalePercent,
            language = currentLanguage,
            onPercentChange = onUiScaleChange
        )
        ReducedMotionToggle(
            enabled = reducedMotion,
            language = currentLanguage,
            onToggle = onReducedMotionChange,
            showDivider = false
        )
    }
}
