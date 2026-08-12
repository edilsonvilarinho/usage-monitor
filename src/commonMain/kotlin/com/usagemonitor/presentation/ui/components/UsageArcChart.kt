package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.usagemonitor.presentation.ui.theme.AppMotion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.UsageUnit

/**
 * Componente de arco de progresso para exibir uso de tokens/requisições.
 *
 * ATOMIC DESIGN — Átomo: componente mínimo, sem estado interno (stateless).
 * Recebe dados como parâmetros e renderiza. Não chama APIs, não tem lógica.
 *
 * Equivalente a um componente "presentational" do React/Vue:
 * só exibe o que recebe nas props.
 *
 * @param used       Quantidade consumida
 * @param total      Limite total (0 = sem limite)
 * @param label      Texto central inferior (ex: nome do modelo)
 * @param unit       TOKENS ou REQUESTS (para formatação do texto)
 * @param size       Tamanho do arco em dp
 * @param strokeWidth Espessura da linha do arco
 */
@Composable
fun UsageArcChart(
    used: Long,
    total: Long,
    unit: UsageUnit,
    modifier: Modifier = Modifier,
    currencyCode: String = "USD",
    size: Dp = 80.dp,
    strokeWidth: Dp = 10.dp,
    percentageTextStyle: TextStyle? = null
) {
    val isCurrency = unit == UsageUnit.CURRENCY_USD

    val percentage = if (isCurrency) {
        if (total > 0L) 1f else 0f
    } else if (total > 0L) {
        (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = 800, easing = AppMotion.enterEasing),
        label = "arcAnimation"
    )

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    val usedColor = if (isCurrency) arcColor(0f) else arcColor(percentage)
    val resolvedTextStyle = percentageTextStyle ?: MaterialTheme.typography.headlineMedium

    val centerText = if (isCurrency) {
        formatCents(total, currencyCode)
    } else {
        "${(percentage * 100).toInt()}%"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedPercentage > 0f) {
                drawArc(
                    color = usedColor.copy(alpha = 0.12f),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedPercentage,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx * 2.8f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = usedColor.copy(alpha = 0.22f),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedPercentage,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx * 1.5f, cap = StrokeCap.Round)
                )
                drawArc(
                    color = usedColor,
                    startAngle = 135f,
                    sweepAngle = 270f * animatedPercentage,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = centerText,
            style = resolvedTextStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

/** Cor do arco com base no nível de uso: tertiary (baixo) → primary (médio) → error (alto). */
private fun arcColor(percentage: Float): androidx.compose.ui.graphics.Color {
    return when {
        percentage >= 0.9f -> androidx.compose.ui.graphics.Color(0xFFF44336)
        percentage >= 0.75f -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        else -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
    }
}
