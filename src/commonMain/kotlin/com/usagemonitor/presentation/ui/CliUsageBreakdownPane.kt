package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.presentation.ui.components.ActivityHeatmapGrid
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes

const val BREAKDOWN_SCROLLBAR_TAG = "breakdownScrollbar"
const val BREAKDOWN_PANE_TAG = "breakdownPane"

/** Altura da barra de fatia; fina o bastante para não competir com o número. */
private val SHARE_BAR_HEIGHT = 4.dp

/**
 * Resumo do consumo da janela por projeto, branch e modelo.
 *
 * Stateless: recebe o resumo já calculado. As três seções descrevem os **mesmos**
 * turnos por eixos diferentes — a tela diz isso em texto, porque somar linhas de
 * seções diferentes contaria o mesmo gasto duas vezes.
 */
@Composable
internal fun CliUsageBreakdownPane(
    breakdown: CliUsageBreakdown?,
    errorMessage: String?,
    language: AppLanguage,
    budget: MonthlyBudgetStatus? = null,
    accountCredits: AccountCreditUsage? = null,
    modifier: Modifier = Modifier
) {
    if (breakdown == null) {
        CenteredMessage(errorMessage ?: CliSessionsLabels.loading(language))
        return
    }

    if (breakdown.isEmpty) {
        CenteredMessage(BreakdownLabels.empty(language))
        return
    }

    Box(modifier = modifier.fillMaxSize().testTag(BREAKDOWN_PANE_TAG)) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BreakdownTotalsCard(breakdown = breakdown, language = language)
            }

            item {
                BurnRateCard(breakdown = breakdown, language = language)
            }

            if (budget != null || accountCredits != null) {
                item(key = "budget") {
                    BudgetCard(budget = budget, accountCredits = accountCredits, language = language)
                }
            }

            if (errorMessage != null) {
                item {
                    // O resumo anterior continua na tela: a mensagem diz que a
                    // última leitura falhou, não que não há dados.
                    NoticeText(BreakdownLabels.staleNotice(errorMessage, language), MaterialTheme.colorScheme.error)
                }
            }

            breakdownSection(
                title = BreakdownLabels.byProject(language),
                buckets = breakdown.byProject,
                totals = breakdown.totals,
                unknownLabel = BreakdownLabels.unknownProject(language),
                accent = CACHE_READ_COLOR,
                language = language
            )
            breakdownSection(
                title = BreakdownLabels.byModel(language),
                buckets = breakdown.byModel,
                totals = breakdown.totals,
                unknownLabel = BreakdownLabels.unknownModel(language),
                accent = INPUT_COLOR,
                language = language
            )
            breakdownSection(
                title = BreakdownLabels.byBranch(language),
                buckets = breakdown.byBranch,
                totals = breakdown.totals,
                unknownLabel = BreakdownLabels.unknownBranch(language),
                accent = OUTPUT_COLOR,
                language = language
            )

            if (!breakdown.heatmap.isEmpty) {
                item(key = "activity") {
                    ActivityCard(breakdown = breakdown, language = language)
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(BREAKDOWN_SCROLLBAR_TAG)
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.breakdownSection(
    title: String,
    buckets: List<CliUsageBucket>,
    totals: CliUsageBucket,
    unknownLabel: String,
    accent: Color,
    language: AppLanguage
) {
    if (buckets.isEmpty()) {
        return
    }

    item(key = "header-$title") {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }

    items(
        count = buckets.size,
        key = { index -> "$title-${buckets[index].label ?: "?"}" }
    ) { index ->
        BreakdownRow(
            bucket = buckets[index],
            totals = totals,
            unknownLabel = unknownLabel,
            accent = accent,
            language = language
        )
    }
}

@Composable
private fun BreakdownTotalsCard(breakdown: CliUsageBreakdown, language: AppLanguage) {
    val totals = breakdown.totals

    DepthSurface(
        accent = CACHE_READ_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large,
        elevation = AppElevation.dialog,
        contentPadding = 16.dp
    ) {
        Text(
            text = BreakdownLabels.totalCost(totals, language),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = INPUT_COLOR
        )
        Text(
            text = BreakdownLabels.totalSubtitle(totals, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = BreakdownLabels.cacheSavings(
                savingsMicros = totals.cacheSavingsMicros,
                share = breakdown.cacheSavingsShare,
                hitRate = totals.cacheHitRate,
                language = language
            ),
            style = MaterialTheme.typography.labelSmall,
            color = CACHE_READ_COLOR
        )
        if (!totals.isCostComplete) {
            Text(
                text = BreakdownLabels.unpricedNotice(totals.unpricedTurnCount, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = BreakdownLabels.axisNotice(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BudgetCard(
    budget: MonthlyBudgetStatus?,
    accountCredits: AccountCreditUsage?,
    language: AppLanguage
) {
    DepthSurface(
        accent = INPUT_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        elevation = AppElevation.card,
        contentPadding = 12.dp
    ) {
        Text(
            text = BreakdownLabels.budgetTitle(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = INPUT_COLOR
        )

        if (budget != null) {
            Text(
                text = BreakdownLabels.budgetValue(
                    spentMicros = budget.spentMicros,
                    limitMicros = budget.limitMicros,
                    isComplete = budget.isSpendComplete,
                    language = language
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (budget.isExceeded) MaterialTheme.colorScheme.error else INPUT_COLOR
            )
            ShareBar(
                share = budget.share,
                accent = if (budget.isExceeded) MaterialTheme.colorScheme.error else INPUT_COLOR
            )
            Text(
                text = BreakdownLabels.budgetProjection(budget.projectedMicros, budget.willExceed, language),
                style = MaterialTheme.typography.labelSmall,
                color = if (budget.willExceed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = BreakdownLabels.budgetScopeNotice(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (accountCredits != null) {
            // Linha própria e moeda explícita: somar isto ao valor acima daria um
            // número inventado quando a conta não é em USD.
            Text(
                text = BreakdownLabels.accountCredits(
                    usedMinorUnits = accountCredits.usedMinorUnits,
                    limitMinorUnits = accountCredits.limitMinorUnits,
                    currencyCode = accountCredits.currencyCode,
                    language = language
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CACHE_READ_COLOR
            )
        }
    }
}

@Composable
private fun BurnRateCard(breakdown: CliUsageBreakdown, language: AppLanguage) {
    val burnRate = breakdown.burnRate

    DepthSurface(
        accent = OUTPUT_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        elevation = AppElevation.card,
        contentPadding = 12.dp
    ) {
        Text(
            text = BreakdownLabels.burnRateTitle(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = OUTPUT_COLOR
        )

        if (burnRate == null) {
            Text(
                text = BreakdownLabels.burnRateUnavailable(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DepthSurface
        }

        Text(
            text = BreakdownLabels.burnRateValue(
                costMicrosPerHour = burnRate.costMicrosPerHour,
                tokensPerHour = burnRate.tokensPerHour,
                language = language
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (burnRate.projectedCostMicros != null) {
            Text(
                text = BreakdownLabels.burnRateProjection(burnRate.projectedCostMicros, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = BreakdownLabels.burnRateElapsed(burnRate.elapsedMillis, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityCard(breakdown: CliUsageBreakdown, language: AppLanguage) {
    DepthSurface(
        accent = CACHE_READ_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        elevation = AppElevation.card,
        contentPadding = 12.dp
    ) {
        Text(
            text = BreakdownLabels.activityTitle(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = CACHE_READ_COLOR
        )
        ActivityHeatmapGrid(
            heatmap = breakdown.heatmap,
            accent = CACHE_READ_COLOR,
            language = language,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = BreakdownLabels.activityNotice(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BreakdownRow(
    bucket: CliUsageBucket,
    totals: CliUsageBucket,
    unknownLabel: String,
    accent: Color,
    language: AppLanguage
) {
    val share = bucket.costShareOf(totals)

    DepthSurface(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        elevation = AppElevation.card,
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = bucket.label ?: unknownLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = BreakdownLabels.bucketCost(bucket),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = INPUT_COLOR
            )
            Text(
                text = formatPercent(share),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ShareBar(share = share, accent = accent)

        Text(
            text = BreakdownLabels.bucketSubtitle(bucket, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Barra de fatia desenhada com dois `Box`, sem animação.
 *
 * Uma animação numa lista que o laço ao vivo republica de cinco em cinco
 * segundos travaria o `waitForIdle` dos testes de componente — mesma razão pela
 * qual o ponto da tela de presença não pisca.
 */
@Composable
private fun ShareBar(share: Double, accent: Color) {
    val fraction = share.coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SHARE_BAR_HEIGHT)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(AppShapes.small)
                    .background(accent)
            )
        }
    }
}
