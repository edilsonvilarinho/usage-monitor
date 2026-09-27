package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppExpandable
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.BinMode
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.components.TurnSeries
import com.usagemonitor.presentation.ui.components.TurnSeriesChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState

/** Mais baixo que o default de `TurnSeriesChart`: são vários numa página só. */
private val DETAIL_CHART_HEIGHT = 120.dp

// ----------------------------------------------------------------------------
// Detalhe
// ----------------------------------------------------------------------------

@Composable
internal fun CliSessionDetailPane(
    detail: CliSessionDetailUiState,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onCloseDetail: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppButton(
                label = CliSessionsLabels.back(language),
                onClick = onCloseDetail,
                tone = AppButtonTone.GHOST
            )
            Text(
                text = shortSessionId(detail.sessionId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            CopySessionCommandButton(
                sessionId = detail.sessionId,
                language = language,
                showLabel = true
            )
        }

        when (detail) {
            is CliSessionDetailUiState.Loading -> AppLoadingState(CliSessionsLabels.loading(language))
            is CliSessionDetailUiState.Error -> AppErrorState(detail.message)
            is CliSessionDetailUiState.Ready -> CliSessionDetailBody(
                detail = detail.result.detail,
                analytics = detail.result.analytics,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary
            )
        }
    }
}

@Composable
private fun CliSessionDetailBody(
    detail: CliSessionDetail,
    analytics: CliSessionAnalytics,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Mesma razão da lista: a barra flutua sobre o conteúdo.
                .padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CliSessionDetailSections(
                detail = detail,
                analytics = analytics,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(DETAIL_SCROLLBAR_TAG)
        )
    }
}

/**
 * As seções do detalhe, emitidas direto no `Column` rolável do chamador.
 *
 * Divididas em duas camadas. A de cima responde à pergunta que traz o usuário
 * aqui — *dá para continuar nesta sessão?* — com o veredito, a identificação e
 * quatro números. A de baixo, recolhida, guarda a apuração: a composição dos
 * tokens, a distribuição do custo e os gráficos por turno.
 *
 * Nada foi removido na divisão; a camada de baixo é a mesma de antes.
 *
 * `internal` porque o modal de time monta o mesmo painel para a sessão de um
 * colega: dois detalhes com a mesma anatomia não podem ter duas implementações.
 */
@Composable
internal fun CliSessionDetailSections(
    detail: CliSessionDetail,
    analytics: CliSessionAnalytics,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit,
    /**
     * Aviso de que os turnos não vieram — servidor de time anterior à rota de
     * detalhe. Preenchido, as seções que dependem de turno **não** são compostas:
     * um gráfico vazio se leria como sessão sem atividade, e a distribuição de
     * custo estimada a partir do modelo predominante seria número inventado.
     */
    missingTurnsNotice: String? = null
) {
    val summary = detail.summary

    SessionHealthBanner(analytics = analytics, language = language)

    SessionMetadataCard(summary = summary, language = language)

    if (missingTurnsNotice != null) {
        NoticeText(missingTurnsNotice, MaterialTheme.colorScheme.error)
    }

    // Integridade do dado não é detalhe avançado: se o custo está incompleto,
    // todo número desta tela está incompleto.
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

    SessionSummaryRow(summary = summary, analytics = analytics, language = language)

    if (missingTurnsNotice == null) {
        val accents = AppAccents.current
        DetailSection(
            title = CliSessionsLabels.contextPerTurnChart(language),
            accent = accents.cacheRead,
            // Duas dúvidas de uma vez: o que a curva mede e o que o ▼ marca.
            help = listOf(GlossaryTerm.CONTEXT_PER_TURN, GlossaryTerm.COMPACTION),
            language = language
        ) {
            TurnSeriesChart(
                series = listOf(
                    TurnSeries(
                        label = CliSessionsLabels.chartContextLegend(language),
                        values = analytics.contextPerTurn,
                        color = accents.cacheRead,
                        binMode = BinMode.LAST
                    )
                ),
                height = DETAIL_CHART_HEIGHT,
                valueFormatter = { value -> formatQuantity(value) },
                highlightDrops = true
            )
        }

        AdvancedDisclosure(
            expanded = advancedExpanded,
            language = language,
            onToggle = onToggleAdvanced
        ) {
            SessionAdvancedSections(
                summary = summary,
                analytics = analytics,
                language = language
            )
        }
    }

    GlossaryPanel(
        expanded = glossaryExpanded,
        language = language,
        onToggle = onToggleGlossary
    )
}

