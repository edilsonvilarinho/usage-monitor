package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val TELEGRAM_SECTION_TEST_TAG = "telegramSection"
const val TELEGRAM_SWITCH_TEST_TAG = "telegramSwitch"

/**
 * O que a seção do bot mostra (#387, direção Q10 — configuração e conversa).
 * [statusPreview] é a resposta real que o `/status` daria agora.
 */
class TelegramBotSectionModel(
    val enabled: Boolean,
    val token: String,
    val statusLabel: String,
    val statusTone: AppTone,
    val chats: List<TelegramChat>,
    /** Código em aberto e quanto falta para expirar, já em texto. */
    val pairingCode: String?,
    val pairingHint: String?,
    val statusPreview: String,
    val onEnabledChange: (Boolean) -> Unit,
    val onTokenChange: (String) -> Unit,
    val onStartPairing: () -> Unit,
    val onRemoveChat: (Long) -> Unit,
    val onSendTest: () -> Unit
)

/**
 * Seção dentro da aba Alertas — sem aba nova (`SettingsTab` não ganha valor):
 * o bot é mais um destino dos mesmos alertas, e os comandos dele mudam estas
 * mesmas preferências. Duas colunas: a configuração e uma conversa de exemplo
 * com o texto real do `/status`, que mostra a mão dupla antes de parear.
 * Abaixo de 560dp as colunas empilham.
 */
@Composable
fun TelegramBotSection(model: TelegramBotSectionModel, language: AppLanguage, modifier: Modifier = Modifier) {
    val pt = language == AppLanguage.PT
    AppDataSurfaceFlush(
        modifier = modifier.fillMaxWidth().testTag(TELEGRAM_SECTION_TEST_TAG),
        header = {
            AppSectionHeader(
                title = if (pt) "Bot do Telegram" else "Telegram bot",
                trailing = {
                    AppSwitch(checked = model.enabled, onCheckedChange = model.onEnabledChange, modifier = Modifier.testTag(TELEGRAM_SWITCH_TEST_TAG))
                }
            )
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(AppSpacing.md)) {
            val wide = maxWidth >= 560.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    TelegramConfigColumn(model, pt, Modifier.weight(1f))
                    TelegramConversationColumn(model, pt, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    TelegramConfigColumn(model, pt, Modifier.fillMaxWidth())
                    TelegramConversationColumn(model, pt, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TelegramConfigColumn(model: TelegramBotSectionModel, pt: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            text = if (pt) {
                "Recebe os alertas no Telegram e responde a comandos. Só metadados de uso — nunca prompt ou resposta. " +
                    "Crie o bot com o @BotFather e cole o token aqui."
            } else {
                "Receives alerts on Telegram and answers commands. Usage metadata only — never prompts or responses. " +
                    "Create the bot with @BotFather and paste its token here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = model.token,
            onValueChange = model.onTokenChange,
            placeholder = if (pt) "Token do bot" else "Bot token",
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (!model.enabled) {
            return@Column
        }
        AppStatusIndicator(label = model.statusLabel, tone = model.statusTone)
        Text(if (pt) "Conversas autorizadas" else "Authorized chats", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        if (model.chats.isEmpty()) {
            Text(
                text = if (pt) "Nenhuma. Pareie uma conversa para receber os alertas." else "None. Pair a chat to receive alerts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        model.chats.forEach { chat ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Text(chat.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                AppButton(label = if (pt) "Remover" else "Remove", onClick = { model.onRemoveChat(chat.id) }, tone = AppButtonTone.GHOST)
            }
        }
        val code = model.pairingCode
        if (code != null) {
            Text(
                text = (if (pt) "No Telegram, envie ao bot: " else "In Telegram, send the bot: ") + "/start $code",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            model.pairingHint?.let { hint ->
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            AppButton(label = if (pt) "Parear conversa" else "Pair chat", onClick = model.onStartPairing)
            AppButton(label = if (pt) "Enviar teste" else "Send test", onClick = model.onSendTest, tone = AppButtonTone.GHOST, enabled = model.chats.isNotEmpty())
        }
        Text(
            text = if (pt) "Discord fica para uma segunda fase: exige conexão permanente (Gateway)." else "Discord is a second phase: it needs a persistent connection (Gateway).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TelegramConversationColumn(model: TelegramBotSectionModel, pt: Boolean, modifier: Modifier) {
    val you = if (pt) "você" else "you"
    val exchanges = listOf(
        "/status" to model.statusPreview,
        (if (pt) "/silencio 12-13" else "/quiet 12-13") to (if (pt) "Silêncio 12:00 → 13:00 aplicado." else "Quiet hours 12:00 → 13:00 set."),
        "/limiar 75,90" to (if (pt) "Limiares de cota: 75%, 90%" else "Quota thresholds: 75%, 90%")
    )
    AppDataSurfaceFlush(
        modifier = modifier,
        header = { AppSectionHeader(title = if (pt) "Conversa de exemplo" else "Sample conversation", subtitle = if (pt) "/status com os números de agora" else "/status with current numbers") }
    ) {
        Column {
            exchanges.forEachIndexed { index, (command, reply) ->
                AppDataRow(showDivider = index != exchanges.lastIndex) {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text("$you › $command", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("bot › $reply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
