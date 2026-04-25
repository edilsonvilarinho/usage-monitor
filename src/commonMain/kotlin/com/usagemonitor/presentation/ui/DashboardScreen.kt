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
import androidx.compose.material3.Divider
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
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppTheme
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.presentation.ui.components.ApiCheckboxRow
import com.usagemonitor.presentation.ui.components.SettingsBar
import com.usagemonitor.presentation.ui.components.UsageArcChart
import com.usagemonitor.presentation.viewmodel.DashboardViewModel
import com.usagemonitor.presentation.viewmodel.UiState
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Ecrã principal do Dashboard.
 *
 * Este é o único componente STATEFUL — observa o ViewModel e distribui
 * dados para os componentes filhos (todos stateless).
 *
 * Em Vue.js seria o componente de página que usa `useStore()` ou `pinia`.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    // `collectAsState()` conecta o StateFlow ao sistema reativo do Compose.
    // Cada vez que uiState muda, este componente re-renderiza automaticamente.
    val uiState by viewModel.uiState.collectAsState()

    // Estado local de preferências (tema e idioma)
    var isDark by remember { mutableStateOf(true) }
    var language by remember { mutableStateOf(AppLanguage.PT) }
    var enabledApis by remember { mutableStateOf(setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX)) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra de configurações no topo
            SettingsBar(
                currentTheme = if (isDark) AppTheme.DARK else AppTheme.LIGHT,
                currentLanguage = language,
                onThemeToggle = { isDark = !isDark },
                onLanguageChange = { language = it }
            )

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Seletor de APIs logo abaixo
            ApiSelector(enabledApis = enabledApis, onToggle = { api, checked ->
                enabledApis = if (checked) enabledApis + api else enabledApis - api
            })

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            Spacer(modifier = Modifier.height(8.dp))

            // Conteúdo central: muda conforme o estado da UI
            when (val state = uiState) {
                is UiState.Loading -> LoadingContent()
                is UiState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { viewModel.refresh() }
                )
                is UiState.Success -> SuccessContent(
                    apiStatsList = state.data,
                    enabledApis = enabledApis,
                    language = language,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Indicador de carregamento centralizado. */
@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Carregando dados das APIs...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Mensagem de erro com botão de retry. */
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Erro ao carregar dados",
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
                Text("Tentar novamente")
            }
        }
    }
}

/**
 * Grid responsivo de cards de uso.
 * Cada modelo MiniMax e a cota Anthropic aparecem como cards separados.
 */
@Composable
private fun SuccessContent(
    apiStatsList: List<com.usagemonitor.domain.entity.ApiUsageStats>,
    enabledApis: Set<ApiSource>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    // Achata todas as cotas em uma lista única de pares (apiName, QuotaInfo)
    val items = apiStatsList
        .filter { stats ->
            // Filtra apenas as APIs que o utilizador ativou
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
        modifier = modifier.fillMaxSize()
    ) {
        items(items) { (apiName, quota) ->
            QuotaCard(apiName = apiName, quota = quota, language = language)
        }
    }
}

/** Card individual para uma cota — stateless. */
@Composable
private fun QuotaCard(
    apiName: String,
    quota: QuotaInfo,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    // Timezone de São Paulo para exibição do reset
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
            // Nome da API
            Text(
                text = apiName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Gráfico em arco
            UsageArcChart(
                used = quota.used,
                total = quota.total,
                label = quota.label,
                unit = quota.unit
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Nome do modelo/recurso
            Text(
                text = quota.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )

            // Timestamp de reset
            Text(
                text = resetLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Componente auxiliar para importar ApiSelector inline no DashboardScreen
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
