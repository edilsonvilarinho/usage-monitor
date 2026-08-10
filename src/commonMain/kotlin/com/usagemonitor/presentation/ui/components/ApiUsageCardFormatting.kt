package com.usagemonitor.presentation.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

internal data class OpenCodeModelSummary(
    val modelName: String,
    val requestsFiveHours: Long,
    val requestsSevenDays: Long
)

internal fun localizedRequestCount(value: Long, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        "$value requisições"
    } else {
        "$value requests"
    }
}

internal fun openCodePrimaryWindowLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Últimas 5h" else "Last 5h"
}

internal fun openCodeSecondaryWindowLabel(value: Long, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        "7d: $value"
    } else {
        "7d: $value"
    }
}

internal fun buildOpenCodeTooltipMetrics(
    summary: OpenCodeModelSummary,
    language: AppLanguage
): List<TooltipMetric> {
    return listOf(
        TooltipMetric(
            label = openCodePrimaryWindowLabel(language),
            value = "${summary.requestsFiveHours} req."
        ),
        TooltipMetric(
            label = if (language == AppLanguage.PT) "Últimos 7d" else "Last 7d",
            value = "${summary.requestsSevenDays} req."
        )
    )
}

internal fun buildOpenCodeModelSummaries(quotas: List<QuotaInfo>): List<OpenCodeModelSummary> {
    val grouped = linkedMapOf<String, OpenCodeModelSummary>()

    quotas.forEach { quota ->
        val modelName = when {
            quota.label.endsWith(" 5h") -> quota.label.removeSuffix(" 5h")
            quota.label.endsWith(" 7d") -> quota.label.removeSuffix(" 7d")
            else -> quota.label
        }

        val existing = grouped[modelName] ?: OpenCodeModelSummary(
            modelName = modelName,
            requestsFiveHours = 0L,
            requestsSevenDays = 0L
        )
        grouped[modelName] = when {
            quota.label.endsWith(" 5h") -> existing.copy(requestsFiveHours = quota.used)
            quota.label.endsWith(" 7d") -> existing.copy(requestsSevenDays = quota.used)
            else -> existing
        }
    }

    return grouped.values.toList()
}

internal fun expandedQuotaTitle(quota: QuotaInfo, language: AppLanguage): String {
    if (quota.unit == UsageUnit.CURRENCY_USD) {
        return quota.label
    }

    return when (quota.periodType) {
        PeriodType.INTERVAL -> if (language == AppLanguage.PT) "Sessão 5h" else "5h session"
        PeriodType.WEEKLY -> if (language == AppLanguage.PT) "Semanal" else "Weekly"
        PeriodType.REPORTED -> if (language == AppLanguage.PT) "Uso atual" else "Current usage"
    }
}

@Composable
internal fun cardContainerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) {
        scheme.surface.copy(alpha = 0.98f)
    } else {
        scheme.surface
    }
}

internal fun accentColorFor(source: ApiSource): Color {
    return when (source) {
        ApiSource.ANTHROPIC -> Color(0xFF4F8CFF)
        ApiSource.MINIMAX -> Color(0xFFFF8A3D)
        ApiSource.CODEX -> Color(0xFF27BFA3)
        ApiSource.DEEPSEEK -> Color(0xFFC084FC)
        ApiSource.OPENCODE -> Color(0xFF7BD389)
        ApiSource.KILO -> Color(0xFFE6D84E)
    }
}

internal fun refreshActionLabel(isRefreshing: Boolean, language: AppLanguage): String {
    return if (isRefreshing) {
        if (language == AppLanguage.PT) "Atualizando" else "Refreshing"
    } else {
        if (language == AppLanguage.PT) "Atualizar" else "Refresh"
    }
}

internal fun minimizeActionLabel(isMinimized: Boolean, language: AppLanguage): String {
    if (isMinimized) {
        return if (language == AppLanguage.PT) "Expandir card" else "Expand card"
    }

    return if (language == AppLanguage.PT) "Minimizar card" else "Minimize card"
}

internal fun historyActionLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Abrir histórico" else "Open history"
}

internal fun resetLabel(quota: QuotaInfo, language: AppLanguage): String {
    if (quota.unit == UsageUnit.CURRENCY_USD) {
        return if (language == AppLanguage.PT) "Saldo não expira" else "Balance never expires"
    }

    if (!quota.hasKnownResetAt) {
        return if (language == AppLanguage.PT) {
            "Janela de reset ainda não disponível"
        } else {
            "Reset window not available yet"
        }
    }

    val (dayFormatted, dateFormatted, timeFormatted) = formatBrtDateTimeParts(quota.periodEndAt, language)

    return if (language == AppLanguage.PT) {
        when (quota.periodType) {
            PeriodType.WEEKLY -> "Reinício: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.REPORTED -> "Reinício reportado: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.INTERVAL -> "Reinício: $dayFormatted $timeFormatted BRT"
        }
    } else {
        when (quota.periodType) {
            PeriodType.WEEKLY -> "Reset: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.REPORTED -> "Reported reset: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.INTERVAL -> "Reset: $dayFormatted $timeFormatted BRT"
        }
    }
}

