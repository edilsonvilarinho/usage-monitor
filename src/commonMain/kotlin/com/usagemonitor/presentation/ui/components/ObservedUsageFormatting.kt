package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit

/*
 * Formatação da atividade observada (OpenCode Zen Free, Kilo Free, Gemini CLI): contagens
 * locais por modelo, sem limite. Saiu de `ApiUsageCardFormatting.kt` (782 linhas) pela regra
 * de tamanho; card, HUD e histórico usam as mesmas funções.
 */

internal data class ObservedUsageModelSummary(
    val modelName: String,
    val amountFiveHours: Long,
    val amountSevenDays: Long,
    val unit: UsageUnit
)

internal fun localizedObservedCount(
    value: Long,
    unit: UsageUnit,
    language: AppLanguage
): String {
    val amount = if (unit == UsageUnit.TOKENS) abbreviate(value) else value.toString()
    return "$amount ${observedUnitNoun(value, unit, language)}"
}

/** O substantivo da contagem observada, no plural certo para [value]: "tokens", "requisição", "requisições". */
internal fun observedUnitNoun(value: Long, unit: UsageUnit, language: AppLanguage): String {
    return when {
        unit == UsageUnit.TOKENS -> "tokens"
        language == AppLanguage.PT -> if (value == 1L) "requisição" else "requisições"
        else -> if (value == 1L) "request" else "requests"
    }
}

internal fun compactObservedCount(value: Long, unit: UsageUnit): String {
    return when (unit) {
        UsageUnit.REQUESTS -> "${abbreviate(value)} req."
        UsageUnit.TOKENS -> "${abbreviate(value)} tok"
        else -> value.toString()
    }
}

internal fun observedPrimaryWindowLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Últimas 5h" else "Last 5h"
}

/**
 * A janela semanal por extenso, como no balão do HUD e na sua descrição acessível.
 * O tooltip do card usa a forma curta "Últimos 7d" por espaço (`AppHudBar.prompt.md`).
 */
internal fun observedWeekWindowLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Últimos 7 dias" else "Last 7 days"
}

internal fun observedSecondaryWindowLabel(
    value: Long,
    source: ApiSource,
    unit: UsageUnit,
    language: AppLanguage
): String {
    val displayValue = if (source == ApiSource.GEMINI) {
        localizedObservedCount(value, unit, language)
    } else {
        value.toString()
    }
    return "7d: $displayValue"
}

internal fun buildObservedUsageTooltipMetrics(
    summary: ObservedUsageModelSummary,
    language: AppLanguage
): List<TooltipMetric> {
    return listOf(
        TooltipMetric(
            label = observedPrimaryWindowLabel(language),
            value = localizedObservedCount(summary.amountFiveHours, summary.unit, language)
        ),
        TooltipMetric(
            label = if (language == AppLanguage.PT) "Últimos 7d" else "Last 7d",
            value = localizedObservedCount(summary.amountSevenDays, summary.unit, language)
        )
    )
}

internal fun buildObservedUsageSummaries(quotas: List<QuotaInfo>): List<ObservedUsageModelSummary> {
    val grouped = linkedMapOf<String, ObservedUsageModelSummary>()

    quotas.forEach { quota ->
        val modelName = when {
            quota.label.endsWith(" 5h") -> quota.label.removeSuffix(" 5h")
            quota.label.endsWith(" 7d") -> quota.label.removeSuffix(" 7d")
            else -> quota.label
        }

        val existing = grouped[modelName] ?: ObservedUsageModelSummary(
            modelName = modelName,
            amountFiveHours = 0L,
            amountSevenDays = 0L,
            unit = quota.unit
        )
        grouped[modelName] = when {
            quota.label.endsWith(" 5h") -> existing.copy(amountFiveHours = quota.used)
            quota.label.endsWith(" 7d") -> existing.copy(amountSevenDays = quota.used)
            else -> existing
        }
    }

    return grouped.values.toList()
}
