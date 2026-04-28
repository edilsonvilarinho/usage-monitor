package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsBar(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    appVersion: String,
    secondsUntilRefresh: Int,
    autoStartEnabled: Boolean,
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        val compact = maxWidth < 760.dp

        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsPrimaryGroup(
                    currentTheme = currentTheme,
                    currentLanguage = currentLanguage,
                    autoStartEnabled = autoStartEnabled,
                    onThemeToggle = onThemeToggle,
                    onAutoStartChange = onAutoStartChange
                )
                SettingsSecondaryGroup(
                    currentLanguage = currentLanguage,
                    appVersion = appVersion,
                    secondsUntilRefresh = secondsUntilRefresh,
                    onRefresh = onRefresh,
                    onLanguageChange = onLanguageChange
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsPrimaryGroup(
                    currentTheme = currentTheme,
                    currentLanguage = currentLanguage,
                    autoStartEnabled = autoStartEnabled,
                    onThemeToggle = onThemeToggle,
                    onAutoStartChange = onAutoStartChange,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                SettingsSecondaryGroup(
                    currentLanguage = currentLanguage,
                    appVersion = appVersion,
                    secondsUntilRefresh = secondsUntilRefresh,
                    onRefresh = onRefresh,
                    onLanguageChange = onLanguageChange,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPrimaryGroup(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    autoStartEnabled: Boolean,
    onThemeToggle: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeToggle(
            isDark = currentTheme == AppTheme.DARK,
            language = currentLanguage,
            onToggle = onThemeToggle
        )

        AutoStartToggle(
            enabled = autoStartEnabled,
            language = currentLanguage,
            onToggle = onAutoStartChange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSecondaryGroup(
    currentLanguage: AppLanguage,
    appVersion: String,
    secondsUntilRefresh: Int,
    onRefresh: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CurrentVersionLabel(
            appVersion = appVersion,
            language = currentLanguage
        )

        RefreshControl(
            secondsUntilRefresh = secondsUntilRefresh,
            language = currentLanguage,
            onRefresh = onRefresh
        )

        LanguageSelector(
            currentLanguage = currentLanguage,
            onLanguageChange = onLanguageChange
        )
    }
}

@Composable
fun CurrentVersionLabel(
    appVersion: String,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val label = if (language == AppLanguage.PT) {
        "Versão: v$appVersion"
    } else {
        "Version: v$appVersion"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun ThemeToggle(
    isDark: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when {
        isDark && language == AppLanguage.PT -> "🌙 Escuro"
        isDark -> "🌙 Dark"
        language == AppLanguage.PT -> "☀️ Claro"
        else -> "☀️ Light"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.toggleable(
            value = isDark,
            role = Role.Switch,
            onValueChange = { onToggle() }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 8.dp)
        )
        Switch(
            checked = isDark,
            onCheckedChange = null
        )
    }
}

@Composable
fun AutoStartToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (language == AppLanguage.PT) "Inicialização com Sistema" else "System Startup"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle(it) }
        )
    }
}

@Composable
fun RefreshControl(
    secondsUntilRefresh: Int,
    language: AppLanguage,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val minutes = secondsUntilRefresh / 60
    val seconds = secondsUntilRefresh % 60
    val countdownText = if (language == AppLanguage.PT) {
        String.format("Próxima atualização: %02d:%02d", minutes, seconds)
    } else {
        String.format("Next update: %02d:%02d", minutes, seconds)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = countdownText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .widthIn(max = 128.dp)
                .padding(end = 4.dp)
        )
        IconButton(onClick = onRefresh) {
            Text(
                text = "↻",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

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
                    text = language.name,
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
