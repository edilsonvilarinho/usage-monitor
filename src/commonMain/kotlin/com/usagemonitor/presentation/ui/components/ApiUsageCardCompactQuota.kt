package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.seriesKey
import kotlin.time.Instant

private const val COMPACT_QUOTA_BADGE_TAG = "compactQuotaBadge"

@Composable
internal fun CompactQuotaSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    stacked: Boolean,
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    if (quotas.size == 1) {
        BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            val badgeWidthFraction = if (maxWidth < 360.dp) 0.76f else 0.5f

            CompactQuotaBadge(
                source = source,
                quota = quotas.first(),
                showUsageDetails = showUsageDetails,
                language = language,
                risk = riskByQuotaKey[quotas.first().seriesKey],
                density = density,
                showTooltip = showTooltip,
                now = now,
                modifier = Modifier.fillMaxWidth(badgeWidthFraction)
            )
        }

        return
    }

    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(density.compactQuotaSpacing)
        ) {
            quotas.forEach { quota ->
                CompactQuotaBadge(
                    source = source,
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    density = density,
                    showTooltip = showTooltip,
                    now = now,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(density.compactQuotaSpacing),
        verticalAlignment = Alignment.Top
    ) {
        quotas.forEach { quota ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                CompactQuotaBadge(
                    source = source,
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    density = density,
                    showTooltip = showTooltip,
                    now = now,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactQuotaBadge(
    source: ApiSource,
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    density: ApiUsageCardDensity,
    /** Ver `shouldShowQuotaTooltip`: em card estreito o popup cobre o card. */
    showTooltip: Boolean,
    now: Instant,
    modifier: Modifier = Modifier
) {
    // Sem tooltip a `testTag` do bloco desce para o conteúdo: presa ao
    // `HoverTooltipBox`, o nó sumiria da árvore em card estreito.
    if (!showTooltip) {
        CompactQuotaBadgeContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            density = density,
            now = now,
            // Sem popup para explicar o semáforo, a explicação vira texto: ver
            // `CompactQuotaBadgeContent.showRiskSummaryLine`.
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
        CompactQuotaBadgeContent(
            quota = quota,
            showUsageDetails = showUsageDetails,
            language = language,
            risk = risk,
            density = density,
            now = now,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun CompactQuotaBadgeContent(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    density: ApiUsageCardDensity,
    now: Instant,
    /**
     * Card estreito (issue #215): sem tooltip para explicar o semáforo — o
     * popup cobriria o card inteiro, `shouldShowQuotaTooltip` —, a mesma
     * frase de `riskDotTooltipSubtitle` vira uma linha de texto sempre
     * visível, truncada com reticências em vez de omitida. Mesma saída que
     * já resolveu o problema análogo na barra HUD: texto no fluxo, não popup.
     */
    showRiskSummaryLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isExpired = quota.isExpiredAt(now)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(COMPACT_QUOTA_BADGE_TAG)
            // Fundo neutro e borda: o tom de acento em bloco fazia o card
            // fechado — que existe para ocupar pouco — chamar mais atenção
            // que o aberto.
            .appSurfaceBlock()
            .padding(
                horizontal = density.badgeHorizontalPadding,
                vertical = density.badgeVerticalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (risk != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A tooltip do ponto fica desligada: o badge inteiro já tem a
                // própria tooltip e dois TooltipBox aninhados disputam o hover.
                RiskSemaphoreDot(
                    risk = risk,
                    quotaLabel = quota.label,
                    language = language,
                    showTooltip = false
                )
                Text(
                    text = quota.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = quota.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        AppAnimatedNumber(
            text = compactPercentageLabel(quota),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            // Mesmo tratamento do arco: o numero e o da janela anterior.
            modifier = Modifier.alpha(if (isExpired) STALE_QUOTA_ALPHA else 1f)
        )

        val detailText = quotaDetailText(quota = quota, showUsageDetails = showUsageDetails)
        if (detailText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = detailText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        if (showRiskSummaryLine && risk != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = riskDotTooltipSubtitle(risk = risk, language = language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
