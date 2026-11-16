package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Comparador de ritmo do card (#610, D3): duas barras na mesma escala, marca do seguro, valor e palavra. Cor nunca sozinha. */
@Composable
internal fun AnalysisPaceComparator(model: PaceComparatorModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = AppSpacing.xs), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(model.eyebrow, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        model.rows.forEach { row -> PaceComparatorLine(row, model.marker) }
        model.safeCaption?.let { caption ->
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PaceComparatorLine(row: PaceComparatorRow, marker: Float?) {
    val tone = when (row.aboveSafe) {
        true -> AppTone.CRITICAL
        false -> AppTone.OK
        null -> AppTone.NEUTRAL
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(row.label, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        val fraction = row.fraction
        if (fraction != null) {
            AppProgressTrack(fraction = fraction, tone = tone, modifier = Modifier.weight(1f), marker = marker)
        } else {
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.weight(0.6f), horizontalAlignment = Alignment.End) {
            Text(row.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            row.verdict?.let { word ->
                Text(word, style = MaterialTheme.typography.labelSmall, color = tone.color(), maxLines = 1)
            }
        }
    }
}