private data class BrtDateTimeParts(val day: String, val date: String, val time: String)

private fun formatBrtDateTimeParts(instant: Instant, language: AppLanguage): BrtDateTimeParts {
    val saoPauloTz = TimeZone.of("America/Sao_Paulo")
    val local = instant.toLocalDateTime(saoPauloTz)
    val dayFormatted = when (local.dayOfWeek) {
        DayOfWeek.MONDAY -> if (language == AppLanguage.PT) "Seg" else "Mon"
        DayOfWeek.TUESDAY -> if (language == AppLanguage.PT) "Ter" else "Tue"
        DayOfWeek.WEDNESDAY -> if (language == AppLanguage.PT) "Qua" else "Wed"
        DayOfWeek.THURSDAY -> if (language == AppLanguage.PT) "Qui" else "Thu"
        DayOfWeek.FRIDAY -> if (language == AppLanguage.PT) "Sex" else "Fri"
        DayOfWeek.SATURDAY -> if (language == AppLanguage.PT) "Sáb" else "Sat"
        DayOfWeek.SUNDAY -> if (language == AppLanguage.PT) "Dom" else "Sun"
    }
    val dateFormatted = if (language == AppLanguage.PT) {
        "${local.date.dayOfMonth.toString().padStart(2, '0')}/${local.date.monthNumber.toString().padStart(2, '0')}"
    } else {
        "${local.date.monthNumber.toString().padStart(2, '0')}/${local.date.dayOfMonth.toString().padStart(2, '0')}"
    }
    val timeFormatted = if (language == AppLanguage.PT) {
        "${local.hour}h${local.minute.toString().padStart(2, '0')}"
    } else {
        "${local.hour}:${local.minute.toString().padStart(2, '0')}"
    }
    return BrtDateTimeParts(dayFormatted, dateFormatted, timeFormatted)
}

/** Formata um instante em BRT (America/Sao_Paulo) como "Seg 11/08 14h32" / "Mon 08/11 14:32". */
internal fun formatBrtDateTime(instant: Instant, language: AppLanguage): String {
    val parts = formatBrtDateTimeParts(instant, language)
    return "${parts.day} ${parts.date} ${parts.time}"
}

internal fun colorFor(level: UsageRiskLevel): Color {
    return when (level) {
        UsageRiskLevel.ON_TRACK -> Color(0xFF4CAF50)
        UsageRiskLevel.AT_RISK -> Color(0xFFFFC107)
        UsageRiskLevel.WILL_EXCEED -> Color(0xFFF44336)
    }
}

internal fun riskLevelLabel(level: UsageRiskLevel, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        when (level) {
            UsageRiskLevel.ON_TRACK -> "Normal"
            UsageRiskLevel.AT_RISK -> "Atenção"
            UsageRiskLevel.WILL_EXCEED -> "Crítico"
        }
    } else {
        when (level) {
            UsageRiskLevel.ON_TRACK -> "Normal"
            UsageRiskLevel.AT_RISK -> "Warning"
            UsageRiskLevel.WILL_EXCEED -> "Critical"
        }
    }
}

internal fun riskDotContentDescription(risk: QuotaRiskSummary, quotaLabel: String, language: AppLanguage): String {
    val levelLabel = riskLevelLabel(risk.level, language)
    return if (language == AppLanguage.PT) {
        "Risco de estouro $quotaLabel: $levelLabel"
    } else {
        "Overage risk $quotaLabel: $levelLabel"
    }
}

internal fun riskDotTooltipTitle(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Projeção de uso" else "Usage projection"
}

internal fun riskDotTooltipSubtitle(risk: QuotaRiskSummary, language: AppLanguage): String {
    val exhaustionAt = risk.estimatedExhaustionAt
    return if (exhaustionAt == null) {
        if (language == AppLanguage.PT) {
            "No ritmo atual, a cota deve resetar antes de esgotar."
        } else {
            "At the current pace, the quota should reset before running out."
        }
    } else {
        val formattedInstant = formatBrtDateTime(exhaustionAt, language)
        if (language == AppLanguage.PT) {
            "No ritmo atual, a cota deve esgotar antes do reset — previsão: $formattedInstant BRT."
        } else {
            "At the current pace, the quota should run out before reset — estimated: $formattedInstant BRT."
        }
    }
}

