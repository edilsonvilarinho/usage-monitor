package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialogContent(
    currentTheme: AppTheme,
    currentLanguage: AppLanguage,
    enabledApis: Set<ApiSource>,
    autoStartEnabled: Boolean,
    onThemeToggle: () -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onApiToggle: (ApiSource, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (currentLanguage == AppLanguage.PT) "Configurações" else "Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (currentLanguage == AppLanguage.PT) "Idioma" else "Language",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LanguageSelector(
                    currentLanguage = currentLanguage,
                    onLanguageChange = onLanguageChange
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (currentLanguage == AppLanguage.PT) "APIs monitoradas" else "Monitored APIs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ApiSource.entries.forEach { api ->
                        ApiCheckboxRow(
                            api = api,
                            language = currentLanguage,
                            isChecked = api in enabledApis,
                            onCheckedChange = { checked -> onApiToggle(api, checked) }
                        )
                    }
                }
            }

            HorizontalDivider()
        }
    }
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
    val countdownText = if (language == AppLanguage.PT) {
        "Próxima atualização: ${formatRefreshCountdown(secondsUntilRefresh)}"
    } else {
        "Next update: ${formatRefreshCountdown(secondsUntilRefresh)}"
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Text(
            text = countdownText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .widthIn(max = 140.dp)
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
