package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import kotlin.time.Instant

const val HISTORY_ACTIVE_SPAN_KEY_TAG = "historyActiveSpanKey"

/**
 * Chave da faixa ativa sob o gráfico (#392). Era uma frase solta que sempre
 * descrevia a janela aberta — com o zoom em outro trecho, ela falava de uma
 * faixa que nem estava na tela. Agora tem amostra da marca e escolhe a frase
 * pelo trecho visível.
 *
 * [currentSpan] é a faixa ativa da janela aberta; `null` sem janela aberta com
 * uso. [insideText] vale quando ela aparece no trecho visível, [outsideText]
 * quando o zoom a deixou de fora, [genericText] sem janela aberta.
 */
data class HistoryActiveSpanKey(
    val currentSpan: ClosedRange<Instant>?,
    val insideText: String,
    val outsideText: String,
    val genericText: String
)

/** A frase da chave para o trecho visível [visibleFrom]..[visibleUntil]. */
internal fun activeSpanKeyText(key: HistoryActiveSpanKey, visibleFrom: Instant, visibleUntil: Instant): String {
    val span = key.currentSpan ?: return key.genericText
    val overlaps = span.start <= visibleUntil && span.endInclusive >= visibleFrom
    return if (overlaps) key.insideText else key.outsideText
}

/** Amostra da faixa (fundo claro com a chave no topo, como no gráfico) e a frase. */
@Composable
internal fun HistoryActiveSpanKeyRow(text: String, color: Color, textColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(HISTORY_ACTIVE_SPAN_KEY_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.padding(top = 3.dp).width(14.dp)) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(color))
            Box(Modifier.fillMaxWidth().height(6.dp).background(color.copy(alpha = ACTIVE_SPAN_SWATCH_ALPHA)))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

/** Mais forte que o fundo do gráfico: a amostra tem 14dp, a faixa ocupa o plot. */
private const val ACTIVE_SPAN_SWATCH_ALPHA = 0.3f

/**
 * O que fica sob o gráfico: a nota do tracejado e a chave da faixa ativa. Fora
 * do composable do gráfico pelo limite de 300 linhas por função.
 */
@Composable
internal fun HistoryChartFooter(
    showPreviousNote: Boolean,
    activeSpanKeyText: String?,
    lineColor: Color,
    textColor: Color,
    language: AppLanguage
) {
    // A cor não basta para dizer "isto é o período anterior" — o traçado
    // sozinho não carrega a legenda, e por escrito é a mesma regra que já vale
    // para todo estado deste sistema.
    if (showPreviousNote) {
        Text(
            text = if (language == AppLanguage.PT) {
                "Tracejado: mesmo ponto do período anterior"
            } else {
                "Dashed: same point last period"
            },
            style = MaterialTheme.typography.labelSmall,
            color = textColor
        )
    }
    if (activeSpanKeyText != null) {
        HistoryActiveSpanKeyRow(text = activeSpanKeyText, color = lineColor, textColor = textColor)
    }
}