internal fun buildQuotaTooltipMetrics(
    quota: QuotaInfo,
    language: AppLanguage,
    risk: QuotaRiskSummary? = null
): List<TooltipMetric> {
    val metrics = mutableListOf<TooltipMetric>()

    if (quota.unit == UsageUnit.CURRENCY_USD) {
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
            value = formatCurrencyAmount(quota.total)
        )
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Status" else "Status",
            value = resetLabel(quota = quota, language = language)
        )
        metrics.addProjectionMetric(risk = risk, language = language)
        return metrics
    }

    metrics += TooltipMetric(
        label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
        value = quotaTooltipUsageValue(quota = quota, language = language)
    )

    if (quota.total > 0L) {
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Restante" else "Remaining",
            value = quotaTooltipRemainingValue(quota = quota)
        )
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Percentual" else "Percentage",
            value = "${(quota.percentageUsed * 100).roundToInt()}%"
        )
    }

    metrics += TooltipMetric(
        label = if (language == AppLanguage.PT) "Reset" else "Reset",
        value = resetLabel(quota = quota, language = language)
    )
    metrics.addProjectionMetric(risk = risk, language = language)
    return metrics
}

// A projeção só entra na tooltip quando o card resumido suprime a tooltip própria
// do RiskSemaphoreDot — evita TooltipBox aninhado dentro do badge.
private fun MutableList<TooltipMetric>.addProjectionMetric(
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

internal fun quotaTooltipUsageValue(quota: QuotaInfo, language: AppLanguage): String {
    return when (quota.unit) {
        UsageUnit.CURRENCY_USD -> formatCurrencyAmount(quota.total)
        UsageUnit.PERCENTAGE -> {
            if (quota.total > 0L) {
                "${quota.used}/${quota.total} %"
            } else {
                "${quota.used} %"
            }
        }
        UsageUnit.REQUESTS -> {
            if (quota.total > 0L) {
                "${quota.used}/${quota.total} req"
            } else if (language == AppLanguage.PT) {
                "${quota.used} req"
            } else {
                "${quota.used} req"
            }
        }
        UsageUnit.TOKENS -> {
            if (quota.total > 0L) {
                "${quota.used}/${quota.total} tok"
            } else {
                "${quota.used} tok"
            }
        }
    }
}

internal fun quotaTooltipRemainingValue(quota: QuotaInfo): String {
    return when (quota.unit) {
        UsageUnit.REQUESTS -> "${quota.remaining} req"
        UsageUnit.TOKENS -> "${quota.remaining} tok"
        UsageUnit.PERCENTAGE -> "${quota.remaining} %"
        UsageUnit.CURRENCY_USD -> formatCurrencyAmount(quota.remaining)
    }
}

internal fun formatCurrencyAmount(cents: Long): String {
    val sign = if (cents < 0L) "-" else ""
    val absoluteCents = kotlin.math.abs(cents)
    val dollars = absoluteCents / 100
    val remainder = absoluteCents % 100
    return "${sign}\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

internal fun compactPercentageLabel(quota: QuotaInfo): String {
    return when (quota.unit) {
        UsageUnit.CURRENCY_USD -> formatCents(quota.total)
        UsageUnit.PERCENTAGE -> "${quota.used}%"
        else -> "${(quota.percentageUsed * 100).roundToInt()}%"
    }
}

internal fun formatUsage(quota: QuotaInfo): String {
    return when (quota.unit) {
        UsageUnit.PERCENTAGE -> "${quota.used}%"
        UsageUnit.CURRENCY_USD -> formatCents(quota.total)
        UsageUnit.TOKENS -> {
            val displayUsed = if (quota.rawUsed > 0L) quota.rawUsed else quota.used
            val displayTotal = if (quota.rawTotal > 0L) quota.rawTotal else quota.total
            "${abbreviate(displayUsed)}/${abbreviate(displayTotal)} tok"
        }
        UsageUnit.REQUESTS -> "${abbreviate(quota.used)}/${abbreviate(quota.total)} req"
    }
}

internal fun formatCents(cents: Long): String {
    val dollars = cents / 100
    val remainder = cents % 100
    return "\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

internal fun abbreviate(n: Long): String {
    return when {
        n >= 1_000_000L -> "${removeDecimal("%.1f".format(n / 1_000_000f))}M"
        n >= 1_000L -> "${removeDecimal("%.1f".format(n / 1_000f))}K"
        else -> n.toString()
    }
}

internal fun removeDecimal(value: String): String = value.replace(",0", "").replace(".0", "")
