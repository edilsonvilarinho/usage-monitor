package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AnalysisPeriodBar
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Barras de pico por período (#538, A05b): uma por dia (7 d) ou por semana (30 d, tudo). A escala
 * começa em 100 % e cresce se algum pico passar; pico ausente (`null`) não desenha barra. A barra
 * do período corrente sai cheia; as anteriores, meio-tom.
 */
@Composable
internal fun AnalysisPeriodBars(bars: List<AnalysisPeriodBar>, barColor: Color, modifier: Modifier = Modifier) {
    val scale = (bars.mapNotNull { bar -> bar.peakPercent }.maxOrNull() ?: 0).coerceAtLeast(100).toFloat()
    Row(
        modifier = modifier.fillMaxWidth().height(96.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        bars.forEach { bar ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                val fraction = ((bar.peakPercent ?: 0) / scale).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(fraction)
                        .background(if (bar.current) barColor else barColor.copy(alpha = 0.5f))
                )
            }
        }
    }
}
