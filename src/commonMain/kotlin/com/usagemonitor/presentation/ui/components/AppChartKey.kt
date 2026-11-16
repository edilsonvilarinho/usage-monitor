package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Papel do traço na legenda: a amostra desenha o mesmo estilo que o gráfico usa para ele. */
internal enum class AppChartKeyStyle { SOLID, DASHED, DOTTED, FADED, ZONE }

/** Uma entrada da legenda: rótulo, estilo do traço e a cor já resolvida pelo tema. */
internal data class AppChartKeyItem(val label: String, val style: AppChartKeyStyle, val color: Color)

/** Tag da amostra de um estilo; o teste acha a amostra por ela. */
internal fun appChartKeySampleTag(style: AppChartKeyStyle): String = "appChartKey-sample-${style.name}"

private val SAMPLE_WIDTH: Dp = 18.dp
private val SAMPLE_HEIGHT: Dp = 8.dp
private val SAMPLE_STROKE: Dp = 2.dp

/** Opacidade da amostra "FADED", a mesma faixa das janelas anteriores atrás da aberta. */
private const val FADED_SAMPLE_ALPHA = 0.45f

/** Fundo da amostra "ZONE": a região pintada no gráfico, em tom fraco. */
private const val ZONE_SAMPLE_ALPHA = 0.18f

/**
 * Legenda de gráfico (#610, A02): uma linha por item, com a amostra do estilo do traço e o rótulo
 * em texto. O rótulo é o que lê o leitor de tela e o que sobrevive sem cor; a amostra só repete o traço.
 */
@Composable
internal fun AppChartKey(items: List<AppChartKeyItem>, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                AppChartKeySample(item.style, item.color)
                Text(item.label, style = labelStyle, color = textColor, maxLines = 1)
            }
        }
    }
}

/** Amostra de um estilo: desenha o traço como o gráfico, com a tag que o teste procura. */
@Composable
private fun AppChartKeySample(style: AppChartKeyStyle, color: Color) {
    Canvas(Modifier.size(SAMPLE_WIDTH, SAMPLE_HEIGHT).testTag(appChartKeySampleTag(style))) {
        val y = size.height / 2f
        val stroke = SAMPLE_STROKE.toPx()
        val start = Offset(0f, y)
        val end = Offset(size.width, y)
        when (style) {
            AppChartKeyStyle.SOLID -> drawLine(color, start, end, strokeWidth = stroke)
            AppChartKeyStyle.DASHED -> drawLine(
                color, start, end, strokeWidth = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
            AppChartKeyStyle.DOTTED -> drawLine(
                color, start, end, strokeWidth = stroke, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1.dp.toPx(), 3.dp.toPx()))
            )
            AppChartKeyStyle.FADED -> drawLine(color.copy(alpha = FADED_SAMPLE_ALPHA), start, end, strokeWidth = stroke)
            AppChartKeyStyle.ZONE -> drawRect(color.copy(alpha = ZONE_SAMPLE_ALPHA))
        }
    }
}
