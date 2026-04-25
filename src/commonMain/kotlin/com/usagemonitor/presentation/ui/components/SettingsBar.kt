package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme

/**
 * Barra de configurações: toggle de tema e seleção de idioma.
 *
 * STATELESS: todos os estados vêm de fora (props) e as ações
 * são emitidas via callbacks (equivalente ao emit() do Vue.js).
 *
 * @param currentTheme    Tema atual (DARK ou LIGHT)
 * @param currentLanguage Idioma atual (PT ou EN)
 * @param onThemeToggle   Callback ao alternar tema
 * @param onLanguageChange Callback ao trocar idioma
 */
@Composable
fun SettingsBar(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle escuro/claro
        ThemeToggle(
            isDark = currentTheme == AppTheme.DARK,
            onToggle = onThemeToggle
        )

        // Botões de idioma
        LanguageSelector(
            currentLanguage = currentLanguage,
            onLanguageChange = onLanguageChange
        )
    }
}

/** Componente isolado para o switch de tema. */
@Composable
fun ThemeToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = if (isDark) "🌙 Escuro" else "☀️ Claro",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(
            checked = isDark,
            onCheckedChange = { onToggle() }
        )
    }
}

/** Componente isolado para os botões de idioma PT/EN. */
@Composable
fun LanguageSelector(
    currentLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == currentLanguage
            TextButton(
                onClick = { onLanguageChange(language) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            ) {
                Text(
                    text = language.name,  // "PT" ou "EN"
                    style = if (isSelected) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelMedium
                    }
                )
            }
        }
    }
}
