package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaRiskSummary

/**
 * Indicador semáforo de projeção: verde (deve resetar antes de esgotar),
 * amarelo/vermelho (deve esgotar antes do reset, com gradação por proximidade).
 *
 * `showTooltip = false` é usado quando o ponto está dentro de um container que já
 * tem tooltip própria (badge resumido): dois TooltipBox aninhados disputam o mesmo
 * hover. O `contentDescription` é mantido nos dois casos.
 */
@Composable
internal fun RiskSemaphoreDot(
    risk: QuotaRiskSummary,
    quotaLabel: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    showTooltip: Boolean = true
) {
    if (!showTooltip) {
        RiskSemaphoreIndicator(
            risk = risk,
            quotaLabel = quotaLabel,
            language = language,
            size = size,
            modifier = modifier
        )
        return
    }

    HoverTooltipBox(
        metrics = emptyList(),
        title = riskDotTooltipTitle(language),
        subtitle = riskDotTooltipSubtitle(risk, language),
        modifier = modifier
    ) {
        RiskSemaphoreIndicator(
            risk = risk,
            quotaLabel = quotaLabel,
            language = language,
            size = size
        )
    }
}

@Composable
private fun RiskSemaphoreIndicator(
    risk: QuotaRiskSummary,
    quotaLabel: String,
    language: AppLanguage,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorFor(risk.level))
            .semantics {
                contentDescription = riskDotContentDescription(risk, quotaLabel, language)
            }
    )
}
