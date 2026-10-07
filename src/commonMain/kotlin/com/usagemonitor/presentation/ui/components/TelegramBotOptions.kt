package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** O rótulo é traduzido; buscar por texto amarraria o teste ao idioma. */
const val TELEGRAM_SOURCE_CONTROL_SWITCH_TEST_TAG = "telegramSourceControlSwitch"

/**
 * Opções do que o bot faz além de responder (#398): permissões e envios por conta
 * própria. Bloco abaixo dos três passos — só faz sentido com uma conversa pareada,
 * e mantém os passos de configuração (#396, V2) como estão.
 */
@Composable
internal fun TelegramBotOptions(model: TelegramBotSectionModel, pt: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            text = if (pt) "Opções do bot" else "Bot options",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        TelegramOptionRow(
            label = if (pt) "Permitir mudar fontes pelo bot" else "Allow changing sources from the bot",
            description = if (pt) {
                "Com ele, o /api liga e desliga as APIs monitoradas. Desligado, o /api só lista."
            } else {
                "With it, /api turns monitored APIs on and off. Off, /api only lists them."
            }
        ) {
            AppSwitch(
                checked = model.allowSourceControl,
                onCheckedChange = model.onAllowSourceControlChange,
                modifier = Modifier.testTag(TELEGRAM_SOURCE_CONTROL_SWITCH_TEST_TAG)
            )
        }
    }
}

/** Rótulo em mono, descrição em sans e o controle à direita — o padrão de `SettingsOptionRow`, sem a linha de dados. */
@Composable
internal fun TelegramOptionRow(label: String, description: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}
