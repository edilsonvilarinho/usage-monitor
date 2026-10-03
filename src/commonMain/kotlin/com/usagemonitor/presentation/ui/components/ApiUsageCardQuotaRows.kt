package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isExtraCreditsQuota
import com.usagemonitor.domain.entity.seriesKey
import kotlin.time.Instant

/**
 * As cotas do card expandido, uma por linha.
 *
 * Eram colunas com um arco de 92dp cada. O arco ocupava a maior parte da altura
 * do card para dizer um número que a linha diz em 12sp, e três deles lado a lado
 * — a Anthropic tem três cotas desde os créditos de uso — obrigavam uma regra de
 * empilhamento própria, com largura mínima por coluna. Empilhado sempre, essa
 * regra deixa de existir: a linha ocupa a largura que o card tiver.
 */
@Composable
internal fun ExpandedQuotaSummary(
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    /** Ver `shouldShowQuotaTooltip`: em card estreito o popup cobre o card. */
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    // Linha de dados, com divisória própria e sem vão entre elas: é a mesma
    // decisão da lista do time. O vão fazia três cotas lerem como três blocos
    // empilhados, e sem a divisória o rótulo de uma encostava no reinício da
    // anterior sem nada dizendo onde uma termina.
    Column(modifier = modifier.fillMaxWidth()) {
        quotas.forEachIndexed { index, quota ->
            AppDataRow(
                showDivider = index != quotas.lastIndex,
                horizontalPadding = density.contentHorizontalPadding,
                verticalPadding = density.contentVerticalPadding
            ) {
                QuotaRow(
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    showTooltip = showTooltip,
                    now = now,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Uma cota: rótulo e valor na mesma linha, barra abaixo, reinício embaixo.
 *
 * A barra é a leitura de relance que o arco fazia, na altura de 4dp em vez de
 * 92. Cota em moeda **não** ganha barra: o saldo da DeepSeek não tem um total
 * contra o qual medir, e uma barra ali desenharia uma fração inventada. Os
 * créditos de uso ganham, porque têm limite mensal declarado.
 */
@Composable
private fun QuotaRow(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    /** Ver `shouldShowQuotaTooltip`: em card estreito o popup cobre o card. */
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    // Sem tooltip a `testTag` do bloco desce para o conteúdo: presa ao
    // `HoverTooltipBox`, o nó sumiria da árvore em card estreito.
    if (!showTooltip) {
        QuotaRowContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            now = now,
            // Sem popup para explicar o semáforo, a explicação vira texto —
            // ver `CompactQuotaBadgeContent.showRiskSummaryLine`, mesma saída.
            showRiskSummaryLine = true,
            modifier = modifier.testTag(quotaBlockTag(quota.label))
        )

        return
    }

    HoverTooltipBox(
        title = quota.label,
        subtitle = expandedQuotaTitle(quota = quota, language = language),
        metrics = buildQuotaTooltipMetrics(quota = quota, language = language, now = now, risk = risk),
        footnote = risk?.let { riskDotTooltipSubtitle(risk = it, language = language) },
        modifier = modifier.testTag(quotaBlockTag(quota.label))
    ) {
        QuotaRowContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            now = now,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun QuotaRowContent(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    now: Instant,
    showRiskSummaryLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isExpired = quota.isExpiredAt(now)
    val staleAlpha = if (isExpired) STALE_QUOTA_ALPHA else 1f
    val hasTrack = quota.unit != UsageUnit.CURRENCY_USD || quota.isExtraCreditsQuota

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (risk != null) {
                RiskSemaphoreDot(
                    risk = risk,
                    quotaLabel = quota.label,
                    language = language,
                    showTooltip = false
                )
            }
            Text(
                text = expandedQuotaTitle(quota = quota, language = language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AppAnimatedNumber(
                text = compactPercentageLabel(quota),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                // Mesmo tratamento de antes: janela vencida mostra o último
                // dado real da fonte, esmaecido para não passar por corrente.
                modifier = Modifier.alpha(staleAlpha),
                contentAlignment = Alignment.CenterEnd
            )
        }

        if (hasTrack) {
            // Marca de ritmo (issue #327): onde o uso estaria em ritmo constante.
            val elapsedLabel = elapsedWindowLabel(quota = quota, language = language, now = now)
            AppProgressTrack(
                fraction = quota.percentageUsed,
                tone = quotaTone(quota = quota, risk = risk),
                marker = quota.elapsedFractionAt(now),
                modifier = Modifier
                    .testTag(quotaProgressTrackTag(quota.label))
                    .semantics { if (elapsedLabel != null) stateDescription = elapsedLabel }
                    .alpha(staleAlpha)
            )
        }

        val detailText = quotaDetailText(quota = quota, showUsageDetails = showUsageDetails)
        if (detailText != null) {
            Text(
                text = detailText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = resetLabel(quota = quota, language = language, now = now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )

        if (showRiskSummaryLine && risk != null) {
            Text(
                text = riskDotTooltipSubtitle(risk = risk, language = language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Severidade da barra.
 *
 * O risco projetado tem prioridade sobre o percentual porque responde à pergunta
 * certa: 40% às onze da manhã pode ser pior que 80% faltando dez minutos para o
 * reinício. Sem projeção conhecida, sobra o percentual, com os mesmos cortes de
 * 75 e 90 que os alertas da bandeja usam.
 */
private fun quotaTone(quota: QuotaInfo, risk: QuotaRiskSummary?): AppTone {
    if (risk != null) {
        return toneFor(risk.level)
    }
    val percent = quota.percentageUsed * 100f
    return when {
        percent >= 90f -> AppTone.CRITICAL
        percent >= 75f -> AppTone.WARNING
        else -> AppTone.OK
    }
}