/**
 * Os quatro números que decidem se vale continuar: quanto já custou, quanto
 * volume passou, quanto disso o cache absorveu e quanto da janela do modelo já
 * foi ocupada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionSummaryRow(
    summary: CliSessionSummary,
    analytics: CliSessionAnalytics,
    language: AppLanguage
) {
    val accents = AppAccents.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // O custo vem do resumo, e não da apuração por turno: é o mesmo número
        // que a linha da lista mostra para esta sessão, e existe mesmo quando os
        // turnos não estão disponíveis. A distribuição por componente, que só o
        // turno prova, continua saindo do `costBreakdown`, no bloco Avançado.
        MetricCard(
            label = CliSessionsLabels.columnCost(language),
            value = formatMicrosUsd(summary.costMicros),
            accent = accents.input,
            help = GlossaryTerm.ESTIMATED_COST,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.columnTokens(language),
            value = formatQuantity(summary.totalTokens),
            accent = accents.cacheRead,
            help = GlossaryTerm.TOTAL_TOKENS,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.cacheHitRate(language),
            value = formatPercent(analytics.cacheHitRate),
            accent = accents.cacheRead,
            help = GlossaryTerm.CACHE_HIT_RATE,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.saturation(language),
            value = analytics.contextSaturation?.let { value -> formatPercent(value) } ?: "—",
            accent = healthColor(analytics.health, accents),
            help = GlossaryTerm.CONTEXT_WINDOW,
            language = language
        )
        // Só aparece quando há intervalo para medir: numa sessão de um turno
        // "0min" seria lido como sessão instantânea, e não como não medida.
        if (analytics.activeTimeMillis > 0L) {
            MetricCard(
                label = CliSessionsLabels.activeTime(language),
                value = formatActiveTime(analytics.activeTimeMillis),
                accent = accents.output,
                language = language
            )
        }
    }
}

/** Tudo o que estava na tela antes da divisão e que não cabe no resumo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionAdvancedSections(
    summary: CliSessionSummary,
    analytics: CliSessionAnalytics,
    language: AppLanguage
) {
    val accents = AppAccents.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            label = CliSessionsLabels.input(language),
            value = formatQuantity(summary.inputTokens),
            accent = accents.input,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.output(language),
            value = formatQuantity(summary.outputTokens),
            accent = accents.output,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.cacheRead(language),
            value = formatQuantity(summary.cacheReadTokens),
            accent = accents.cacheRead,
            help = GlossaryTerm.CACHE_READ,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.cacheWrite(language),
            value = formatQuantity(summary.cacheWriteTokens),
            accent = accents.cacheWrite,
            help = GlossaryTerm.CACHE_WRITE,
            language = language
        )
    }

    DetailSection(
        title = CliSessionsLabels.cacheHitRate(language),
        accent = accents.cacheRead,
        trailing = formatPercent(analytics.cacheHitRate),
        help = listOf(GlossaryTerm.CACHE_HIT_RATE),
        language = language
    ) {
        AppProgressTrack(fraction = analytics.cacheHitRate.toFloat(), tone = AppTone.OK)
    }

    DetailSection(
        title = CliSessionsLabels.costDistribution(language),
        accent = accents.input,
        trailing = formatMicrosUsd(analytics.costBreakdown.totalMicros),
        help = listOf(GlossaryTerm.COST_DISTRIBUTION),
        language = language
    ) {
        CostDistributionBar(analytics = analytics)
        Spacer(modifier = Modifier.height(8.dp))
        CostDistributionLegend(analytics = analytics, language = language)
    }

    DetailSection(
        title = CliSessionsLabels.savings(language),
        accent = accents.savings,
        trailing = formatMicrosUsd(analytics.cacheSavingsMicros),
        help = listOf(GlossaryTerm.SAVINGS),
        language = language
    ) {
        NoticeText(CliSessionsLabels.savingsExplanation(language), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            label = CliSessionsLabels.averageContext(language),
            value = formatQuantity(analytics.averageContextPerTurn),
            accent = accents.cacheRead,
            help = GlossaryTerm.AVERAGE_CONTEXT,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.liveContext(language),
            value = formatQuantity(analytics.liveContextTokens),
            accent = accents.cacheRead,
            help = GlossaryTerm.LIVE_CONTEXT,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.nextInteraction(language),
            value = formatMicrosUsd(analytics.nextInteractionCostMicros),
            accent = accents.input,
            help = GlossaryTerm.NEXT_INTERACTION,
            language = language
        )
    }

    DetailSection(
        title = CliSessionsLabels.cacheWritePerTurnChart(language),
        accent = accents.cacheWrite,
        help = listOf(GlossaryTerm.CACHE_WRITE_PER_TURN),
        language = language
    ) {
        TurnSeriesChart(
            series = listOf(
                TurnSeries("5m", analytics.cacheWrite5mPerTurn, accents.cacheWrite, BinMode.SUM),
                TurnSeries("1h", analytics.cacheWrite1hPerTurn, accents.output, BinMode.SUM)
            ),
            stacked = true,
            height = DETAIL_CHART_HEIGHT,
            valueFormatter = { value -> formatQuantity(value) }
        )
    }

    DetailSection(
        title = CliSessionsLabels.costVersusSavingsChart(language),
        accent = accents.savings,
        help = listOf(GlossaryTerm.COST_VERSUS_SAVINGS),
        language = language
    ) {
        TurnSeriesChart(
            series = listOf(
                TurnSeries(
                    label = CliSessionsLabels.chartCostLegend(language),
                    values = analytics.cumulativeCostMicros,
                    color = accents.input,
                    binMode = BinMode.MAX
                ),
                TurnSeries(
                    label = CliSessionsLabels.chartSavingsLegend(language),
                    values = analytics.cumulativeSavingsMicros,
                    color = accents.savings,
                    binMode = BinMode.MAX
                )
            ),
            height = DETAIL_CHART_HEIGHT,
            valueFormatter = { value -> formatMicrosUsdShort(value) }
        )
    }
}

/**
 * Cabeçalho clicável que revela [content].
 *
 * O conteúdo não é composto enquanto está fechado — são dois gráficos e sete
 * cards que não têm por que existir na árvore só para ficarem invisíveis.
 */
@Composable
internal fun AdvancedDisclosure(
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppDataSurface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            contentPadding = 14.dp,
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CliSessionsLabels.advancedToggle(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CliSessionsLabels.advancedHint(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AppExpandable(expanded) {
            content()
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
internal fun SessionHealthBanner(analytics: CliSessionAnalytics, language: AppLanguage) {
    val health = analytics.health

    // Vira o aviso do sistema: barra de severidade de 2dp, título e descrição.
    // O veredito é o título, e o número que o gerou entra na descrição junto com
    // o conselho — antes eram três textos concorrendo na mesma linha.
    AppBanner(
        title = CliSessionsLabels.healthTitle(health, language),
        tone = healthTone(health),
        description = CliSessionsLabels.healthReason(
            saturationLabel = analytics.contextSaturation?.let { value -> formatPercent(value) },
            nextCostLabel = formatMicrosUsd(analytics.nextInteractionCostMicros),
            language = language
        ),
        detail = CliSessionsLabels.healthAdvice(health, language),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionMetadataCard(summary: CliSessionSummary, language: AppLanguage) {
    AppDataSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
        verticalArrangement = Arrangement.Top
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
