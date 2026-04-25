package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.SettingsBar
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    settings: Settings? = null,
    enabledApis: MutableStateFlow<Set<ApiSource>>? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val snackbarHostState = SnackbarHostState()

    val enabledApisValue = enabledApis?.value ?: setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX)
    val flow: MutableStateFlow<Set<ApiSource>> = enabledApis ?: MutableStateFlow(enabledApisValue)
    val enabledApisState by flow.collectAsState()

    var isDark by remember { mutableStateOf(settings?.getBoolean("isDark", true) ?: true) }
    var language by remember {
        mutableStateOf(
            settings?.getStringOrNull("language")
                ?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.PT
        )
    }
    var localEnabledApis by remember { mutableStateOf(enabledApisValue) }

    LaunchedEffect(enabledApisState) {
        localEnabledApis = enabledApisState
    }

    LaunchedEffect(localEnabledApis) {
        if (enabledApis != null) {
            enabledApis.emit(localEnabledApis)
        }
        settings?.putString("enabledApis", localEnabledApis.joinToString(",") { it.name })
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { key ->
            val msg = when {
                key == "RATE_LIMIT:ANTHROPIC" -> if (language == AppLanguage.PT) "Anthropic rate limited — tentando novamente..." else "Anthropic rate limited — retrying..."
                key.startsWith("ERROR:ANTHROPIC:") -> {
                    val apiMsg = key.removePrefix("ERROR:ANTHROPIC:").replace("_", ":")
                    if (language == AppLanguage.PT) "Anthropic: $apiMsg" else "Anthropic: $apiMsg"
                }
                key == "RATE_LIMIT:MINIMAX" -> if (language == AppLanguage.PT) "MiniMax rate limited — tentando novamente..." else "MiniMax rate limited — retrying..."
                key.startsWith("ERROR:MINIMAX:") -> {
                    val apiMsg = key.removePrefix("ERROR:MINIMAX:").replace("_", ":")
                    if (language == AppLanguage.PT) "MiniMax: $apiMsg" else "MiniMax: $apiMsg"
                }
                else -> key
            }
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.clearToast()
        }
    }

    AppTheme(isDark = isDark) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            SettingsBar(
                                currentTheme = if (isDark) AppTheme.DARK else AppTheme.LIGHT,
                                currentLanguage = language,
                                secondsUntilRefresh = secondsUntilRefresh,
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
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { scaffoldPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        ApiSelector(
                            enabledApis = localEnabledApis,
                            onToggle = { api, checked ->
                                localEnabledApis = if (checked) localEnabledApis + api else localEnabledApis - api
                            }
                        )
                    }

                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

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
                            enabledApis = localEnabledApis,
                            language = language,
                            modifier = Modifier.weight(1f)
                        )
                    }
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
            columns = GridCells.Adaptive(minSize = 260.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = modifier.weight(1f).fillMaxSize()
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
    val dayFormatted = when (resetLocal.dayOfWeek) {
        DayOfWeek.MONDAY -> if (language == AppLanguage.PT) "Seg" else "Mon"
        DayOfWeek.TUESDAY -> if (language == AppLanguage.PT) "Ter" else "Tue"
        DayOfWeek.WEDNESDAY -> if (language == AppLanguage.PT) "Qua" else "Wed"
        DayOfWeek.THURSDAY -> if (language == AppLanguage.PT) "Qui" else "Thu"
        DayOfWeek.FRIDAY -> if (language == AppLanguage.PT) "Sex" else "Fri"
        DayOfWeek.SATURDAY -> if (language == AppLanguage.PT) "Sáb" else "Sat"
        DayOfWeek.SUNDAY -> if (language == AppLanguage.PT) "Dom" else "Sun"
    }
    val resetLabel = if (language == AppLanguage.PT) {
        "Reinício: $dayFormatted ${resetLocal.hour}h${resetLocal.minute.toString().padStart(2, '0')} BRT"
    } else {
        "Reset: $dayFormatted ${resetLocal.hour}:${resetLocal.minute.toString().padStart(2, '0')} BRT"
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = apiName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            UsageArcChart(
                used = quota.used,
                total = quota.total,
                unit = quota.unit
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (apiName != "Anthropic") {
                Text(
                    text = formatUsage(quota.used, quota.total, quota.unit, quota.rawUsed, quota.rawTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

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

private fun formatUsage(used: Long, total: Long, unit: com.usagemonitor.domain.entity.UsageUnit, rawUsed: Long = 0L, rawTotal: Long = 0L): String {
    return when (unit) {
        com.usagemonitor.domain.entity.UsageUnit.PERCENTAGE -> "${used}%"
        com.usagemonitor.domain.entity.UsageUnit.TOKENS -> {
            val displayUsed = if (rawUsed > 0L) rawUsed else used
            val displayTotal = if (rawTotal > 0L) rawTotal else total
            "${abbreviate(displayUsed)}/${abbreviate(displayTotal)} tok"
        }
        com.usagemonitor.domain.entity.UsageUnit.REQUESTS -> "${abbreviate(used)}/${abbreviate(total)} req"
    }
}

private fun abbreviate(n: Long): String {
    return when {
        n >= 1_000_000L -> "${removeDecimal("%.1f".format(n / 1_000_000f))}M"
        n >= 1_000L     -> "${removeDecimal("%.1f".format(n / 1_000f))}K"
        else            -> n.toString()
    }
}

private fun removeDecimal(s: String): String = s.replace(",0", "").replace(".0", "")

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
