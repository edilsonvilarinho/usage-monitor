package com.usagemonitor.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.usagemonitor.domain.entity.AppLanguage

@Composable
fun RefreshWarningDialog(
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (language == AppLanguage.PT) "Atualizar agora?" else "Refresh now?"
    val body = if (language == AppLanguage.PT) {
        "Forçar uma atualização antes do horário agendado consome cota da API e pode acelerar o esgotamento do limite de requisições. Deseja continuar?"
    } else {
        "Forcing a refresh ahead of the scheduled time consumes API quota and may accelerate hitting the request limit. Continue?"
    }
    val confirmLabel = if (language == AppLanguage.PT) "Atualizar" else "Refresh"
    val dismissLabel = if (language == AppLanguage.PT) "Cancelar" else "Cancel"

    // Os botões do app, sendo o de confirmar o único com peso, porque é a ação
    // que o diálogo propõe. Escurecimento, foco preso e Esc vêm do `AppDialog`.
    AppDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
        },
        text = {
            ModalDialogText(text = body)
        },
        confirmButton = {
            AppButton(label = confirmLabel, onClick = onConfirm, tone = AppButtonTone.PRIMARY)
        },
        dismissButton = {
            AppButton(label = dismissLabel, onClick = onDismiss, tone = AppButtonTone.GHOST)
        }
    )
}
