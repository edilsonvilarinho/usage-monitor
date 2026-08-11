package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.presentation.ui.components.BinMode
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.components.TurnSeries
import com.usagemonitor.presentation.ui.components.TurnSeriesChart
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel

private val INPUT_COLOR = Color(0xFF4C8DFF)
private val OUTPUT_COLOR = Color(0xFFB07CFF)
private val CACHE_READ_COLOR = Color(0xFF4CAF50)
private val CACHE_WRITE_COLOR = Color(0xFFFFA726)
private val SAVINGS_COLOR = Color(0xFF26C6DA)
private val SATURATED_COLOR = Color(0xFFE05252)
private val NEUTRAL_ACCENT = Color(0xFF7C8CA5)

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun CliSessionsScreen(
    viewModel: CliSessionsViewModel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    CliSessionsContent(
        state = state,
        language = language,
        onRefresh = { viewModel.refresh() },
        onSelectRange = { range -> viewModel.setRange(range) },
        onOpenSession = { sessionId -> viewModel.openSession(sessionId) },
        onCloseDetail = { viewModel.closeDetail() },
        modifier = modifier
    )
}

@Composable
internal fun CliSessionsContent(
    state: CliSessionsUiState,
    language: AppLanguage,
    onRefresh: () -> Unit,
    onSelectRange: (CliSessionRange) -> Unit,
    onOpenSession: (String) -> Unit,
    onCloseDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is CliSessionsUiState.Loading -> CenteredMessage(CliSessionsLabels.loading(language))

            is CliSessionsUiState.Error -> CenteredMessage(state.message)

            is CliSessionsUiState.Success -> {
                val detail = state.detail
                if (detail == null) {
                    CliSessionsList(
                        state = state,
                        language = language,
                        onRefresh = onRefresh,
                        onSelectRange = onSelectRange,
                        onOpenSession = onOpenSession
                    )
                } else {
                    CliSessionDetailPane(
                        detail = detail,
                        language = language,
                        onCloseDetail = onCloseDetail
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ----------------------------------------------------------------------------
// Lista
// ----------------------------------------------------------------------------

@Composable
private fun CliSessionsList(
    state: CliSessionsUiState.Success,
    language: AppLanguage,
    onRefresh: () -> Unit,
    onSelectRange: (CliSessionRange) -> Unit,
    onOpenSession: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CliSessionsHeader(
            state = state,
            language = language,
            onRefresh = onRefresh,
            onSelectRange = onSelectRange
        )

        if (state.indexWarning != null) {
            NoticeText(state.indexWarning, MaterialTheme.colorScheme.error)
        }

        if (state.sessions.isEmpty()) {
            CenteredMessage(CliSessionsLabels.emptyInRange(state.range, state.rangeAnchored, language))
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = state.sessions, key = { session -> session.sessionId }) { session ->
                CliSessionRow(
                    session = session,
                    language = language,
                    onOpen = { onOpenSession(session.sessionId) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CliSessionsHeader(
    state: CliSessionsUiState.Success,
    language: AppLanguage,
    onRefresh: () -> Unit,
    onSelectRange: (CliSessionRange) -> Unit
) {
    DepthSurface(
        accent = CACHE_READ_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large,
        elevation = AppElevation.dialog,
        contentPadding = 16.dp
    ) {
        if (state.profileLabel != null) {
            Text(
                text = state.profileLabel,
                style = MaterialTheme.typography.labelMedium,
                color = CACHE_READ_COLOR,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = CliSessionsLabels.sessionCount(state.sessions.size, language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CliSessionsLabels.estimatedCostNotice(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = formatQuantity(state.totalTokens),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CACHE_READ_COLOR
                )
                Text(
                    text = CliSessionsLabels.columnTokens(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = if (state.isTotalCostComplete) {
                        formatMicrosUsdShort(state.totalCostMicros)
                    } else {
                        "${formatMicrosUsdShort(state.totalCostMicros)}+"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = INPUT_COLOR
                )
                Text(
                    text = if (state.range == CliSessionRange.ALL) {
                        CliSessionsLabels.estimatedTotal(language)
                    } else {
                        CliSessionsLabels.estimatedTotalInRange(state.range, state.rangeEndsAt, language)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            for (entry in CliSessionRange.entries) {
                FilterChip(
                    selected = state.range == entry,
                    onClick = { onSelectRange(entry) },
                    label = { Text(CliSessionsLabels.rangeLabel(entry, language)) }
                )
            }
            TextButton(onClick = onRefresh) {
                Text(CliSessionsLabels.refresh(language))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CliSessionRow(
    session: CliSessionSummary,
    language: AppLanguage,
    onOpen: () -> Unit
) {
    DepthSurface(
        accent = CACHE_READ_COLOR,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        glowAlpha = 0.16f,
        contentPadding = 14.dp
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.width(158.dp)) {
                Text(
                    text = shortSessionId(session.sessionId),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatInstant(session.lastTs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(modifier = Modifier.width(140.dp)) {
                Text(
                    text = session.projectName ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = CliSessionsLabels.turnsLabel(session.turnCount, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MetricText(CliSessionsLabels.columnTokens(language), formatQuantity(session.totalTokens))

            Column(modifier = Modifier.width(84.dp)) {
                MetricText(CliSessionsLabels.columnCache(language), formatPercent(session.cacheHitRate))
                Spacer(modifier = Modifier.height(4.dp))
                MeterBar(fraction = session.cacheHitRate, color = CACHE_READ_COLOR, height = 4.dp)
            }

            MetricText(
                label = CliSessionsLabels.columnCost(language),
                value = if (session.isCostComplete) {
                    formatMicrosUsd(session.costMicros)
                } else {
                    "${formatMicrosUsd(session.costMicros)}+"
                },
                valueColor = INPUT_COLOR
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Detalhe
// ----------------------------------------------------------------------------

@Composable
private fun CliSessionDetailPane(
    detail: CliSessionDetailUiState,
    language: AppLanguage,
    onCloseDetail: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCloseDetail) {
                Text(CliSessionsLabels.back(language))
            }
            Text(
                text = shortSessionId(detail.sessionId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        when (detail) {
            is CliSessionDetailUiState.Loading -> CenteredMessage(CliSessionsLabels.loading(language))
            is CliSessionDetailUiState.Error -> CenteredMessage(detail.message)
            is CliSessionDetailUiState.Ready -> CliSessionDetailBody(
                detail = detail.result.detail,
                analytics = detail.result.analytics,
                language = language
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CliSessionDetailBody(
    detail: CliSessionDetail,
    analytics: CliSessionAnalytics,
    language: AppLanguage
) {
    val summary = detail.summary
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SessionHealthBanner(analytics = analytics, language = language)

        SessionMetadataCard(summary = summary, language = language)

        if (summary.stale) {
            NoticeText(CliSessionsLabels.staleNotice(language), MaterialTheme.colorScheme.error)
        }
        if (!analytics.isCostComplete) {
            NoticeText(
                CliSessionsLabels.unpricedNotice(analytics.unpricedTurnCount, language),
                MaterialTheme.colorScheme.error
            )
        }
        if (analytics.sidechainTurnCount > 0) {
            NoticeText(
                CliSessionsLabels.sidechainNotice(analytics.sidechainTurnCount, language),
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(CliSessionsLabels.input(language), formatQuantity(summary.inputTokens), INPUT_COLOR)
            MetricCard(CliSessionsLabels.output(language), formatQuantity(summary.outputTokens), OUTPUT_COLOR)
            MetricCard(CliSessionsLabels.cacheRead(language), formatQuantity(summary.cacheReadTokens), CACHE_READ_COLOR)
            MetricCard(
                CliSessionsLabels.cacheWrite(language),
                formatQuantity(summary.cacheWriteTokens),
                CACHE_WRITE_COLOR
            )
        }

        DetailSection(
            title = CliSessionsLabels.cacheHitRate(language),
            accent = CACHE_READ_COLOR,
            trailing = formatPercent(analytics.cacheHitRate)
        ) {
            MeterBar(fraction = analytics.cacheHitRate, color = CACHE_READ_COLOR)
        }

        DetailSection(
            title = CliSessionsLabels.costDistribution(language),
            accent = INPUT_COLOR,
            trailing = formatMicrosUsd(analytics.costBreakdown.totalMicros)
        ) {
            CostDistributionBar(analytics = analytics)
            Spacer(modifier = Modifier.height(8.dp))
            CostDistributionLegend(analytics = analytics, language = language)
        }

        DetailSection(
            title = CliSessionsLabels.savings(language),
            accent = SAVINGS_COLOR,
            trailing = formatMicrosUsd(analytics.cacheSavingsMicros)
        ) {
            NoticeText(CliSessionsLabels.savingsExplanation(language), MaterialTheme.colorScheme.onSurfaceVariant)
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                CliSessionsLabels.averageContext(language),
                formatQuantity(analytics.averageContextPerTurn),
                CACHE_READ_COLOR
            )
            MetricCard(
                CliSessionsLabels.liveContext(language),
                formatQuantity(analytics.liveContextTokens),
                CACHE_READ_COLOR
            )
            MetricCard(
                CliSessionsLabels.nextInteraction(language),
                formatMicrosUsd(analytics.nextInteractionCostMicros),
                INPUT_COLOR
            )
            MetricCard(
                label = CliSessionsLabels.saturation(language),
                value = analytics.contextSaturation?.let { value -> formatPercent(value) } ?: "—",
                accent = healthColor(analytics.health)
            )
        }

        DetailSection(
            title = CliSessionsLabels.contextPerTurnChart(language),
            accent = CACHE_READ_COLOR
        ) {
            TurnSeriesChart(
                series = listOf(
                    TurnSeries(
                        label = CliSessionsLabels.chartContextLegend(language),
                        values = analytics.contextPerTurn,
                        color = CACHE_READ_COLOR,
                        binMode = BinMode.LAST
                    )
                ),
                valueFormatter = { value -> formatQuantity(value) },
                highlightDrops = true
            )
        }

        DetailSection(
            title = CliSessionsLabels.cacheWritePerTurnChart(language),
            accent = CACHE_WRITE_COLOR
        ) {
            TurnSeriesChart(
                series = listOf(
                    TurnSeries("5m", analytics.cacheWrite5mPerTurn, CACHE_WRITE_COLOR, BinMode.SUM),
                    TurnSeries("1h", analytics.cacheWrite1hPerTurn, OUTPUT_COLOR, BinMode.SUM)
                ),
                stacked = true,
                valueFormatter = { value -> formatQuantity(value) }
            )
        }

        DetailSection(
            title = CliSessionsLabels.costVersusSavingsChart(language),
            accent = SAVINGS_COLOR
        ) {
            TurnSeriesChart(
                series = listOf(
                    TurnSeries(
                        label = CliSessionsLabels.chartCostLegend(language),
                        values = analytics.cumulativeCostMicros,
                        color = INPUT_COLOR,
                        binMode = BinMode.MAX
                    ),
                    TurnSeries(
                        label = CliSessionsLabels.chartSavingsLegend(language),
                        values = analytics.cumulativeSavingsMicros,
                        color = SAVINGS_COLOR,
                        binMode = BinMode.MAX
                    )
                ),
                valueFormatter = { value -> formatMicrosUsdShort(value) }
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Peças reutilizadas
// ----------------------------------------------------------------------------

/**
 * Recomendação sobre continuar ou recomeçar a sessão.
 *
 * O legado mostrava só um aviso binário de saturação e ele acendia em quase
 * metade das sessões. Aqui o status é graduado e sempre diz *por quê*: o alerta
 * sem o número que o gerou não dá para conferir nem para confiar.
 */
@Composable
private fun SessionHealthBanner(analytics: CliSessionAnalytics, language: AppLanguage) {
    val health = analytics.health
    val accent = healthColor(health)

    DepthSurface(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        glowAlpha = if (health == CliSessionHealth.HEALTHY) 0.14f else 0.26f,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(AppShapes.small)
                    .background(accent)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CliSessionsLabels.healthTitle(health, language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Text(
                        text = CliSessionsLabels.healthReason(
                            saturationLabel = analytics.contextSaturation?.let { value -> formatPercent(value) },
                            nextCostLabel = formatMicrosUsd(analytics.nextInteractionCostMicros),
                            language = language
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CliSessionsLabels.healthAdvice(health, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Onde a sessão rodou. A máquina não vem do transcript — o Claude Code não a
 * registra — e sim de quem indexou o arquivo, que é a mesma máquina no uso normal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionMetadataCard(summary: CliSessionSummary, language: AppLanguage) {
    DepthSurface(
        accent = NEUTRAL_ACCENT,
        modifier = Modifier.fillMaxWidth(),
        glowAlpha = 0.10f,
        contentPadding = 14.dp
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricText(CliSessionsLabels.machine(language), summary.hostName ?: "—")
            MetricText(CliSessionsLabels.projectPath(language), summary.cwd ?: "—")
            MetricText(CliSessionsLabels.branch(language), summary.gitBranch ?: "—")
            MetricText(
                label = CliSessionsLabels.period(language),
                value = "${formatInstant(summary.firstTs)} → ${formatInstant(summary.lastTs)}"
            )
        }
    }
}

private fun healthColor(health: CliSessionHealth): Color {
    return when (health) {
        CliSessionHealth.HEALTHY -> CACHE_READ_COLOR
        CliSessionHealth.ATTENTION -> CACHE_WRITE_COLOR
        CliSessionHealth.SATURATED -> SATURATED_COLOR
    }
}

@Composable
private fun DetailSection(
    title: String,
    accent: Color,
    trailing: String? = null,
    content: @Composable () -> Unit
) {
    DepthSurface(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .clip(AppShapes.small)
                        .background(accent)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    footer: String? = null
) {
    DepthSurface(
        accent = accent,
        modifier = Modifier.width(168.dp),
        contentPadding = 12.dp
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (footer != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

@Composable
private fun MetricText(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun NoticeText(message: String, color: Color) {
    Text(text = message, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun MeterBar(
    fraction: Double,
    color: Color,
    height: androidx.compose.ui.unit.Dp = 10.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.7f), color)
                    )
                )
        )
    }
}

@Composable
private fun CostDistributionBar(analytics: CliSessionAnalytics) {
    val breakdown = analytics.costBreakdown

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        for ((value, color) in costSegments(analytics)) {
            val weight = breakdown.fractionOf(value).toFloat()
            if (weight <= 0f) {
                continue
            }
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.72f))))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostDistributionLegend(analytics: CliSessionAnalytics, language: AppLanguage) {
    val breakdown = analytics.costBreakdown
    val labels = listOf(
        CliSessionsLabels.input(language),
        CliSessionsLabels.output(language),
        CliSessionsLabels.cacheRead(language),
        CliSessionsLabels.cacheWrite(language)
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        costSegments(analytics).forEachIndexed { index, (value, color) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(AppShapes.small).background(color))
                Text(
                    text = "${labels[index]} ${formatPercent(breakdown.fractionOf(value))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun costSegments(analytics: CliSessionAnalytics): List<Pair<Long, Color>> {
    val breakdown = analytics.costBreakdown
    return listOf(
        breakdown.inputMicros to INPUT_COLOR,
        breakdown.outputMicros to OUTPUT_COLOR,
        breakdown.cacheReadMicros to CACHE_READ_COLOR,
        breakdown.cacheWriteMicros to CACHE_WRITE_COLOR
    )
}
