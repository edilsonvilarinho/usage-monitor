package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.presentation.ui.components.ActivityHeatmapGrid
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Orçamento do mês, num painel de dados com cabeçalho.
 *
 * Não existe no protótipo — é recurso posterior a ele — e por isso recebe a
 * anatomia comum: cabeçalho com título e divisória, corpo com o valor, a barra e
 * as qualificações. O título deixou de ser azul pelo mesmo motivo dos totais.
 */
@Composable
internal fun BudgetPanel(
    budget: MonthlyBudgetStatus?,
    accountCredits: AccountCreditUsage?,
    language: AppLanguage
) {
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = BreakdownLabels.budgetTitle(language)) }
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            if (budget != null) {
                Text(
                    text = BreakdownLabels.budgetValue(
                        spentMicros = budget.spentMicros,
                        limitMicros = budget.limitMicros,
                        isComplete = budget.isSpendComplete,
                        language = language
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (budget.isExceeded) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                AppProgressTrack(
                    fraction = budget.share.toFloat(),
                    tone = if (budget.isExceeded) AppTone.CRITICAL else AppTone.INFO
                )
                NoticeText(
                    BreakdownLabels.budgetProjection(budget.projectedMicros, budget.willExceed, language),
                    if (budget.willExceed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                NoticeText(
                    BreakdownLabels.budgetScopeNotice(language),
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (accountCredits != null) {
                // Linha própria e moeda explícita: somar isto ao valor acima daria
                // um número inventado quando a conta não é em USD.
                NoticeText(
                    BreakdownLabels.accountCredits(
                        usedMinorUnits = accountCredits.usedMinorUnits,
                        limitMinorUnits = accountCredits.limitMinorUnits,
                        currencyCode = accountCredits.currencyCode,
                        language = language
                    ),
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * A página do eixo como tabela: uma faixa de legendas e uma linha por balde.
 *
 * Era um card por linha, com o rótulo em cima, o custo à direita e uma barra de
 * largura total embaixo — três elementos para dizer o que uma linha de tabela diz
 * com colunas alinhadas, e numa lista de dez projetos a tela virava dez blocos.
 *
 * A coluna de tempo ativo aparece **uma vez para a lista inteira** ou não
 * aparece: os eixos de modelo e de ferramenta não têm hora, e uma coluna que
 * existe em algumas linhas e some em outras desloca tudo o que vem depois.
 */
@Composable
internal fun BucketTable(
    page: BreakdownPage<CliUsageBucket>,
    axis: BreakdownAxis,
    totals: CliUsageBucket,
    unknownLabel: String,
    language: AppLanguage
) {
    val hasActiveTime = page.items.any { bucket -> bucket.activeMillis != null }

    AppDataSurfaceFlush(
        header = {
            AppColumnHeaderRow(startGutter = 0.dp) {
                AppColumnHeaderLabel(
                    label = BreakdownLabels.columnAxis(axis, language),
                    modifier = Modifier.weight(1f).widthIn(min = AXIS_COLUMN_MIN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = CliSessionsLabels.columnSessions(language),
                    modifier = Modifier.width(SESSIONS_COLUMN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = BreakdownLabels.columnTurns(language),
                    modifier = Modifier.width(TURNS_COLUMN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = CliSessionsLabels.columnTokens(language),
                    modifier = Modifier.width(TOKENS_COLUMN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = CliSessionsLabels.columnCost(language),
                    modifier = Modifier.width(COST_COLUMN_WIDTH)
                )
                if (hasActiveTime) {
                    AppColumnHeaderLabel(
                        label = CliSessionsLabels.activeTime(language),
                        modifier = Modifier.width(ACTIVE_TIME_COLUMN_WIDTH)
                    )
                }
                AppColumnHeaderLabel(
                    label = CliSessionsLabels.columnShare(language),
                    modifier = Modifier.width(SHARE_COLUMN_WIDTH)
                )
            }
        }
    ) {
        page.items.forEachIndexed { index, bucket ->
            BucketRow(
                bucket = bucket,
                totals = totals,
                unknownLabel = unknownLabel,
                hasActiveTime = hasActiveTime,
                showDivider = index < page.items.lastIndex,
                language = language
            )
        }
    }
}

@Composable
private fun BucketRow(
    bucket: CliUsageBucket,
    totals: CliUsageBucket,
    unknownLabel: String,
    hasActiveTime: Boolean,
    showDivider: Boolean,
    language: AppLanguage
) {
    val share = bucket.costShareOf(totals)

    AppDataRow(showDivider = showDivider) {
        AppCellValue(
            value = bucket.label ?: unknownLabel,
            modifier = Modifier.weight(1f).widthIn(min = AXIS_COLUMN_MIN_WIDTH)
        )
        AppCellValue(
            value = bucket.sessionCount.toString(),
            modifier = Modifier.width(SESSIONS_COLUMN_WIDTH)
        )
        AppCellValue(
            value = bucket.turnCount.toString(),
            modifier = Modifier.width(TURNS_COLUMN_WIDTH)
        )
        AppCellValue(
            value = formatQuantity(bucket.totalTokens),
            modifier = Modifier.width(TOKENS_COLUMN_WIDTH)
        )
        AppCellValue(
            value = BreakdownLabels.bucketCost(bucket),
            modifier = Modifier.width(COST_COLUMN_WIDTH)
        )
        if (hasActiveTime) {
            // Hora nula é eixo sem medida e hora zero é balde só de sessões de um
            // turno: nos dois casos sai o travessão, porque "0min" seria lido como
            // trabalho instantâneo.
            AppCellValue(
                value = bucket.activeMillis
                    ?.takeIf { millis -> millis > 0L }
                    ?.let { millis -> formatActiveTime(millis) }
                    ?: "—",
                modifier = Modifier.width(ACTIVE_TIME_COLUMN_WIDTH)
            )
        }
        Column(modifier = Modifier.width(SHARE_COLUMN_WIDTH)) {
            AppCellValue(value = formatPercent(share))
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            AppProgressTrack(fraction = share.toFloat(), tone = AppTone.INFO)
        }
    }
}

/**
 * A página de ferramentas, na mesma anatomia.
 *
 * Sem coluna de custo: um turno que chama `Read` e `Bash` gastou tokens uma vez
 * só, e ratear entre as duas contaria o mesmo gasto duas vezes. A fatia é contra
 * a ferramenta mais chamada da janela **inteira**, não da página — a pergunta é
 * qual domina, e renormalizar por página faria a primeira linha de toda página
 * parecer o pico.
 */
@Composable
internal fun ToolTable(page: BreakdownPage<CliToolUsage>, peak: Int, language: AppLanguage) {
    AppDataSurfaceFlush(
        header = {
            AppColumnHeaderRow(startGutter = 0.dp) {
                AppColumnHeaderLabel(
                    label = BreakdownLabels.columnAxis(BreakdownAxis.TOOL, language),
                    modifier = Modifier.weight(1f).widthIn(min = AXIS_COLUMN_MIN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = BreakdownLabels.columnCalls(language),
                    modifier = Modifier.width(CALLS_COLUMN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = BreakdownLabels.columnTurns(language),
                    modifier = Modifier.width(TURNS_COLUMN_WIDTH)
                )
                AppColumnHeaderLabel(
                    label = CliSessionsLabels.columnShare(language),
                    modifier = Modifier.width(SHARE_COLUMN_WIDTH)
                )
            }
        }
    ) {
        page.items.forEachIndexed { index, tool ->
            val share = if (peak <= 0) 0.0 else tool.callCount.toDouble() / peak.toDouble()
            AppDataRow(showDivider = index < page.items.lastIndex) {
                AppCellValue(
                    value = tool.toolName,
                    modifier = Modifier.weight(1f).widthIn(min = AXIS_COLUMN_MIN_WIDTH)
                )
                AppCellValue(
                    value = tool.callCount.toString(),
                    modifier = Modifier.width(CALLS_COLUMN_WIDTH)
                )
                AppCellValue(
                    value = tool.turnCount.toString(),
                    modifier = Modifier.width(TURNS_COLUMN_WIDTH)
                )
                Column(modifier = Modifier.width(SHARE_COLUMN_WIDTH)) {
                    AppCellValue(value = formatPercent(share))
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    AppProgressTrack(fraction = share.toFloat(), tone = AppTone.INFO)
                }
            }
        }
    }
}

/**
 * A grade de atividade, num painel com cabeçalho.
 *
 * A explicação vive no `trailing` do cabeçalho, como no protótipo: ela qualifica
 * a grade inteira, e abaixo dela lia como mais uma linha de dado.
 */
@Composable
internal fun ActivityPanel(breakdown: CliUsageBreakdown, language: AppLanguage) {
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = BreakdownLabels.activityTitle(language),
                trailing = {
                    Text(
                        text = BreakdownLabels.activityNotice(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            )
        }
    ) {
        Box(modifier = Modifier.padding(AppSpacing.md)) {
            ActivityHeatmapGrid(
                heatmap = breakdown.heatmap,
                accent = AppAccents.current.cacheRead,
                language = language,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
