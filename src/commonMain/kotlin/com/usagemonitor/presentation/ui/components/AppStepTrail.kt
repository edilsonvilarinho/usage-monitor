package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Situação de uma etapa de um caminho (issue #494: o caminho da conexão com o
 * servidor de time). [UNKNOWN] é a etapa que a falha não prova — nunca vira ✓.
 */
enum class AppStepStatus { DONE, FAILED, NOT_REACHED, UNKNOWN }

data class AppStep(val label: String, val status: AppStepStatus, val detail: String? = null)

private val STEP_GLYPH_WIDTH = 16.dp
private val STEP_LABEL_WIDTH = 112.dp

/** Glifo escrito de cada estado: cor nunca informa sozinha. */
internal fun AppStepStatus.glyph(): String {
    return when (this) {
        AppStepStatus.DONE -> "✓"
        AppStepStatus.FAILED -> "×"
        AppStepStatus.NOT_REACHED -> "–"
        AppStepStatus.UNKNOWN -> "?"
    }
}

@Composable
private fun AppStepStatus.textColor(): Color {
    return when (this) {
        AppStepStatus.DONE -> MaterialTheme.colorScheme.onSurface
        AppStepStatus.FAILED -> AppTone.CRITICAL.color()
        AppStepStatus.NOT_REACHED,
        AppStepStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/**
 * Trilha compacta: as etapas numa linha que quebra, "✓ Endereço › × Certificado".
 *
 * A etapa que falhou ganha a borda crítica; as que não foram alcançadas ficam em
 * muted. Sem `weight` dentro do `FlowRow` (armadilha de teste de tela).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppStepTrail(steps: List<AppStep>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                Text(text = "›", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val borderColor = if (step.status == AppStepStatus.FAILED) {
                AppTone.CRITICAL.color()
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Text(
                text = "${step.status.glyph()} ${step.label}",
                style = MaterialTheme.typography.labelSmall,
                color = step.status.textColor(),
                maxLines = 1,
                modifier = Modifier
                    .border(AppBorderWidth, borderColor, AppShapes.extraSmall)
                    .padding(horizontal = AppSpacing.sm, vertical = 2.dp)
            )
        }
    }
}

/** Caminho completo: uma etapa por linha, glifo, nome e o que aconteceu nela. */
@Composable
fun AppStepList(steps: List<AppStep>, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEach { step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = step.status.glyph(),
                    style = MaterialTheme.typography.labelSmall,
                    color = step.status.textColor(),
                    modifier = Modifier.width(STEP_GLYPH_WIDTH)
                )
                Text(
                    text = step.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(STEP_LABEL_WIDTH)
                )
                val detail = step.detail
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = step.status.textColor(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
