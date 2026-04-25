package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    label: String,
    unit: UsageUnit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 12.dp
) {
    // Calcula o percentual (0.0 a 1.0)
    val percentage = if (total > 0L) {
        (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Animação suave ao carregar ou atualizar os dados
    val animatedPercentage by animateFloatAsState(
        targetValue = percentage,
        animationSpec = tween(durationMillis = 800),
        label = "arcAnimation"
    )

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val usedColor = arcColor(percentage)
    val textStyle = MaterialTheme.typography.bodySmall
    val labelStyle = MaterialTheme.typography.labelSmall

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val diameter = this.size.minDimension - strokePx
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)
            val arcSize = Size(diameter, diameter)

            // Trilha de fundo (arco completo)
            drawArc(
                color = trackColor,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Arco de progresso
            if (animatedPercentage > 0f) {
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

        // Texto central: percentual e label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(percentage * 100).toInt()}%",
                style = textStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatUsage(used, total, unit),
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Cor do arco muda conforme o nível de uso: verde → amarelo → vermelho. */
private fun arcColor(percentage: Float): Color {
    return when {
        percentage < 0.6f -> Color(0xFF4CAF50)  // verde
        percentage < 0.85f -> Color(0xFFFFC107) // amarelo
        else -> Color(0xFFF44336)               // vermelho
    }
}

/** Formata o texto de uso abreviado (ex: "50K/200K tok", "22/45 req", "15%"). */
private fun formatUsage(used: Long, total: Long, unit: UsageUnit): String {
    return when (unit) {
        UsageUnit.PERCENTAGE -> "${used}%"
        UsageUnit.TOKENS     -> "${abbreviate(used)}/${abbreviate(total)} tok"
        UsageUnit.REQUESTS   -> "${abbreviate(used)}/${abbreviate(total)} req"
    }
}

/** Abrevia números grandes: 1500 → "1.5K", 1200000 → "1.2M". */
private fun abbreviate(n: Long): String {
    return when {
        n >= 1_000_000L -> "${"%.1f".format(n / 1_000_000f)}M"
        n >= 1_000L     -> "${"%.1f".format(n / 1_000f)}K"
        else            -> n.toString()
    }
}
