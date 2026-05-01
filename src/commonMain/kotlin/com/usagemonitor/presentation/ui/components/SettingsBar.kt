package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

// Breakpoints responsivos do FooterBar — abaixo de cada limite, layout colapsa um nível.
private val NarrowBreakpoint = 360.dp
private val MediumBreakpoint = 500.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FooterBar(
    appVersion: String,
    language: AppLanguage,
    nextRefreshAt: Instant,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    nowProvider: () -> Instant = { Clock.System.now() },
    countdownUpdatesEnabled: Boolean = true
) {
    val initialRemaining = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
    var secondsUntilRefresh by remember(nextRefreshAt) { mutableStateOf(initialRemaining) }

    LaunchedEffect(nextRefreshAt, countdownUpdatesEnabled) {
        secondsUntilRefresh = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
        if (!countdownUpdatesEnabled) {
            return@LaunchedEffect
        }

        while (true) {
            val remaining = (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt()
            secondsUntilRefresh = remaining
            if (remaining <= 0) {
                break
            }
            delay(1_000L)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val compact = maxWidth < MediumBreakpoint
            val dense = maxWidth < NarrowBreakpoint

            if (dense) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FooterCompactStatusGroup(
                        appVersion = appVersion,
                        language = language,
                        secondsUntilRefresh = secondsUntilRefresh,
                        dense = true,
                        modifier = Modifier.weight(1f)
                    )
                    FooterActionGroup(
                        language = language,
                        onRefresh = onRefresh,
                        onOpenSettings = onOpenSettings,
                        iconOnly = true
                    )
                }
            } else if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FooterCompactStatusGroup(
                        appVersion = appVersion,
                        language = language,
                        secondsUntilRefresh = secondsUntilRefresh
                    )
                    FooterActionGroup(
                        language = language,
                        onRefresh = onRefresh,
                        onOpenSettings = onOpenSettings,
                        compact = true
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FooterStatusGroup(
                        appVersion = appVersion,
                        language = language,
                        secondsUntilRefresh = secondsUntilRefresh,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    FooterActionGroup(
                        language = language,
                        onRefresh = onRefresh,
                        onOpenSettings = onOpenSettings
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FooterCompactStatusGroup(
    appVersion: String,
    language: AppLanguage,
    secondsUntilRefresh: Int,
    dense: Boolean = false,
    modifier: Modifier = Modifier
) {
    val countdown = formatRefreshCountdown(secondsUntilRefresh)
    val refreshLabel = if (dense) {
        countdown
    } else if (language == AppLanguage.PT) {
        "Próx. $countdown"
    } else {
        "Next $countdown"
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FooterCompactBadge(text = "v$appVersion")
        FooterCompactBadge(text = refreshLabel)
    }
}

@Composable
private fun FooterCompactBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FooterStatusGroup(
    appVersion: String,
    language: AppLanguage,
    secondsUntilRefresh: Int,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FooterStatusItem(
            label = if (language == AppLanguage.PT) "Versão:" else "Version:",
            value = "v$appVersion"
        )
        FooterStatusItem(
            label = if (language == AppLanguage.PT) "Próxima atualização:" else "Next update:",
            value = formatRefreshCountdown(secondsUntilRefresh)
        )
    }
}

@Composable
private fun FooterStatusItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.widthIn(min = 110.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FooterActionGroup(
    language: AppLanguage,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    compact: Boolean = false,
    iconOnly: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterIconActionButton(
            label = if (language == AppLanguage.PT) "Atualizar agora" else "Refresh now",
            onClick = onRefresh
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        if (iconOnly) {
            FooterIconActionButton(
                label = if (language == AppLanguage.PT) "Abrir configurações" else "Open settings",
                onClick = onOpenSettings
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        } else {
            TextButton(
                onClick = onOpenSettings,
                contentPadding = if (compact) {
                    PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                } else {
                    PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                }
            ) {
                Text(
                    text = if (language == AppLanguage.PT) "Configurações" else "Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun FooterIconActionButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier
            .height(34.dp)
            .width(34.dp)
            .semantics {
                contentDescription = label
            }
    ) {
        content()
    }
}

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
    onClose: () -> Unit,
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
                            isChecked = api in enabledApis,
                            onCheckedChange = { checked -> onApiToggle(api, checked) }
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onClose) {
                    Text(if (currentLanguage == AppLanguage.PT) "Fechar" else "Close")
                }
            }
        }
    }
}

private fun formatRefreshCountdown(secondsUntilRefresh: Int): String {
    val minutes = secondsUntilRefresh / 60
    val seconds = secondsUntilRefresh % 60
    return String.format("%02d:%02d", minutes, seconds)
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
