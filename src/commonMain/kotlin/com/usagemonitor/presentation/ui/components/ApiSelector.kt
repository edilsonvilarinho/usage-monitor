package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.statusBadgeLabel
import com.usagemonitor.domain.entity.statusSupportingText

/**
 * Checkboxes para ativar/desativar o monitoramento de cada API.
 *
 * STATELESS: não armazena estado. Recebe `enabledApis` do ViewModel
 * e emite eventos via `onToggle`. A lógica de state management fica
 * no ViewModel — este componente apenas renderiza.
 *
 * Em Vue.js seria: v-model emitido para o pai via emit('update:modelValue').
 *
 * @param enabledApis  Conjunto de APIs atualmente ativas
 * @param onToggle     Callback ao clicar: recebe a API e o novo estado
 */
@Composable
fun ApiSelector(
    enabledApis: Set<ApiSource>,
    language: AppLanguage,
    onToggle: (ApiSource, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ApiSource.entries.forEach { api ->
            ApiCheckboxRow(
                api = api,
                language = language,
                isChecked = api in enabledApis,
                onCheckedChange = { checked -> onToggle(api, checked) }
            )
        }
    }
}

/**
 * Linha individual de checkbox + label.
 * Extraída como componente separado para ser testável de forma isolada.
 */
@Composable
fun ApiCheckboxRow(
    api: ApiSource,
    language: AppLanguage = AppLanguage.PT,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeLabel = api.statusBadgeLabel(language)
    val supportingText = api.statusSupportingText(language)

    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .toggleable(
                value = isChecked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 2.dp)
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = null
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = apiLabel(api, language),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (badgeLabel != null) {
                    SourceStatusBadge(label = badgeLabel)
                }
            }
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SourceStatusBadge(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun apiLabel(api: ApiSource, language: AppLanguage): String {
    return api.displayName(language)
}
