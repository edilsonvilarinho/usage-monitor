package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.resumeSessionCommand

/** Quanto tempo o botão fica confirmando a cópia antes de voltar ao normal. */
private const val COPY_FEEDBACK_MILLIS = 2_000L

/**
 * Copia o que permite voltar à sessão.
 *
 * A tela mostra o id truncado em oito caracteres, que não retoma nada: o
 * `claude --resume` só volta direto para a conversa com o id inteiro. O botão
 * existe para entregar esse valor completo sem obrigar ninguém a caçá-lo em
 * `~/.claude/projects`.
 *
 * [isLocalSession] falso é a sessão de um colega, vinda do servidor de time: o
 * transcript não está nesta máquina, então ali se copia apenas o identificador —
 * um comando de retomada que cairia num seletor vazio seria pior que botão
 * nenhum.
 */
@Composable
internal fun CopySessionCommandButton(
    sessionId: String,
    language: AppLanguage,
    isLocalSession: Boolean = true,
    /** `false` deixa só o ícone; o texto fica na tooltip, para a lista densa. */
    showLabel: Boolean = false,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit = rememberClipboardWriter()
) {
    // Chaveado pela sessão: a lista se republica a cada tique do laço ao vivo, e
    // sem a chave a confirmação de uma linha apareceria na linha seguinte.
    var copied by remember(sessionId) { mutableStateOf(false) }

    LaunchedEffect(sessionId, copied) {
        if (!copied) {
            return@LaunchedEffect
        }
        delay(COPY_FEEDBACK_MILLIS)
        copied = false
    }

    val idleLabel = if (isLocalSession) {
        CliSessionsLabels.copyResumeCommand(language)
    } else {
        CliSessionsLabels.copySessionId(language)
    }
    val label = if (copied) CliSessionsLabels.copied(language) else idleLabel
    val payload = if (isLocalSession) resumeSessionCommand(sessionId) else sessionId
    val onClick = {
        onCopy(payload)
        copied = true
    }

    if (showLabel) {
        // Botão do sistema, com o ícone no slot de `leading`: o `TextButton` do
        // Material trazia altura, raio e cor próprios, e este fica ao lado de
        // botões que já são retângulo de raio 6.
        AppButton(
            label = label,
            onClick = onClick,
            tone = AppButtonTone.GHOST,
            leading = { CopyStateIcon(copied = copied) },
            modifier = modifier.semantics { contentDescription = label }
        )
        return
    }

    HoverTooltipBox(
        metrics = emptyList(),
        title = label,
        modifier = modifier
    ) {
        // Quadrado de 26dp como todo botão de ícone do app: numa linha de 32dp,
        // o círculo de 40dp do Material era o item mais alto e definia a altura
        // da lista inteira.
        AppIconButton(
            contentDescription = label,
            onClick = onClick
        ) {
            CopyStateIcon(copied = copied)
        }
    }
}

@Composable
private fun CopyStateIcon(copied: Boolean) {
    Icon(
        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        // Verde de confirmação e não o azul de destaque: o estado que o ícone
        // relata é "deu certo", que no resto do app é `AppTone.OK`.
        tint = if (copied) {
            AppTone.OK.color()
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

/**
 * Escrita no clipboard do sistema, isolada num valor injetável.
 *
 * O teste de componente passa a própria lambda: escrever no clipboard real
 * durante a suíte apagaria o que o usuário tem copiado.
 */
@Composable
internal fun rememberClipboardWriter(): (String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return remember(clipboard) {
        { text -> clipboard.setText(AnnotatedString(text)) }
    }
}
