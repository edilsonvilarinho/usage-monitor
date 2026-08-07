package com.usagemonitor.presentation.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}
