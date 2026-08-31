package com.usagemonitor.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isExtraCreditsQuota
import com.usagemonitor.domain.entity.seriesKey
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.darkAppAccents

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
    // Vem antes do periodType: a cota de créditos é REPORTED e seria rotulada
    // como "Uso atual", que não diz nada sobre créditos.
    if (quota.isExtraCreditsQuota) {
        return if (language == AppLanguage.PT) "Créditos de uso" else "Usage credits"
    }

    if (quota.unit == UsageUnit.CURRENCY_USD) {
        return quota.label
    }

    return when (quota.periodType) {
        PeriodType.INTERVAL -> if (language == AppLanguage.PT) "Sessão 5h" else "5h session"
        PeriodType.WEEKLY -> if (language == AppLanguage.PT) "Semanal" else "Weekly"
        PeriodType.MONTHLY -> if (language == AppLanguage.PT) "Mensal" else "Monthly"
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

/**
 * Cor de identidade da fonte.
 *
 * [accents] tem default escuro porque este card ainda não migrou para o token por
 * tema — trocar o default mudaria a renderização do dashboard inteiro, que está
 * fora desta rodada. O que a mudança resolve é a duplicação: esta tabela e a do
 * histórico eram duas cópias literais da mesma paleta.
 */
internal fun accentColorFor(
    source: ApiSource,
    accents: AppAccents = darkAppAccents
): Color {
    return when (source) {
        ApiSource.ANTHROPIC -> accents.anthropic
        ApiSource.MINIMAX -> accents.minimax
        ApiSource.CODEX -> accents.codex
        ApiSource.DEEPSEEK -> accents.deepseek
        // Go e Zen Free são planos da mesma integração e dividem o acento: o
        // sistema visual fixa seis identidades de fonte e diz que elas não mudam.
        // O que separa os dois cards é o título, e a regra "cor nunca informa
        // sozinha" já garante que isso basta.
        ApiSource.OPENCODE, ApiSource.OPENCODE_GO -> accents.opencode
        ApiSource.KILO -> accents.kilo
        ApiSource.OPENROUTER -> accents.openrouter
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

internal fun cliSessionsActionLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Sessões CLI desta conta" else "CLI sessions for this account"
}

internal fun teamUsageActionLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Sessões do time nesta conta" else "Team sessions in this account"
}

internal fun teamPresenceActionLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        "Quem está conectado agora"
    } else {
        "Who is connected now"
    }
}

internal fun historyActionLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Abrir histórico" else "Open history"
}

/**
 * Linha de reset da cota.
 *
 * [now] existe para o rótulo saber que a janela já venceu. Sem essa comparação
 * um reset passado era formatado como se fosse futuro e o card repetia o valor
 * saturado da janela anterior como se fosse o atual.
 */
