package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween

/**
 * Um número que desliza quando muda.
 *
 * A coleta de dez em dez minutos trocava o `41%` por `68%` num quadro, e em
 * quatro cards ao mesmo tempo: a mudança existia, mas o olho não a pegava. O
 * valor novo entra **de baixo quando sobe e de cima quando desce** — a direção
 * do movimento diz a direção da mudança antes de o número ser lido.
 *
 * Mola `GENTLE`, sem rebote: número que passa do lugar e volta é o mesmo defeito
 * da barra que passa do percentual. O texto continua sendo o formatado pelo
 * chamador (`compactPercentageLabel`, valor em dinheiro, contagem): esta
 * primitiva não formata nada, só troca.
 *
 * Durante a transição existem dois nós de texto; depois do idle, um só — é o
 * que os testes de componente veem.
 */
@Composable
fun AppAnimatedNumber(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    /** De onde a caixa cresce quando o número muda de largura (`9%` → `10%`). */
    contentAlignment: Alignment = Alignment.CenterStart
) {
    val slide = appSpring<IntOffset>(AppMotion.Springs.GENTLE, visibilityThreshold = IntOffset.VisibilityThreshold)
    val size = appSpring<IntSize>(AppMotion.Springs.GENTLE, visibilityThreshold = IntSize.VisibilityThreshold)
    val enterFade = appTween<Float>(AppMotion.normal)
    val exitFade = appTween<Float>(AppMotion.exit, AppMotion.exitEasing)
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            val rising = numericDirection(initialState, targetState) >= 0
            val enter = slideInVertically(slide) { height -> if (rising) height / 2 else -height / 2 } +
                fadeIn(enterFade)
            val exit = slideOutVertically(slide) { height -> if (rising) -height / 2 else height / 2 } +
                fadeOut(exitFade)
            (enter togetherWith exit).using(SizeTransform(clip = true) { _, _ -> size })
        },
        label = "appAnimatedNumber"
    ) { value ->
        Text(
            text = value,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

/**
 * Sinal da variação entre dois textos numéricos: positivo se subiu, negativo se
 * desceu, zero se não dá para dizer. Lê só dígitos e o separador decimal —
 * `41%`, `US$ 12,84`, `1.234` —, e texto sem número algum (`—`) não tem direção.
 */
internal fun numericDirection(before: String, after: String): Int {
    val old = leadingNumber(before) ?: return 0
    val new = leadingNumber(after) ?: return 0
    return new.compareTo(old)
}

private fun leadingNumber(text: String): Double? {
    val digits = StringBuilder()
    var decimalSeen = false
    for (char in text) {
        when {
            char.isDigit() -> digits.append(char)
            (char == ',' || char == '.') && digits.isNotEmpty() && !decimalSeen -> {
                digits.append('.')
                decimalSeen = true
            }
        }
    }
    return digits.toString().trimEnd('.').toDoubleOrNull()
}
