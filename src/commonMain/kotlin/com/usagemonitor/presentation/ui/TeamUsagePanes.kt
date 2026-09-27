package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.components.TeamTrendChart
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.viewmodel.TeamSessionDetailUiState

/**
 * Aba da tendência: o gráfico com a altura inteira do modal.
 *
 * Era um painel fixo acima da lista, e nessa posição comia cerca de metade da
 * janela para mostrar um recorte — dias — que nem sequer obedece ao filtro de
 * janela escolhido logo acima dele. Como aba, ele só aparece para quem o pediu, e
 * aí tem espaço para ser lido.
 *
 * Rola porque a altura cresce com o número de integrantes: com dez pessoas a
 * última faixa cairia fora da janela.
 */
@Composable
internal fun TeamTrendPane(trend: TeamUsageTrend?, language: AppLanguage) {
    if (trend == null) {
        AppEmptyState(TeamUsageLabels.trendUnavailable(language))
        return
    }
    if (trend.isEmpty) {
        AppEmptyState(TeamUsageLabels.trendEmpty(language))
        return
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().testTag(TEAM_TREND_PANE_TAG)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // O gráfico primeiro e a explicação depois, como no protótipo: um
            // painel de três linhas acima dele empurrava as barras para baixo da
            // dobra numa janela baixa, e a frase que diz o que ele mede já vive
            // na linha de cabeçalho do próprio gráfico.
            TeamTrendChart(trend = trend, language = language)

            Text(
                text = TeamUsageLabels.trendHint(trend.days.size, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // O fuso e a independência do filtro de janela: sem esta frase, um
            // gráfico de dias ao lado de números de 5h é lido como o mesmo
            // recorte.
            Text(
                text = TeamUsageLabels.trendNotice(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(TEAM_TREND_SCROLLBAR_TAG)
        )
    }
}

// ----------------------------------------------------------------------------
// Detalhe da sessão
// ----------------------------------------------------------------------------

/**
 * O mesmo painel do modal de Sessões CLI, para uma sessão de outra máquina.
 *
 * As seções vêm de `CliSessionsScreen` — a sessão de um colega tem de ser lida
 * exatamente como a da própria máquina. Aqui só se orquestra a rolagem e o
 * estado de carga; nenhuma métrica é recalculada.
 */
@Composable
internal fun TeamSessionDetailPane(
    detail: TeamSessionDetailUiState,
    language: AppLanguage,
    /** Sessão desta máquina; só ela oferece o botão de copiar (issue #102). */
    isLocalSession: Boolean,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onCloseDetail: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppButton(
                label = TeamUsageLabels.back(language),
                onClick = onCloseDetail,
                tone = AppButtonTone.GHOST
            )
            Text(
                text = shortSessionId(detail.sessionId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // O painel também abre a sessão de um colega, e ali não há o que
            // copiar: o transcript não está nesta máquina e a issue #102 pede
            // que a sessão de outro integrante não seja copiável.
            if (isLocalSession) {
                CopySessionCommandButton(
                    sessionId = detail.sessionId,
                    language = language,
                    showLabel = true
                )
            }
        }

        when (detail) {
            is TeamSessionDetailUiState.Loading -> AppLoadingState(
                TeamUsageLabels.detailLoading(language)
            )

            is TeamSessionDetailUiState.Error -> AppErrorState(
                TeamUsageLabels.serverError(detail.message, language)
            )

            is TeamSessionDetailUiState.Ready -> TeamSessionDetailBody(
                detail = detail,
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
private fun TeamSessionDetailBody(
    detail: TeamSessionDetailUiState.Ready,
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
                detail = detail.result.detail,
                analytics = detail.result.analytics,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary,
                missingTurnsNotice = if (detail.turnsUnavailable) {
                    TeamUsageLabels.missingTurnsNotice(language)
                } else {
                    null
                }
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(TEAM_DETAIL_SCROLLBAR_TAG)
        )
    }
}
