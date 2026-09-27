package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.AppThemePreset

/**
 * Grade responsiva com as paletas disponíveis (`AppThemePreset.entries`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePresetPicker(
    selected: AppThemePreset,
    language: AppLanguage = AppLanguage.PT,
    onSelect: (AppThemePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        ThemePresetGroup(
            title = if (isPt) "Escuros" else "Dark",
            presets = AppThemePreset.dark,
            selected = selected,
            language = language,
            onSelect = onSelect
        )
        ThemePresetGroup(
            title = if (isPt) "Claros" else "Light",
            presets = AppThemePreset.light,
            selected = selected,
            language = language,
            onSelect = onSelect
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePresetGroup(
    title: String,
    presets: List<AppThemePreset>,
    selected: AppThemePreset,
    language: AppLanguage,
    onSelect: (AppThemePreset) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            presets.forEach { preset ->
                ThemePresetCard(
                    preset = preset,
                    selected = preset == selected,
                    language = language,
                    onClick = { onSelect(preset) }
                )
            }
        }
    }
}

@Composable
private fun ThemePresetCard(
    preset: AppThemePreset,
    selected: Boolean,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val label = if (language == AppLanguage.PT) preset.labelPt else preset.labelEn
    Surface(
        modifier = Modifier
            .width(126.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .testTag(THEME_PRESET_TEST_TAG_PREFIX + preset.name),
        shape = AppShapes.small,
        color = preset.surface,
        contentColor = preset.foreground,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) preset.primary else preset.border
        )
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).height(12.dp).background(preset.background)
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).height(12.dp).background(preset.raised)
                )
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.weight(1f).height(12.dp).background(preset.primary)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = preset.foreground,
                maxLines = 1
            )
        }
    }
}

/**
 * Seletor de tema legado: segmentado de duas opções, mantido para consumidores
 * de componente que ainda precisam escolher apenas o modo claro/escuro.
 *
 * Era um rótulo com emoji ao lado de um interruptor, e a forma mentia sobre a
 * natureza da escolha: interruptor diz ligado/desligado, e tema é uma escolha
 * entre duas alternativas — a mesma pergunta que o seletor de idioma logo
 * abaixo já respondia com um segmentado. Era também o único emoji da interface.
 */
@Composable
fun ThemeToggle(
    isDark: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT
    val options = listOf(
        AppSegment(label = if (isPt) "Escuro" else "Dark"),
        AppSegment(label = if (isPt) "Claro" else "Light")
    )
    AppSegmentedControl(
        options = options,
        selectedIndex = if (isDark) 0 else 1,
        // O callback do app alterna, não escolhe: clicar na opção já ativa não
        // pode inverter o tema.
        onSelect = { index -> if ((index == 0) != isDark) onToggle() },
        modifier = modifier
    )
}

@Composable
fun LanguageSelector(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    // Segmentado: idioma é uma escolha entre alternativas mutuamente exclusivas,
    // que é exatamente o que este controle diz. Dois botões de texto lado a lado
    // deixavam a diferença entre escolhido e não escolhido só na cor.
    AppSegmentedControl(
        options = AppLanguage.entries.map { language -> AppSegment(label = language.name) },
        selectedIndex = AppLanguage.entries.indexOf(currentLanguage),
        onSelect = { index -> onLanguageChange(AppLanguage.entries[index]) },
        modifier = modifier
    )
}
