package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageView

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamUsageHeader(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onSelectView: (TeamUsageView) -> Unit,
    onExportReport: () -> Unit
) {
    // Sem painel em volta: o corpo da janela já é a superfície, e um retângulo com
    // borda envolvendo barra de controles, métricas e abas transformava o
    // cabeçalho inteiro num bloco só — que é o que o protótipo desenha solto.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        // Abas **antes** das métricas, como no protótipo: a aba escolhe o que a
        // janela mostra, e os totais são conteúdo dela.
        //
        // `Row` e não `FlowRow`: as abas levam `weight` para empurrar o resto para
        // a direita, e peso dentro de um `FlowRow` fica sem referência de largura.
        AppToolbar(spacing = AppSpacing.sm) {
            val view = state.effectiveView
            // Na visão global a série nunca é carregada — uma linha por conta não
            // caberia num gráfico só — e uma aba que nunca mostra nada é pior que
            // aba nenhuma.
            val tabs = buildList {
                add(AppTab(label = TeamUsageLabels.tabMembers(language), testTag = TEAM_TAB_MEMBERS_TAG))
                add(AppTab(label = BreakdownLabels.tabBreakdown(language), testTag = TEAM_TAB_BREAKDOWN_TAG))
                if (state.isTrendAvailable) {
                    add(AppTab(label = TeamUsageLabels.tabTrend(language), testTag = TEAM_TAB_TREND_TAG))
                }
            }
            val views = buildList {
                add(TeamUsageView.MEMBERS)
                add(TeamUsageView.BREAKDOWN)
                if (state.isTrendAvailable) {
                    add(TeamUsageView.TREND)
                }
            }

            AppTabs(
                tabs = tabs,
                selectedIndex = views.indexOf(view).coerceAtLeast(0),
                onSelect = { index -> onSelectView(views[index]) },
                modifier = Modifier.weight(1f)
            )

            if (state.isAdminOverview) {
                Text(
                    text = TeamUsageLabels.allEmailGroups(
                        emailCount = state.emailGroups.size,
                        accountCount = state.memberGroups.size,
                        language = language
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.testTag(TEAM_ADMIN_OVERVIEW_TAG)
                )
            }
            // O carimbo da última alteração desceu para a barra de estado; aqui
            // fica só o selo de leitura ao vivo, que é estado do laço e não dado.
            LiveBadge(language = language)

            // A janela vale para as três abas, então trocá-la é a escolha de fora
            // e a aba é a de dentro — as duas na mesma faixa.
            AppSegmentedControl(
                options = CliSessionRange.entries.map { entry ->
                    AppSegment(label = TeamUsageLabels.rangeLabel(entry, language))
                },
                selectedIndex = CliSessionRange.entries.indexOf(state.range),
                onSelect = { index -> onSelectRange(CliSessionRange.entries[index]) }
            )

            // O relatorio nao segue a aba: ele e o recorte inteiro da janela, com
            // integrantes, resumo e sessoes juntos.
            AppButton(
                label = ExportLabels.exportPdf(language),
                onClick = onExportReport,
                modifier = Modifier.testTag(TEAM_EXPORT_PDF_TAG)
            )
        }

        // O rótulo da conta é dado e não controle: fica na linha de texto abaixo
        // da barra, junto das outras qualificações da janela.
        if (state.accountLabel != null) {
            Text(
                text = state.accountLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Blocos de métrica de largura igual, como no modal da máquina: eram
        // três pares valor/rótulo flutuando sobre o mesmo painel.
        //
        // Os totais são o mesmo tipo de coisa e ficam na mesma cor. O acento é
        // identidade de fonte, não de valor: tokens em verde ao lado de custo em
        // azul sugere duas categorias onde há duas medidas da mesma janela.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(
                label = TeamUsageLabels.columnMembers(language),
                value = state.activeMemberCount.toString(),
                // O veredito por sessão vive dois níveis abaixo, dentro de um
                // integrante recolhido. Aqui ele aparece sem nenhum clique.
                footer = CliSessionsLabels.healthTally(state.healthTally, language),
                footerColor = healthTallyColor(state.healthTally),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH).testTag(TEAM_TOTAL_MEMBERS_BLOCK_TAG)
            )

            AppMetricBlock(
                label = TeamUsageLabels.columnTokens(language),
                value = formatQuantity(state.totalTokens),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )

            AppMetricBlock(
                label = TeamUsageLabels.columnCost(language),
                value = if (state.isTotalCostComplete) {
                    formatMicrosUsdShort(state.totalCostMicros)
                } else {
                    "${formatMicrosUsdShort(state.totalCostMicros)}+"
                },
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )
        }

        // Fora dos blocos, pela mesma razão do modal da máquina: a janela do
        // custo é uma frase, e dentro do bloco ela media três vezes a largura
        // dele.
        Text(
            text = TeamUsageLabels.sessionCount(state.sessionCount, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = TeamUsageLabels.estimatedTotalInRange(
                range = state.range,
                endsAt = state.rangeEndsAt,
                isAnchored = state.rangeAnchored,
                language = language
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val exportOutcome = state.exportOutcome
        if (exportOutcome != null) {
            Text(
                text = exportOutcomeMessage(exportOutcome, language),
                style = MaterialTheme.typography.labelSmall,
                color = if (exportOutcome is CliExportOutcome.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Faixa de legendas das colunas, uma vez para a lista inteira.
 *
 * Cada célula reimprimia "Máquina", "Custo", "Tempo ativo" e "do time" ao lado do
 * próprio valor: numa lista de time isso dobra o texto da tela e o ruído cresce
 * com o número de pessoas. A legenda pertence à coluna.
 *
 * Fora da `LazyColumn` de propósito, como na tela de presença: na visão global a
 * lista já tem as faixas de conta rolando dentro dela, e dois níveis de cabeçalho
 * grudado empilhariam.
 */
@Composable
internal fun TeamColumnHeader(
    language: AppLanguage,
    hasStatusColumn: Boolean,
    hasActionColumn: Boolean
) {
    AppColumnHeaderRow(
        modifier = Modifier.padding(end = SCROLLBAR_GUTTER).testTag(TEAM_COLUMN_HEADER_TAG),
        horizontalPadding = TEAM_ROW_HORIZONTAL_PADDING,
        spacing = TEAM_COLUMN_SPACING
    ) {
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnMember(language),
            modifier = Modifier.width(TEAM_COLUMN_IDENTITY)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnSessions(language),
            modifier = Modifier.width(TEAM_COLUMN_SESSIONS)
        )
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnTokens(language),
            modifier = Modifier.width(TEAM_COLUMN_TOKENS)
        )
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnCost(language),
            modifier = Modifier.width(TEAM_COLUMN_COST)
        )
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnActiveTime(language),
            modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnShare(language),
            modifier = Modifier.width(TEAM_COLUMN_SHARE)
        )
        if (hasStatusColumn) {
            AppColumnHeaderLabel(
                label = TeamUsageLabels.columnStatus(language),
                modifier = Modifier.width(TEAM_COLUMN_STATUS)
            )
        }
        if (hasActionColumn) {
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(TEAM_ACTION_SLOT))
        }
    }
}
