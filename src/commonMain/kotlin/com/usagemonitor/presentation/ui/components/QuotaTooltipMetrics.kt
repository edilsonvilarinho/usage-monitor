package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

/*
 * Métricas da tooltip de cota que saíram de `ApiUsageCardFormatting.kt` pelo
 * limite de 800 linhas, quando a marca de ritmo (issue #327) pediu uma nova.
 */

// A projeção só entra na tooltip quando o card resumido suprime a tooltip própria
// do RiskSemaphoreDot — evita TooltipBox aninhado dentro do badge.
internal fun MutableList<TooltipMetric>.addProjectionMetric(
    risk: QuotaRiskSummary?,
    language: AppLanguage
) {
    if (risk == null) {
        return
    }

    add(
        TooltipMetric(
            label = riskDotTooltipTitle(language),
            value = riskLevelLabel(risk.level, language)
        )
    )
}

/**
 * O texto da marca de ritmo da barra (issue #327). A marca é só posição, e a
 * tooltip é quem diz o que ela significa — senão a barra informaria por forma
 * sem legenda nenhuma.
 */
internal fun MutableList<TooltipMetric>.addElapsedWindowMetric(
    quota: QuotaInfo,
    language: AppLanguage,
    now: Instant
) {
    val label = elapsedWindowLabel(quota, language, now) ?: return
    add(
        TooltipMetric(
            label = if (language == AppLanguage.PT) "Janela decorrida" else "Window elapsed",
            value = label
        )
    )
}

/** "42%" — a fração da janela decorrida, ou `null` quando a fonte não dá o início. */
internal fun elapsedWindowLabel(quota: QuotaInfo, language: AppLanguage, now: Instant): String? {
    val fraction = quota.elapsedFractionAt(now) ?: return null
    val percent = (fraction * 100).roundToInt()
    return if (language == AppLanguage.PT) "$percent% (marca na barra)" else "$percent% (mark on the bar)"
}
