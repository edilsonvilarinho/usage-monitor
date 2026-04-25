package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.SettingsBar
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    settings: Settings? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val minutesUntilRefresh by viewModel.minutesUntilRefresh.collectAsState()

    // Preferências carregadas do storage e persistidas em cada mudança
    var isDark by remember { mutableStateOf(settings?.getBoolean("isDark", true) ?: true) }
    var language by remember {
        mutableStateOf(
            settings?.getStringOrNull("language")
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.PT
        )
    }
    var enabledApis by remember {
        mutableStateOf(
            settings?.getStringOrNull("enabledApis")
                ?.split(",")
                ?.mapNotNull { runCatching { ApiSource.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?.ifEmpty { setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX) }
                ?: setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX)
        )
    }

    AppTheme(isDark = isDark) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettingsBar(
                    currentTheme = if (isDark) AppTheme.DARK else AppTheme.LIGHT,
                    currentLanguage = language,
                    minutesUntilRefresh = minutesUntilRefresh,
                    onThemeToggle = {
                        isDark = !isDark
                        settings?.putBoolean("isDark", isDark)
                    },
                    onLanguageChange = { lang ->
                        language = lang
                        settings?.putString("language", lang.name)
                    },
                    onRefresh = { viewModel.refresh() }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                ApiSelector(
                    enabledApis = enabledApis,
                    onToggle = { api, checked ->
                        enabledApis = if (checked) enabledApis + api else enabledApis - api
                        settings?.putString("enabledApis", enabledApis.joinToString(",") { it.name })
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Spacer(modifier = Modifier.height(8.dp))

                when (val state = uiState) {
                    is UiState.Loading -> LoadingContent(language = language)
                    is UiState.Error -> ErrorContent(
                        message = state.message,
                        language = language,
                        onRetry = { viewModel.refresh() }
                    )
                    is UiState.Success -> SuccessContent(
                        apiStatsList = state.data,
                        partialErrors = state.errors,
                        enabledApis = enabledApis,
                        language = language,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(language: AppLanguage) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (language == AppLanguage.PT) "Carregando dados das APIs..." else "Loading API data...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, language: AppLanguage, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = if (language == AppLanguage.PT) "Erro ao carregar dados" else "Failed to load data",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(if (language == AppLanguage.PT) "Tentar novamente" else "Retry")
            }
        }
    }
}

@Composable
private fun SuccessContent(
    apiStatsList: List<com.usagemonitor.domain.entity.ApiUsageStats>,
    partialErrors: List<String>,
    enabledApis: Set<ApiSource>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (partialErrors.isNotEmpty()) {
            partialErrors.forEach { error ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚠ $error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        val items = apiStatsList
            .filter { stats ->
                val apiSource = if (stats.apiName == "Anthropic") ApiSource.ANTHROPIC else ApiSource.MINIMAX
                apiSource in enabledApis
            }
            .flatMap { stats ->
                stats.quotas.map { quota -> Pair(stats.apiName, quota) }
            }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxSize()
        ) {
            items(items) { (apiName, quota) ->
                QuotaCard(apiName = apiName, quota = quota, language = language)
            }
        }
    }
}

@Composable
private fun QuotaCard(
    apiName: String,
    quota: QuotaInfo,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val saoPauloTz = TimeZone.of("America/Sao_Paulo")
    val resetLocal = quota.periodEndAt.toLocalDateTime(saoPauloTz)
    val resetLabel = if (language == AppLanguage.PT) {
        "Reset: ${resetLocal.hour}h${resetLocal.minute.toString().padStart(2, '0')} BRT"
    } else {
        "Reset: ${resetLocal.hour}:${resetLocal.minute.toString().padStart(2, '0')} BRT"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = apiName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            UsageArcChart(
                used = quota.used,
                total = quota.total,
                label = quota.label,
                unit = quota.unit
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = quota.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            if (quota.periodType == PeriodType.WEEKLY) {
                Text(
                    text = if (language == AppLanguage.PT) "Semanal" else "Weekly",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = resetLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApiSelector(
    enabledApis: Set<ApiSource>,
    onToggle: (ApiSource, Boolean) -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        ApiSource.entries.forEach { api ->
            ApiCheckboxRow(
                api = api,
                isChecked = api in enabledApis,
                onCheckedChange = { checked -> onToggle(api, checked) }
            )
        }
    }
}
