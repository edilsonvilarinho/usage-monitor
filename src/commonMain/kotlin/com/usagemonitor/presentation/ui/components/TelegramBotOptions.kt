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
import com.usagemonitor.domain.entity.TELEGRAM_SUMMARY_HOURS
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** O rótulo é traduzido; buscar por texto amarraria o teste ao idioma. */
const val TELEGRAM_SOURCE_CONTROL_SWITCH_TEST_TAG = "telegramSourceControlSwitch"
const val TELEGRAM_DAILY_SUMMARY_TEST_TAG = "telegramDailySummary"
const val TELEGRAM_LIVE_PANEL_SWITCH_TEST_TAG = "telegramLivePanelSwitch"
const val TELEGRAM_PC_ACTIONS_SWITCH_TEST_TAG = "telegramPcActionsSwitch"
const val TELEGRAM_MACHINE_NAME_SWITCH_TEST_TAG = "telegramMachineNameSwitch"
const val TELEGRAM_TEAM_SWITCH_TEST_TAG = "telegramTeamSwitch"

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
            label = if (pt) "Resumo diário" else "Daily summary",
            description = if (pt) {
                "Uma mensagem por dia com a cota mais cheia de cada conta, o que reinicia hoje e o gasto das últimas 24 h. Horário em BRT; o silêncio adia."
            } else {
                "One message a day with each account's fullest quota, what resets today and the last 24 h spend. Time in BRT; quiet hours delay it."
            }
        ) {}
        val hours = listOf<Int?>(null) + TELEGRAM_SUMMARY_HOURS
        AppSegmentedControl(
            options = hours.map { hour -> AppSegment(label = hour?.let { value -> "${value.toString().padStart(2, '0')}:00" } ?: if (pt) "Desligado" else "Off") },
            selectedIndex = hours.indexOf(model.dailySummaryHour).coerceAtLeast(0),
            onSelect = { index -> model.onDailySummaryHourChange(hours[index]) },
            modifier = Modifier.testTag(TELEGRAM_DAILY_SUMMARY_TEST_TAG)
        )
        TelegramOptionRow(
            label = if (pt) "Painel fixado ao vivo" else "Live pinned panel",
            description = if (pt) {
                "Fixa uma mensagem no topo da conversa e a edita a cada coleta, sem notificar. Desligar desafixa."
            } else {
                "Pins a message at the top of the chat and edits it on every collection, silently. Turning it off unpins it."
            }
        ) {
            AppSwitch(
                checked = model.livePanelEnabled,
                onCheckedChange = model.onLivePanelChange,
                modifier = Modifier.testTag(TELEGRAM_LIVE_PANEL_SWITCH_TEST_TAG)
            )
        }
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
        TelegramOptionRow(
            label = if (pt) "Permitir ações no PC" else "Allow PC actions",
            description = if (pt) {
                "Com ele, o /hud traz a HUD para a frente neste computador. Desligado, o bot só lê."
            } else {
                "With it, /hud brings the HUD to the front on this computer. Off, the bot only reads."
            }
        ) {
            AppSwitch(
                checked = model.preferences.allowPcActions,
                onCheckedChange = { allowed -> model.onPreferencesChange(model.preferences.copy(allowPcActions = allowed)) },
                modifier = Modifier.testTag(TELEGRAM_PC_ACTIONS_SWITCH_TEST_TAG)
            )
        }
        val machine = model.machineName
        TelegramOptionRow(
            label = if (pt) "Nome deste PC nas mensagens" else "This PC's name in messages",
            description = when {
                machine == null && pt -> "Útil com o bot em mais de um computador. O sistema não informou o nome deste."
                machine == null -> "Useful with the bot on more than one computer. The system did not report this one's name."
                pt -> "Útil com o bot em mais de um computador. Este assina como $machine."
                else -> "Useful with the bot on more than one computer. This one signs as $machine."
            }
        ) {
            AppSwitch(
                checked = model.preferences.showMachineName,
                onCheckedChange = { show -> model.onPreferencesChange(model.preferences.copy(showMachineName = show)) },
                modifier = Modifier.testTag(TELEGRAM_MACHINE_NAME_SWITCH_TEST_TAG)
            )
        }
        TelegramOptionRow(
            label = if (pt) "Mostrar o time no bot" else "Show the team in the bot",
            description = if (pt) {
                "Com ele, o /time lista quem do time está online pelo apelido. Expõe dado de outras pessoas na conversa."
            } else {
                "With it, /team lists who from the team is online by nickname. It shows other people's data in the chat."
            }
        ) {
            AppSwitch(
                checked = model.preferences.showTeam,
                onCheckedChange = { show -> model.onPreferencesChange(model.preferences.copy(showTeam = show)) },
                modifier = Modifier.testTag(TELEGRAM_TEAM_SWITCH_TEST_TAG)
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
