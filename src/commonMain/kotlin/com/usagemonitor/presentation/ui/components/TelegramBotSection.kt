package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TelegramChat
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlinx.coroutines.delay

const val TELEGRAM_SECTION_TEST_TAG = "telegramSection"
const val TELEGRAM_SWITCH_TEST_TAG = "telegramSwitch"

/**
 * O que a seção do bot mostra (#387; #396, direção V2 — três passos).
 * [statusPreview] é a resposta real que o `/status` daria agora, sem as marcas HTML.
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
    val onSendTest: () -> Unit,
    /** O polling está de pé com este token: o passo do token está feito. */
    val connected: Boolean = false,
    /** `@` do bot sem a arroba, do `getMe`; `null` esconde "Abrir no Telegram". */
    val botUsername: String? = null,
    /** `/api` pode ligar e desligar fontes (#398, Y8). */
    val allowSourceControl: Boolean = false,
    val onAllowSourceControlChange: (Boolean) -> Unit = {}
)

/**
 * Seção dentro da aba Alertas — sem aba nova (`SettingsTab` não ganha valor):
 * o bot é mais um destino dos mesmos alertas, e os comandos dele mudam estas
 * mesmas preferências.
 *
 * Três passos numerados (#396, direção V2): token, pareamento e teste. O comando
 * de pareamento é uma caixa com **Copiar** e, quando o `getMe` deu o `@` do bot,
 * **Abrir no Telegram** — o link `t.me/<bot>?start=<código>` já manda o `/start`.
 * A conversa de exemplo saiu do card: "Ver como o bot responde" a abre, recolhida.
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
        Column(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            BodyText(
                if (pt) "Recebe os alertas no Telegram e responde a comandos. Só metadados de uso — nunca prompt ou resposta."
                else "Receives alerts on Telegram and answers commands. Usage metadata only — never prompts or responses."
            )
            TelegramStep(number = 1, title = if (pt) "Token do bot" else "Bot token", state = stepState(done = model.enabled && model.connected), pt = pt) {
                BodyText(if (pt) "Crie o bot com o @BotFather e cole o token aqui." else "Create the bot with @BotFather and paste its token here.")
                AppTextField(
                    value = model.token,
                    onValueChange = model.onTokenChange,
                    placeholder = if (pt) "Token do bot" else "Bot token",
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (model.enabled) AppStatusIndicator(label = model.statusLabel, tone = model.statusTone)
            }
            if (!model.enabled) {
                return@Column
            }
            val pairingDone = model.chats.isNotEmpty() && model.pairingCode == null
            TelegramStep(number = 2, title = if (pt) "Parear uma conversa" else "Pair a chat", state = stepState(done = pairingDone), pt = pt) {
                TelegramChats(model, pt)
                val code = model.pairingCode
                if (code != null) {
                    TelegramPairingCommand(code, model, pt)
                } else {
                    AppButton(
                        label = if (model.chats.isEmpty()) (if (pt) "Parear conversa" else "Pair chat") else (if (pt) "Parear outra conversa" else "Pair another chat"),
                        onClick = model.onStartPairing
                    )
                }
            }
            TelegramStep(number = 3, title = if (pt) "Testar" else "Test", state = stepState(done = false, waiting = model.chats.isEmpty()), pt = pt) {
                if (model.chats.isEmpty()) {
                    BodyText(if (pt) "Disponível depois do pareamento." else "Available after pairing.")
                }
                AppButton(label = if (pt) "Enviar teste" else "Send test", onClick = model.onSendTest, enabled = model.chats.isNotEmpty())
            }
            TelegramBotOptions(model, pt)
            TelegramPreview(model.statusPreview, pt)
            BodyText(
                if (pt) "Discord fica para uma segunda fase: exige conexão permanente (Gateway)."
                else "Discord is a second phase: it needs a persistent connection (Gateway)."
            )
        }
    }
}

private enum class StepState { DONE, CURRENT, WAITING }

private fun stepState(done: Boolean, waiting: Boolean = false): StepState = when {
    done -> StepState.DONE
    waiting -> StepState.WAITING
    else -> StepState.CURRENT
}

/** Um passo: número e título, o estado em ponto e palavra, e o conteúdo abaixo. */
@Composable
private fun TelegramStep(number: Int, title: String, state: StepState, pt: Boolean, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text("$number · $title", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            when (state) {
                StepState.DONE -> AppStatusIndicator(label = if (pt) "Feito" else "Done", tone = AppTone.OK)
                StepState.CURRENT -> AppStatusIndicator(label = if (pt) "Agora" else "Now", tone = AppTone.INFO)
                StepState.WAITING -> AppStatusIndicator(label = if (pt) "Depois" else "Later", tone = AppTone.NEUTRAL)
            }
        }
        content()
    }
}

/** Conversas pareadas; "Remover" colado ao nome, não na outra ponta da coluna. */
@Composable
private fun TelegramChats(model: TelegramBotSectionModel, pt: Boolean) {
    if (model.chats.isEmpty()) {
        BodyText(if (pt) "Nenhuma conversa pareada ainda." else "No paired chat yet.")
        return
    }
    model.chats.forEach { chat ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(chat.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            AppButton(label = if (pt) "Remover" else "Remove", onClick = { model.onRemoveChat(chat.id) }, tone = AppButtonTone.GHOST)
        }
    }
}

/** Caixa do comando com Copiar (vira "Copiado ✓" por 1,6 s) e o link que já manda o `/start`. */
@Composable
private fun TelegramPairingCommand(code: String, model: TelegramBotSectionModel, pt: Boolean) {
    val command = "/start $code"
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_FEEDBACK_MILLIS)
            copied = false
        }
    }
    Text(if (pt) "Envie ao bot no Telegram" else "Send this to the bot on Telegram", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    AppDataSurface(contentPadding = AppSpacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(command, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            AppButton(
                label = if (copied) (if (pt) "Copiado ✓" else "Copied ✓") else (if (pt) "Copiar" else "Copy"),
                onClick = {
                    clipboard.setText(AnnotatedString(command))
                    copied = true
                },
                tone = AppButtonTone.PRIMARY
            )
        }
    }
    model.pairingHint?.let { hint -> BodyText(hint) }
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        model.botUsername?.let { username ->
            AppButton(
                label = if (pt) "Abrir no Telegram" else "Open in Telegram",
                onClick = { runCatching { uriHandler.openUri("https://t.me/$username?start=$code") } }
            )
        }
        AppButton(label = if (pt) "Gerar novo código" else "New code", onClick = model.onStartPairing, tone = AppButtonTone.GHOST)
    }
}

/** A resposta real do `/status`, recolhida por padrão. */
@Composable
private fun TelegramPreview(preview: String, pt: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        AppButton(
            label = if (expanded) (if (pt) "▾ Ocultar exemplo" else "▾ Hide sample") else (if (pt) "▸ Ver como o bot responde" else "▸ See how the bot replies"),
            onClick = { expanded = !expanded },
            tone = AppButtonTone.GHOST
        )
        AppExpandable(expanded) {
            AppDataSurface(contentPadding = AppSpacing.sm) {
                Text("/status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(preview, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private const val COPIED_FEEDBACK_MILLIS = 1_600L
