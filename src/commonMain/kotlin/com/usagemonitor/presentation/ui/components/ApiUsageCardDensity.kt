package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Largura de card abaixo da qual o layout adota a densidade estreita.
internal val NarrowCardWidthThreshold = 320.dp

// Largura de card abaixo da qual os badges resumidos deixam de caber lado a lado.
internal val StackedQuotaWidthThreshold = 210.dp

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
    val badgeVerticalPadding: Dp,
    val arcSize: Dp,
    val arcStrokeWidth: Dp,
    val arcPercentageFontSize: TextUnit
)

private val regularCardDensity = ApiUsageCardDensity(
    contentHorizontalPadding = 20.dp,
    contentVerticalPadding = 16.dp,
    actionButtonSize = 34.dp,
    actionIconSize = 16.dp,
    actionSpacing = 8.dp,
    headerSpacing = 8.dp,
    compactQuotaSpacing = 10.dp,
    expandedQuotaSpacing = 14.dp,
    badgeHorizontalPadding = 12.dp,
    badgeVerticalPadding = 10.dp,
    arcSize = 92.dp,
    arcStrokeWidth = 10.dp,
    arcPercentageFontSize = 18.sp
)

private val narrowCardDensity = ApiUsageCardDensity(
    contentHorizontalPadding = 12.dp,
    contentVerticalPadding = 12.dp,
    actionButtonSize = 28.dp,
    actionIconSize = 14.dp,
    actionSpacing = 4.dp,
    headerSpacing = 6.dp,
    compactQuotaSpacing = 6.dp,
    expandedQuotaSpacing = 8.dp,
    badgeHorizontalPadding = 8.dp,
    badgeVerticalPadding = 8.dp,
    arcSize = 72.dp,
    arcStrokeWidth = 8.dp,
    arcPercentageFontSize = 15.sp
)

internal fun resolveApiUsageCardDensity(cardWidth: Dp): ApiUsageCardDensity {
    return if (cardWidth < NarrowCardWidthThreshold) {
        narrowCardDensity
    } else {
        regularCardDensity
    }
}

/** Badges resumidos empilham verticalmente quando não há largura para a linha. */
internal fun shouldStackCompactQuotas(cardWidth: Dp, quotaCount: Int): Boolean {
    return quotaCount > 1 && cardWidth < StackedQuotaWidthThreshold
}
