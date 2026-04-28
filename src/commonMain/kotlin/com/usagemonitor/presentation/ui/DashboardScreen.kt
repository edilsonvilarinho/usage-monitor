package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.ApiUsageCard
import com.usagemonitor.presentation.ui.components.SettingsBar
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    appVersion: String,
    settings: Settings? = null,
    enabledApis: MutableStateFlow<Set<ApiSource>>? = null,
    initialAutoStartEnabled: Boolean = false,
    onAutoStartChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val defaultEnabledApis = emptySet<ApiSource>()
    val uiState by viewModel.uiState.collectAsState()
    val secondsUntilRefresh by viewModel.secondsUntilRefresh.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val snackbarHostState = SnackbarHostState()

    val enabledApisValue = enabledApis?.value ?: defaultEnabledApis
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
    var autoStartEnabled by remember { mutableStateOf(initialAutoStartEnabled) }

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
                key == "RATE_LIMIT:CODEX" -> if (language == AppLanguage.PT) "Codex rate limited — tentando novamente..." else "Codex rate limited — retrying..."
                key.startsWith("ERROR:CODEX:") -> {
                    val apiMsg = key.removePrefix("ERROR:CODEX:").replace("_", ":")
                    if (language == AppLanguage.PT) "Codex: $apiMsg" else "Codex: $apiMsg"
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
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        SettingsBar(
                            currentTheme = if (isDark) AppTheme.DARK else AppTheme.LIGHT,
                            currentLanguage = language,
                            appVersion = appVersion,
                            secondsUntilRefresh = secondsUntilRefresh,
                            autoStartEnabled = autoStartEnabled,
                            onThemeToggle = {
                                isDark = !isDark
                                settings?.putBoolean("isDark", isDark)
                            },
                            onLanguageChange = { lang ->
                                language = lang
                                settings?.putString("language", lang.name)
                            },
                            onAutoStartChange = { enabled ->
                                autoStartEnabled = enabled
                                settings?.putBoolean("autoStart", enabled)
                                onAutoStartChange?.invoke(enabled)
                            },
                            onRefresh = { viewModel.refresh() }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { scaffoldPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(scaffoldPadding)
                ) {
                    ApiSelector(
                        enabledApis = localEnabledApis,
                        onToggle = { api, checked ->
                            localEnabledApis = if (checked) localEnabledApis + api else localEnabledApis - api
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuccessContent(
    apiStatsList: List<com.usagemonitor.domain.entity.ApiUsageStats>,
    partialErrors: List<String>,
    enabledApis: Set<ApiSource>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val items = apiStatsList.filter { stats -> stats.source in enabledApis }
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = 12.dp)
        ) {
            if (partialErrors.isNotEmpty()) {
                partialErrors.forEach { error ->
                    Row(
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

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val compact = maxWidth < 720.dp
                val cardMaxWidth = if (compact) maxWidth else (maxWidth - 16.dp) / 2

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = if (compact) 1 else 2,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items.forEach { stats ->
                        ApiUsageCard(
                            apiName = stats.apiName,
                            quotas = stats.quotas,
                            showUsageDetails = stats.source != ApiSource.ANTHROPIC,
                            language = language,
                            modifier = Modifier.widthIn(max = cardMaxWidth)
                        )
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApiSelector(
    enabledApis: Set<ApiSource>,
    onToggle: (ApiSource, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
