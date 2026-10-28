package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamStageStatus
import com.usagemonitor.domain.entity.stageStatuses
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppStatusDot
import com.usagemonitor.presentation.ui.components.AppStep
import com.usagemonitor.presentation.ui.components.AppStepList
import com.usagemonitor.presentation.ui.components.AppStepStatus
import com.usagemonitor.presentation.ui.components.AppStepTrail
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** As etapas da falha como passos da trilha, com o texto de cada uma. */
internal fun teamConnectionSteps(
    failure: TeamConnectionFailure,
    kind: TeamCredentialKind,
    language: AppLanguage
): List<AppStep> {
    return failure.stageStatuses().map { (stage, status) ->
        AppStep(
            label = TeamConnectionLabels.stage(stage, kind, language),
            status = when (status) {
                TeamStageStatus.PASSED -> AppStepStatus.DONE
                TeamStageStatus.FAILED -> AppStepStatus.FAILED
                TeamStageStatus.NOT_REACHED -> AppStepStatus.NOT_REACHED
                TeamStageStatus.UNKNOWN -> AppStepStatus.UNKNOWN
            },
            detail = TeamConnectionLabels.stageDetail(failure, status, kind, language)
        )
    }
}

/**
 * Erro de tela cheia das janelas de time (AJ4, issue #494): onde parou, a trilha
 * compacta, de quem é o conserto, "Tentar de novo" e a cópia do texto técnico.
 *
 * Falha sem etapa conhecida ([failure] nulo) mostra a mensagem de antes — não se
 * inventa etapa para o que não foi classificado — mas também ganha o "Tentar de novo".
 */
@Composable
fun TeamConnectionErrorState(
    failure: TeamConnectionFailure?,
    fallbackMessage: String,
    technicalDetail: String,
    kind: TeamCredentialKind,
    language: AppLanguage,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (failure == null) {
        AppErrorState(
            message = fallbackMessage,
            modifier = modifier,
            retryLabel = TeamConnectionLabels.retry(language),
            onRetry = onRetry
        )
        return
    }
    val clipboard = LocalClipboardManager.current
    AppErrorState(
        message = TeamConnectionLabels.headline(failure, kind, language),
        modifier = modifier,
        detail = TeamConnectionLabels.advice(failure, kind, language),
        retryLabel = TeamConnectionLabels.retry(language),
        onRetry = onRetry,
        secondaryLabel = TeamConnectionLabels.copyDetail(language),
        onSecondary = { clipboard.setText(AnnotatedString(technicalDetail)) }
    ) {
        AppStepTrail(teamConnectionSteps(failure, kind, language))
    }
}

/**
 * Erro de uma ação numa linha (remover, emitir chave, exportar): a lista
 * continua na tela e o erro diz onde parou, nunca a exceção crua.
 */
@Composable
fun TeamConnectionNotice(
    failure: TeamConnectionFailure?,
    fallbackMessage: String,
    kind: TeamCredentialKind,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val text = if (failure == null) fallbackMessage else TeamConnectionLabels.headline(failure, kind, language)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AppStatusDot(tone = AppTone.CRITICAL)
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = AppTone.CRITICAL.color())
    }
}

/** Caminho completo, uma etapa por linha, e o conselho embaixo (Configurações › Time). */
@Composable
fun TeamConnectionPath(
    failure: TeamConnectionFailure,
    kind: TeamCredentialKind,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        AppStepList(teamConnectionSteps(failure, kind, language))
        Text(
            text = TeamConnectionLabels.advice(failure, kind, language),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
