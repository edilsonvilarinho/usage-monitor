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
 */
@Composable
internal fun RiskSemaphoreDot(
    risk: QuotaRiskSummary,
    quotaLabel: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    HoverTooltipBox(
        metrics = emptyList(),
        title = riskDotTooltipTitle(language),
        subtitle = riskDotTooltipSubtitle(risk, language),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colorFor(risk.level))
                .semantics {
                    contentDescription = riskDotContentDescription(risk, quotaLabel, language)
                }
        )
    }
}