internal fun resetLabel(quota: QuotaInfo, language: AppLanguage, now: Instant): String {
    // Os créditos reiniciam junto com o ciclo mensal de cobrança. A resposta da
    // Anthropic não traz a data desse reinício, então o texto não cita data — e
    // por isso a cota também não passa pelos ramos de janela vencida abaixo.
    if (quota.isExtraCreditsQuota) {
        return if (language == AppLanguage.PT) {
            "Reinicia no início do mês"
        } else {
            "Resets at the start of the month"
        }
    }

    if (quota.unit == UsageUnit.CURRENCY_USD) {
        return if (language == AppLanguage.PT) "Saldo não expira" else "Balance never expires"
    }

    // Vencida: o número na tela ainda é o da janela anterior. Dizer isso é o
    // máximo honesto — o valor novo só chega com a próxima coleta.
    if (quota.isExpiredAt(now)) {
        return if (language == AppLanguage.PT) {
            "Janela reiniciada · coletando dados"
        } else {
            "Window reset · collecting data"
        }
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
            PeriodType.MONTHLY -> "Reinício: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.REPORTED -> "Reinício reportado: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.INTERVAL -> "Reinício: $dayFormatted $timeFormatted BRT"
        }
    } else {
        when (quota.periodType) {
            PeriodType.WEEKLY -> "Reset: $dayFormatted $dateFormatted $timeFormatted BRT"
            PeriodType.MONTHLY -> "Reset: $dayFormatted $dateFormatted $timeFormatted BRT"
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

/**
 * Cor do semáforo de risco.
 *
 * Passou a sair de [AppTone] em vez de três literais. Os antigos `0xFFFFC107` e
 * `0xFFF44336` nunca foram medidos contra as superfícies dos dois temas — o
 * amarelo dava menos de 3:1 sobre a superfície clara —, e o verde já era, por
 * coincidência, o mesmo `cacheRead` da paleta. Um dono só para a semântica de
 * cor, e o `AppAccentsContrastTest` a cobre.
 */
@Composable
@ReadOnlyComposable
internal fun colorFor(level: UsageRiskLevel): Color {
    return toneFor(level).color()
}

/**
 * O pior risco entre as cotas de um card, para o badge do cabeçalho.
 *
 * "Pior" é o maior [UsageRiskLevel.ordinal]: a ordem do enum vai de `ON_TRACK`
 * a `WILL_EXCEED`, e é ela que decide — comparar por percentual daria outra
 * resposta, porque 40% às onze da manhã pode ser pior que 80% dez minutos antes
 * do reinício.
 *
 * Cota **vencida** não entra: a janela dela descreve um período que já não
 * existe, e a projeção sobre ele diria respeito a nada. É a mesma exclusão que
 * `evaluateUsageAlerts` faz antes de notificar.
 *
 * Devolve `null` quando nenhuma cota do card tem projeção conhecida — sem risco
 * calculado não há estado a afirmar, e um badge "Normal" ali seria uma garantia
 * que ninguém deu.
 */
internal fun worstQuotaRisk(
    quotas: List<QuotaInfo>,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    now: Instant
): Pair<QuotaInfo, QuotaRiskSummary>? {
    if (riskByQuotaKey.isEmpty()) {
        return null
    }
    return quotas
        .asSequence()
        .filterNot { quota -> quota.isExpiredAt(now) }
        .mapNotNull { quota -> riskByQuotaKey[quota.seriesKey]?.let { risk -> quota to risk } }
        .maxByOrNull { (_, risk) -> risk.level.ordinal }
}

internal fun toneFor(level: UsageRiskLevel): AppTone {
    return when (level) {
        UsageRiskLevel.ON_TRACK -> AppTone.OK
        UsageRiskLevel.AT_RISK -> AppTone.WARNING
        UsageRiskLevel.WILL_EXCEED -> AppTone.CRITICAL
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

/**
 * A frase que explica o nível de risco.
 *
 * Ela falava em "reset" nos dois ramos, e para um saldo pré-pago isso é uma
 * afirmação falsa: o card imprime "Saldo não expira" na linha de cima e a
 * tooltip prometia um reset logo abaixo (issue #109). Sem reset a frase é sobre
 * quando os créditos acabam, que é a única coisa que o consumo permite prever.
 */
internal fun riskDotTooltipSubtitle(risk: QuotaRiskSummary, language: AppLanguage): String {
    val exhaustionAt = risk.estimatedExhaustionAt

    if (!risk.hasKnownResetAt) {
        // Saldo sem previsão é saldo parado: sem consumo observado não há data a
        // dar, e prometer uma seria inventar.
        if (exhaustionAt == null) {
            return if (language == AppLanguage.PT) {
                "Sem consumo observado no período — não há previsão de término."
            } else {
                "No usage observed in the period — no depletion estimate."
            }
        }
        val formattedInstant = formatBrtDateTime(exhaustionAt, language)
        return if (language == AppLanguage.PT) {
            "No ritmo atual, os créditos devem acabar em $formattedInstant BRT."
        } else {
            "At the current pace, the credits should run out on $formattedInstant BRT."
        }
    }

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

internal fun riskStatusContentDescription(
    quotaLabel: String,
    risk: QuotaRiskSummary,
    language: AppLanguage
): String {
    val levelLabel = riskLevelLabel(risk.level, language)
    val explanation = riskDotTooltipSubtitle(risk, language)
    return if (language == AppLanguage.PT) {
        "Status $levelLabel. Cota $quotaLabel. $explanation"
    } else {
        "Status $levelLabel. Quota $quotaLabel. $explanation"
    }
}

internal fun buildQuotaTooltipMetrics(
    quota: QuotaInfo,
    language: AppLanguage,
    now: Instant,
    risk: QuotaRiskSummary? = null
): List<TooltipMetric> {
    val metrics = mutableListOf<TooltipMetric>()

    if (quota.isExtraCreditsQuota) {
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Créditos usados" else "Credits used",
            value = formatCurrencyAmount(quota.rawUsed, quota.currencyCode)
        )
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Limite mensal" else "Monthly limit",
            value = formatCurrencyAmount(quota.rawTotal, quota.currencyCode)
        )
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Percentual" else "Percentage",
            value = "${quota.used}%"
        )
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Reset" else "Reset",
            value = resetLabel(quota = quota, language = language, now = now)
        )
        metrics.addProjectionMetric(risk = risk, language = language)
        return metrics
    }

    if (quota.unit == UsageUnit.CURRENCY_USD) {
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
            value = formatCurrencyAmount(quota.total, quota.currencyCode)
        )
        metrics += TooltipMetric(
            label = if (language == AppLanguage.PT) "Status" else "Status",
            value = resetLabel(quota = quota, language = language, now = now)
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
        value = resetLabel(quota = quota, language = language, now = now)
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
        UsageUnit.CURRENCY_USD -> formatCurrencyAmount(quota.total, quota.currencyCode)
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
        UsageUnit.CURRENCY_USD -> formatCurrencyAmount(quota.remaining, quota.currencyCode)
    }
}

/**
 * Símbolo da moeda. Códigos sem símbolo conhecido caem no próprio código, para
 * nunca exibir um valor em BRL com cifrão de dólar.
 */
internal fun currencySymbol(currencyCode: String): String {
    return when (currencyCode.uppercase()) {
        "USD" -> "$"
        "BRL" -> "R$"
        "EUR" -> "€"
        "GBP" -> "£"
        else -> "${currencyCode.uppercase()} "
    }
}

// Assume duas casas decimais (`exponent`/`decimal_places` = 2 nas moedas que as
// fontes atuais devolvem). Moeda com outro expoente exigiria propagá-lo.
internal fun formatCurrencyAmount(cents: Long, currencyCode: String = "USD"): String {
    val sign = if (cents < 0L) "-" else ""
    val absoluteCents = kotlin.math.abs(cents)
    val units = absoluteCents / 100
    val remainder = absoluteCents % 100
    return "$sign${currencySymbol(currencyCode)}${units}.${remainder.toString().padStart(2, '0')}"
}

/**
 * O percentual exibido de uma cota.
 *
 * **Truncado, não arredondado**, e é a regra que o resto do app já seguia: o
 * arco fazia `toInt()` e os alertas da bandeja tratam o limiar como piso — 89,9%
 * não cruzou 90%. Só esta função arredondava, então o mesmo card mostrava 26% na
 * cota expandida e 27% no badge minimizado, para o mesmo 12 de 45.
 */
internal fun compactPercentageLabel(quota: QuotaInfo): String {
    return when (quota.unit) {
        UsageUnit.CURRENCY_USD -> formatCents(quota.total, quota.currencyCode)
        UsageUnit.PERCENTAGE -> "${quota.used}%"
        else -> "${(quota.percentageUsed * 100).toInt()}%"
    }
}

internal fun formatUsage(quota: QuotaInfo): String {
    if (quota.isExtraCreditsQuota) {
        return formatCreditsUsage(quota)
    }

    return when (quota.unit) {
        UsageUnit.PERCENTAGE -> "${quota.used}%"
        UsageUnit.CURRENCY_USD -> formatCents(quota.total, quota.currencyCode)
        UsageUnit.TOKENS -> {
            val displayUsed = if (quota.rawUsed > 0L) quota.rawUsed else quota.used
            val displayTotal = if (quota.rawTotal > 0L) quota.rawTotal else quota.total
            "${abbreviate(displayUsed)}/${abbreviate(displayTotal)} tok"
        }
        UsageUnit.REQUESTS -> "${abbreviate(quota.used)}/${abbreviate(quota.total)} req"
    }
}

/** Créditos consumidos sobre o limite mensal, na moeda da conta. */
internal fun formatCreditsUsage(quota: QuotaInfo): String {
    val used = formatCents(quota.rawUsed, quota.currencyCode)
    val total = formatCents(quota.rawTotal, quota.currencyCode)
    return "$used/$total"
}

/**
 * Texto da linha de detalhe da cota, ou nulo quando não há detalhe a exibir.
 *
 * Os créditos ignoram [showUsageDetails]: o interruptor existe para esconder as
 * estimativas de tokens da Anthropic, e os valores de crédito vêm prontos da API.
 */
internal fun quotaDetailText(quota: QuotaInfo, showUsageDetails: Boolean): String? {
    if (quota.isExtraCreditsQuota) {
        return formatCreditsUsage(quota)
    }

    if (!showUsageDetails) {
        return null
    }

    if (quota.unit == UsageUnit.PERCENTAGE || quota.unit == UsageUnit.CURRENCY_USD) {
        return null
    }

    return formatUsage(quota)
}

/**
 * Ordem de exibição das cotas no card.
 *
 * Os créditos entram sempre por último: são leitura acessória e a posição não
 * pode sair do `periodType`, que os colocaria à frente das janelas de uso.
 */
internal fun orderQuotasForCard(quotas: List<QuotaInfo>): List<QuotaInfo> {
    val creditsQuota = quotas.firstOrNull { quota -> quota.isExtraCreditsQuota }
    val windowQuotas = quotas.filterNot { quota -> quota.isExtraCreditsQuota }

    return buildList {
        windowQuotas.firstOrNull { quota -> quota.periodType == PeriodType.REPORTED }?.let(::add)
        windowQuotas.firstOrNull { quota -> quota.periodType == PeriodType.INTERVAL }?.let(::add)
        windowQuotas.firstOrNull { quota -> quota.periodType == PeriodType.WEEKLY }?.let(::add)
        windowQuotas.firstOrNull { quota -> quota.periodType == PeriodType.MONTHLY }?.let(::add)
        windowQuotas.filterNot { quota -> quota in this }.forEach(::add)
        if (creditsQuota != null) {
            add(creditsQuota)
        }
    }
}

internal fun formatCents(cents: Long, currencyCode: String = "USD"): String {
    val units = cents / 100
    val remainder = cents % 100
    return "${currencySymbol(currencyCode)}${units}.${remainder.toString().padStart(2, '0')}"
}

internal fun abbreviate(n: Long): String {
    return when {
        n >= 1_000_000L -> "${removeDecimal("%.1f".format(n / 1_000_000f))}M"
        n >= 1_000L -> "${removeDecimal("%.1f".format(n / 1_000f))}K"
        else -> n.toString()
    }
}

internal fun removeDecimal(value: String): String = value.replace(",0", "").replace(".0", "")
