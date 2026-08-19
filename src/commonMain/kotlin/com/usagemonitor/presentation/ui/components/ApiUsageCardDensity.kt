package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Largura de card abaixo da qual o layout adota a densidade estreita.
internal val NarrowCardWidthThreshold = 320.dp

// Largura mínima de cada badge resumido para caber lado a lado com os demais.
internal val MinimumCompactQuotaWidth = 105.dp

/**
 * Dimensões do [ApiUsageCard] derivadas da largura real do card.
 *
 * Função pura (sem `@Composable`) para permitir teste unitário direto das
 * fronteiras de densidade sem subir uma árvore de composição.
 */
internal data class ApiUsageCardDensity(
    val contentHorizontalPadding: Dp,
    val contentVerticalPadding: Dp,
    val actionButtonSize: Dp,
    val actionIconSize: Dp,
    val actionSpacing: Dp,
    val headerSpacing: Dp,
    val compactQuotaSpacing: Dp,
    val expandedQuotaSpacing: Dp,
    val badgeHorizontalPadding: Dp,
    val badgeVerticalPadding: Dp
)

private val regularCardDensity = ApiUsageCardDensity(
    contentHorizontalPadding = 12.dp,
    contentVerticalPadding = 10.dp,
    actionButtonSize = 26.dp,
    actionIconSize = 14.dp,
    actionSpacing = 4.dp,
    headerSpacing = 8.dp,
    compactQuotaSpacing = 8.dp,
    expandedQuotaSpacing = 10.dp,
    badgeHorizontalPadding = 10.dp,
    badgeVerticalPadding = 8.dp
)

private val narrowCardDensity = ApiUsageCardDensity(
    contentHorizontalPadding = 8.dp,
    contentVerticalPadding = 8.dp,
    actionButtonSize = 24.dp,
    actionIconSize = 12.dp,
    actionSpacing = 2.dp,
    headerSpacing = 6.dp,
    compactQuotaSpacing = 6.dp,
    expandedQuotaSpacing = 8.dp,
    badgeHorizontalPadding = 8.dp,
    badgeVerticalPadding = 6.dp
)

internal fun resolveApiUsageCardDensity(cardWidth: Dp): ApiUsageCardDensity {
    return if (cardWidth < NarrowCardWidthThreshold) {
        narrowCardDensity
    } else {
        regularCardDensity
    }
}

/**
 * Badges resumidos empilham verticalmente quando não há largura para a linha.
 *
 * O limite acompanha a quantidade de cotas: com a leitura de créditos o card da
 * Anthropic passou a ter três, e um limite fixo deixaria as três espremidas.
 */
internal fun shouldStackCompactQuotas(cardWidth: Dp, quotaCount: Int): Boolean {
    return quotaCount > 1 && cardWidth < MinimumCompactQuotaWidth * quotaCount
}

// A regra de empilhamento das colunas expandidas saiu junto com os arcos: a cota
// expandida virou linha de largura cheia, e linha empilha por construção. O que
// sobrou de decisão de largura vale só para os badges do card minimizado.

/**
 * Largura mínima de card para o tooltip de cota abrir.
 *
 * Coincide hoje com [NarrowCardWidthThreshold], mas responde a outra pergunta e por
 * isso é constante própria: não é "como apertar o padding", é "o popup de cinco
 * linhas cabe sem cobrir o dado que está sendo lido". Amarrar as duas faria ajustar
 * uma mexer na outra.
 */
internal val QuotaTooltipMinCardWidth = 320.dp

/**
 * Abaixo de [QuotaTooltipMinCardWidth] o tooltip de cota cobre o card inteiro — a
 * janela do modo somente cards tem ~230dp de largura útil, e o popup tem piso de
 * 180dp mais cinco a seis linhas de métrica. Ali ele esconde justamente o número que
 * o ponteiro estava apontando.
 *
 * O preço é conhecido e aceito: em card estreito não há caminho visual para a
 * projeção de uso. Ela volta abrindo a janela.
 */
internal fun shouldShowQuotaTooltip(cardWidth: Dp): Boolean {
    return cardWidth >= QuotaTooltipMinCardWidth
}
