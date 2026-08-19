package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliActivityHeatmap
import com.usagemonitor.presentation.ui.theme.AppShapes
import kotlinx.datetime.DayOfWeek

const val ACTIVITY_HEATMAP_TAG = "activityHeatmap"

private val CELL_SIZE = 14.dp
private val CELL_SPACING = 2.dp
private val DAY_LABEL_WIDTH = 32.dp

/** Uma etiqueta a cada três horas; 24 números lado a lado viram uma faixa ilegível. */
private const val HOUR_LABEL_STEP = 3

/** Piso de opacidade das células com atividade, para não sumirem no fundo. */
private const val MIN_ACTIVE_ALPHA = 0.18f

/**
 * Grade 7×24 de atividade, dia da semana × hora local.
 *
 * Stateless e sem animação: a lista que a contém é republicada pelo laço ao vivo
 * de cinco em cinco segundos, e uma transição infinita travaria o `waitForIdle`
 * dos testes de componente.
 */
@Composable
fun ActivityHeatmapGrid(
    heatmap: CliActivityHeatmap,
    accent: Color,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.horizontalScroll(rememberScrollState()).testTag(ACTIVITY_HEATMAP_TAG),
        verticalArrangement = Arrangement.spacedBy(CELL_SPACING)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CELL_SPACING)) {
            Spacer(modifier = Modifier.width(DAY_LABEL_WIDTH))
            for (hour in 0..23) {
                Box(modifier = Modifier.size(CELL_SIZE), contentAlignment = Alignment.Center) {
                    if (hour % HOUR_LABEL_STEP == 0) {
                        Text(
                            text = hour.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        for (day in DayOfWeek.entries) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel(day, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(DAY_LABEL_WIDTH)
                )
                for (hour in 0..23) {
                    val intensity = heatmap.intensityAt(day, hour)
                    Box(
                        modifier = Modifier
                            .size(CELL_SIZE)
                            // Raio 4 e não 6: a célula tem 14dp, e um raio de
                            // botão numa célula desse tamanho a deixa quase
                            // redonda — a grade deixa de ler como grade.
                            .clip(AppShapes.extraSmall)
                            .background(cellColor(intensity, accent, emptyColor))
                    )
                }
            }
        }
    }
}

/**
 * Célula sem atividade fica com a cor de fundo, não com o acento transparente:
 * a diferença entre "nada aconteceu" e "aconteceu pouco" é justamente o que a
 * grade precisa mostrar.
 */
private fun cellColor(intensity: Float, accent: Color, emptyColor: Color): Color {
    if (intensity <= 0f) {
        return emptyColor
    }
    return accent.copy(alpha = MIN_ACTIVE_ALPHA + (1f - MIN_ACTIVE_ALPHA) * intensity)
}

internal fun dayLabel(day: DayOfWeek, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        when (day) {
            DayOfWeek.MONDAY -> "Seg"
            DayOfWeek.TUESDAY -> "Ter"
            DayOfWeek.WEDNESDAY -> "Qua"
            DayOfWeek.THURSDAY -> "Qui"
            DayOfWeek.FRIDAY -> "Sex"
            DayOfWeek.SATURDAY -> "Sáb"
            DayOfWeek.SUNDAY -> "Dom"
        }
    } else {
        when (day) {
            DayOfWeek.MONDAY -> "Mon"
            DayOfWeek.TUESDAY -> "Tue"
            DayOfWeek.WEDNESDAY -> "Wed"
            DayOfWeek.THURSDAY -> "Thu"
            DayOfWeek.FRIDAY -> "Fri"
            DayOfWeek.SATURDAY -> "Sat"
            DayOfWeek.SUNDAY -> "Sun"
        }
    }
}
