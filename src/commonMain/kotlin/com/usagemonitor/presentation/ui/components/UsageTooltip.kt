package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes

internal data class TooltipMetric(
    val label: String,
    val value: String
)

@Composable
internal fun UsageTooltipContent(
    metrics: List<TooltipMetric>,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    /**
     * Frase que explica o estado, abaixo de uma divisória.
     *
     * Existe porque a métrica sozinha diz *qual* é o estado e não o que ele
     * significa: "Projeção de uso — Normal" não conta que a leitura é sobre a cota
     * resetar antes de esgotar. É `bodySmall` — sans, texto corrido — e não `label*`,
     * que nesta escala é rótulo e número.
     */
    footnote: String? = null
) {
    Surface(
        modifier = modifier.widthIn(max = 280.dp),
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        // Overlay curto: 2dp de sombra em vez de 12. A tooltip flutua sobre o
        // gráfico, não sobre a janela inteira.
        tonalElevation = AppElevation.raised,
        shadowElevation = AppElevation.raised,
        border = BorderStroke(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 180.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            metrics.forEach { metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = metric.value,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End
                    )
                }
            }

            if (!footnote.isNullOrBlank()) {
                AppDivider()
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HoverTooltipBox(
    metrics: List<TooltipMetric>,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    footnote: String? = null,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            UsageTooltipContent(
                title = title,
                subtitle = subtitle,
                metrics = metrics,
                footnote = footnote
            )
        },
        state = rememberTooltipState(isPersistent = true)
    ) {
        Box(modifier = modifier) {
            content()
        }
    }
}
