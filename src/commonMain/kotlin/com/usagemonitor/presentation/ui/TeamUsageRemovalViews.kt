package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppDialog
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.ModalDialogText

// Diálogos de remoção e a linha de erro de ação das Sessões do time, fora de
// `TeamUsageScreen.kt` pelo teto de 800 linhas (a tela estava em 749, #494).

internal data class PendingSessionRemoval(
    val member: TeamMemberUsage,
    val session: CliSessionSummary
)

/** Confirmação obrigatória: a remoção apaga dados e não tem desfazer. */
@Composable
internal fun RemoveMemberConfirmation(
    member: TeamMemberUsage,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(TeamUsageLabels.removeMemberTitle(language)) },
        text = { ModalDialogText(TeamUsageLabels.removeMemberWarning(member.alias, language)) },
        confirmButton = {
            AppButton(
                label = TeamUsageLabels.confirmRemoval(language),
                onClick = onConfirm,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag(TEAM_REMOVE_CONFIRM_TAG)
            )
        },
        dismissButton = {
            AppButton(
                label = TeamUsageLabels.cancel(language),
                onClick = onDismiss,
                tone = AppButtonTone.GHOST
            )
        }
    )
}

@Composable
internal fun RemoveSessionConfirmation(
    target: PendingSessionRemoval,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(TeamUsageLabels.removeSessionTitle(language)) },
        text = {
            ModalDialogText(
                TeamUsageLabels.removeSessionWarning(
                    sessionId = target.session.sessionId,
                    projectName = target.session.projectName,
                    language = language
                )
            )
        },
        confirmButton = {
            AppButton(
                label = TeamUsageLabels.confirmSessionRemoval(language),
                onClick = onConfirm,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag(TEAM_SESSION_REMOVE_CONFIRM_TAG)
            )
        },
        dismissButton = {
            AppButton(
                label = TeamUsageLabels.cancel(language),
                onClick = onDismiss,
                tone = AppButtonTone.GHOST
            )
        }
    )
}

@Composable
internal fun TeamActionErrorRow(text: String, language: AppLanguage, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        AppButton(
            label = TeamUsageLabels.cancel(language),
            onClick = onDismiss,
            tone = AppButtonTone.GHOST
        )
    }
}
