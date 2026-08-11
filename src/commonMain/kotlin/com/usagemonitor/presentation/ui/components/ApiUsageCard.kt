package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.seriesKey
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.domain.entity.statusBadgeLabel
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val COMPACT_QUOTA_BADGE_TAG = "compactQuotaBadge"

/** Opacidade do número de uma janela já vencida — o dado é real, mas velho. */
private const val STALE_QUOTA_ALPHA = 0.45f

// Durações centralizadas das animações do card (em ms). Mantém legibilidade ao
// alterar timing globalmente sem caçar literais espalhados pelo composable.
private object CardAnimations {
    val EXPAND_DURATION_MS  = AppMotion.slow + AppMotion.normal   // 600ms
    val MINIMIZE_DURATION_MS = AppMotion.normal                   // 250ms
    const val PULSE_DURATION_MS = 1500
}

@Composable
fun ApiUsageCard(
    source: ApiSource,
    apiName: String,
    quotas: List<QuotaInfo>,
    accountContext: UsageAccountContext? = null,
    notices: Set<ApiUsageNotice> = emptySet(),
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary> = emptyMap(),
    showUsageDetails: Boolean,
    isRefreshing: Boolean,
    isMinimized: Boolean = false,
    isBeingDragged: Boolean = false,
    isDragTarget: Boolean = false,
    language: AppLanguage,
    animationDelayMillis: Int,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit = {},
    /** Só os cards Anthropic recebem: sessões do Claude Code pertencem a uma conta. */
    onOpenCliSessions: (() -> Unit)? = null,
    /**
     * Só os cards Anthropic de contas marcadas como parte do time recebem, e só
     * com a integração ligada. Nulo esconde o botão — quem não usa a integração
     * não ganha um botão que não leva a lugar nenhum.
     */
    onOpenTeamUsage: (() -> Unit)? = null,
    onToggleMinimized: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    /**
     * Instante contra o qual o vencimento das janelas de cota é medido.
     *
     * É um valor, não um relógio: quem avança o tempo é a `DashboardScreen`, o
     * único ponto stateful da tela. Assim o card segue sem corrotina própria.
     */
    now: Instant = Clock.System.now(),
    modifier: Modifier = Modifier
) {
    val orderedQuotas = buildList {
        quotas.firstOrNull { it.periodType == PeriodType.REPORTED }?.let(::add)
        quotas.firstOrNull { it.periodType == PeriodType.INTERVAL }?.let(::add)
        quotas.firstOrNull { it.periodType == PeriodType.WEEKLY }?.let(::add)
        quotas.filterNot { quota -> quota in this }
            .forEach(::add)
    }

    var visible by remember(source) { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    LaunchedEffect(source) {
        visible = false
        delay(animationDelayMillis.toLong())
        visible = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = CardAnimations.EXPAND_DURATION_MS),
        label = "cardAlpha"
    )

    val cardScale by animateFloatAsState(
        targetValue = when {
            isBeingDragged -> 1.02f
            isDragTarget -> 0.99f
            visible -> 1f
            else -> 0.96f
        },
        animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS),
        label = "cardScale"
    )
    val cardOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = CardAnimations.EXPAND_DURATION_MS),
        label = "cardOffsetY"
    )
    val cardElevation by animateDpAsState(
        targetValue = when {
            isBeingDragged -> 14.dp
            isRefreshing   -> 8.dp
            isDragTarget   -> 6.dp
            else           -> AppElevation.card
        },
        animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS),
        label = "cardElevation"
    )
    val pulseAlpha by animateFloatAsState(
        targetValue = when {
            isBeingDragged -> 0.3f
            isDragTarget -> 0.2f
            isRefreshing -> 0.22f
            else -> 0.12f
        },
        animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS),
        label = "pulseAlpha"
    )
    val shimmerProgress = if (isRefreshing) {
        rememberInfiniteTransition(label = "cardShimmer")
            .animateFloat(
                initialValue = -1f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = CardAnimations.PULSE_DURATION_MS),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shimmerProgress"
            ).value
    } else {
        0f
    }

    val hoverBackground by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.surfaceVariant
                      else           cardContainerColor(),
        animationSpec = tween(durationMillis = AppMotion.fast),
        label = "cardHoverBg"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(hoverInteraction)
            .animateContentSize(animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS))
            .pointerInput(source) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount)
                }
            }
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
                translationY = cardOffsetY.toPx()
            },
        shape = AppShapes.large,
        colors = CardDefaults.cardColors(containerColor = hoverBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = cardElevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(AppShapes.large)
                .drawWithCache {
                    val accentColor = accentColorFor(source = source).copy(alpha = pulseAlpha)
                    val topGlow = Brush.verticalGradient(
                        colors = listOf(
                            accentColor,
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = size.height * 0.7f
                    )
                    val streakBrush = if (isRefreshing) {
                        val streakStartX = size.width * shimmerProgress
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                accentColor.copy(alpha = 0.34f),
                                Color.Transparent
                            ),
                            start = Offset(streakStartX - size.width * 0.4f, 0f),
                            end = Offset(streakStartX, size.height)
                        )
                    } else {
                        null
                    }

                    onDrawWithContent {
                        drawContent()
                        drawRect(brush = topGlow)
                        if (streakBrush != null) {
                            drawRect(brush = streakBrush)
                        }
                    }
                }
        ) {
            val density = resolveApiUsageCardDensity(maxWidth)
            val stackCompactQuotas = shouldStackCompactQuotas(
                cardWidth = maxWidth,
                quotaCount = orderedQuotas.size
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = density.contentHorizontalPadding,
                        vertical = density.contentVerticalPadding
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(density.headerSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        HoverTooltipBox(
                            title = apiName,
                            metrics = emptyList(),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = apiName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        source.statusBadgeLabel(language)?.let { badgeLabel ->
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier
                                    .clip(AppShapes.medium)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(density.actionSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardIconActionButton(
                            label = refreshActionLabel(
                                isRefreshing = isRefreshing,
                                language = language
                            ),
                            onClick = onRefresh,
                            buttonSize = density.actionButtonSize,
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(density.actionIconSize),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(density.actionIconSize),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        CardIconActionButton(
                            label = historyActionLabel(language = language),
                            onClick = onOpenHistory,
                            buttonSize = density.actionButtonSize
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(density.actionIconSize),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        if (onOpenCliSessions != null) {
                            CardIconActionButton(
                                label = cliSessionsActionLabel(language = language),
                                onClick = onOpenCliSessions,
                                buttonSize = density.actionButtonSize
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    modifier = Modifier.size(density.actionIconSize),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // Só chega não-nulo quando a integração está ligada e esta
                        // conta foi marcada como parte do time nas Configurações.
                        if (onOpenTeamUsage != null) {
                            CardIconActionButton(
                                label = teamUsageActionLabel(language = language),
                                onClick = onOpenTeamUsage,
                                buttonSize = density.actionButtonSize
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Groups,
                                    contentDescription = null,
                                    modifier = Modifier.size(density.actionIconSize),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        CardIconActionButton(
                            label = minimizeActionLabel(
                                isMinimized = isMinimized,
                                language = language
                            ),
                            onClick = onToggleMinimized,
                            buttonSize = density.actionButtonSize
                        ) {
                            Icon(
                                imageVector = if (isMinimized) {
                                    Icons.Rounded.Add
                                } else {
                                    Icons.Rounded.Remove
                                },
                                contentDescription = null,
                                modifier = Modifier.size(density.actionIconSize),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (accountContext != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AccountIdentityLabel(
                        account = accountContext,
                        language = language,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = isMinimized,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(durationMillis = AppMotion.normal, delayMillis = 50, easing = AppMotion.enterEasing)) +
                            scaleIn(
                                animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS, easing = AppMotion.enterEasing),
                                initialScale = 0.97f
                            )).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.exitEasing)) +
                                scaleOut(
                                    animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.exitEasing),
                                    targetScale = 0.98f
                                )
                        ).using(SizeTransform(clip = false))
                    },
                    label = "cardLayoutMode"
                ) { minimized ->
                    if (source.isObservedActivitySource()) {
                        OpenCodeUsageSummary(
                            source = source,
                            quotas = orderedQuotas,
                            language = language,
                            compact = minimized
                        )
                    } else if (minimized) {
                        CompactQuotaSummary(
                            source = source,
                            quotas = orderedQuotas,
                            showUsageDetails = showUsageDetails,
                            language = language,
                            riskByQuotaKey = riskByQuotaKey,
                            density = density,
                            stacked = stackCompactQuotas,
                            now = now
                        )
                    } else {
                        ExpandedQuotaSummary(
                            quotas = orderedQuotas,
                            showUsageDetails = showUsageDetails,
                            language = language,
                            riskByQuotaKey = riskByQuotaKey,
                            density = density,
                            now = now
                        )
                    }
                }

                if (!isMinimized && notices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    InlineNotices(
                        notices = notices,
                        source = source,
                        language = language
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountIdentityLabel(
    account: UsageAccountContext,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(
                    text = if (language == AppLanguage.PT) {
                        "Conta da última coleta: ${account.displayLabel}"
                    } else {
                        "Account from last snapshot: ${account.displayLabel}"
                    }
                )
            }
        },
        state = tooltipState,
        modifier = modifier
    ) {
        Text(
            text = account.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("usageAccountLabel")
                .semantics {
                    contentDescription = if (language == AppLanguage.PT) {
                        "Conta da última coleta: ${account.displayLabel}"
                    } else {
                        "Account from last snapshot: ${account.displayLabel}"
                    }
                }
        )
    }
}

@Composable
private fun InlineNotices(
    notices: Set<ApiUsageNotice>,
    source: ApiSource,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        notices
            .toList()
            .sortedBy { notice -> notice.ordinal }
            .forEach { notice ->
                Text(
                    text = inlineNoticeText(notice = notice, language = language),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColorFor(source),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AppShapes.medium)
                        .background(accentColorFor(source).copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
    }
}

private fun inlineNoticeText(notice: ApiUsageNotice, language: AppLanguage): String {
    return when (notice) {
        ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE -> {
            if (language == AppLanguage.PT) {
                "Quota 7d indisponível na fonte semanal do Codex"
            } else {
                "7d quota unavailable in Codex weekly source"
            }
        }
        ApiUsageNotice.SOURCE_UNSTABLE -> {
            if (language == AppLanguage.PT) {
                "Fonte de uso do Codex instável: o contrato mudou e os limites podem oscilar até estabilizar."
            } else {
                "Codex usage source is unstable: the contract changed and limits may fluctuate until it stabilizes."
            }
        }
    }
}

@Composable
private fun OpenCodeUsageSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val modelSummaries = remember(quotas) { buildOpenCodeModelSummaries(quotas) }

    if (modelSummaries.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(AppShapes.extraLarge)
                .background(accentColorFor(source).copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (language == AppLanguage.PT) "Nenhum uso free detectado" else "No free usage detected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (language == AppLanguage.PT) {
                    "Abra o ${source.displayName(language)} e use um modelo free para começar a preencher este card."
                } else {
                    "Use a free ${source.displayName(language)} model to start populating this card."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)
    ) {
        modelSummaries.forEach { summary ->
            OpenCodeModelRow(
                source = source,
                summary = summary,
                language = language,
                compact = compact
            )
        }
    }
}

@Composable
private fun OpenCodeModelRow(
    source: ApiSource,
    summary: OpenCodeModelSummary,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    // No modo compacto a linha não renderiza as barras internas (que já têm
    // tooltip própria), então é seguro dar tooltip à linha inteira.
    if (compact) {
        HoverTooltipBox(
            title = summary.modelName,
            subtitle = if (language == AppLanguage.PT) "Atividade observada" else "Observed activity",
            metrics = buildOpenCodeTooltipMetrics(summary = summary, language = language),
            modifier = modifier
        ) {
            OpenCodeModelRowContent(
                source = source,
                summary = summary,
                language = language,
                compact = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        return
    }

    OpenCodeModelRowContent(
        source = source,
        summary = summary,
        language = language,
        compact = false,
        modifier = modifier
    )
}

@Composable
private fun OpenCodeModelRowContent(
    source: ApiSource,
    summary: OpenCodeModelSummary,
    language: AppLanguage,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.extraLarge)
            .background(accentColorFor(source).copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = summary.modelName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (language == AppLanguage.PT) "Limite oficial indisponível" else "Official limit unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = localizedRequestCount(summary.requestsFiveHours, language),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = openCodePrimaryWindowLabel(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = openCodeSecondaryWindowLabel(summary.requestsSevenDays, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!compact) {
            OpenCodeInlineComparisonChart(
                source = source,
                summary = summary,
                language = language
            )
        }
    }
}


@Composable
private fun OpenCodeInlineComparisonChart(
    source: ApiSource,
    summary: OpenCodeModelSummary,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val maxValue = maxOf(summary.requestsFiveHours, summary.requestsSevenDays, 1L).toFloat()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (language == AppLanguage.PT) {
                "Atividade observada"
            } else {
                "Observed activity"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OpenCodeInlineBar(
            modelName = summary.modelName,
            label = "5h",
            value = summary.requestsFiveHours,
            fraction = summary.requestsFiveHours / maxValue,
            accentColor = accentColorFor(source),
            language = language
        )
        OpenCodeInlineBar(
            modelName = summary.modelName,
            label = "7d",
            value = summary.requestsSevenDays,
            fraction = summary.requestsSevenDays / maxValue,
            accentColor = accentColorFor(source),
            language = language
        )
    }
}

@Composable
private fun OpenCodeInlineBar(
    modelName: String,
    label: String,
    value: Long,
    fraction: Float,
    accentColor: Color,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HoverTooltipBox(
            title = modelName,
            subtitle = if (language == AppLanguage.PT) {
                "Atividade observada"
            } else {
                "Observed activity"
            },
            metrics = listOf(
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Janela" else "Window",
                    value = label
                ),
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Requisições" else "Requests",
                    value = value.toString()
                )
            ),
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(AppShapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(AppShapes.small)
                        .background(accentColor.copy(alpha = 0.82f))
                )
            }
        }

        Text(
            text = if (language == AppLanguage.PT) "$value req." else "$value req.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}


@Composable
private fun CardIconActionButton(
    label: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    HoverTooltipBox(
        title = label,
        metrics = emptyList()
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(buttonSize)
                .semantics {
                    contentDescription = label
                }
        ) {
            content()
        }
    }
}

@Composable
private fun CompactQuotaSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    stacked: Boolean,
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
    now: Instant,
    modifier: Modifier = Modifier
) {
    val isExpired = quota.isExpiredAt(now)

    HoverTooltipBox(
        title = quota.label,
        subtitle = expandedQuotaTitle(quota = quota, language = language),
        metrics = buildQuotaTooltipMetrics(quota = quota, language = language, now = now, risk = risk),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(COMPACT_QUOTA_BADGE_TAG)
                .clip(AppShapes.extraLarge)
                .background(accentColorFor(source = source).copy(alpha = 0.12f))
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

            Text(
                text = compactPercentageLabel(quota),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                // Mesmo tratamento do arco: o numero e o da janela anterior.
                modifier = Modifier.alpha(if (isExpired) STALE_QUOTA_ALPHA else 1f)
            )

            if (showUsageDetails && quota.unit != UsageUnit.PERCENTAGE && quota.unit != UsageUnit.CURRENCY_USD) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatUsage(quota),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ExpandedQuotaSummary(
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    riskByQuotaKey: Map<QuotaSeriesKey, QuotaRiskSummary>,
    density: ApiUsageCardDensity,
    now: Instant,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(density.expandedQuotaSpacing),
        verticalAlignment = Alignment.Top
    ) {
        for (index in quotas.indices) {
            val quota = quotas[index]
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                QuotaColumn(
                    quota = quota,
                    showUsageDetails = showUsageDetails,
                    language = language,
                    risk = riskByQuotaKey[quota.seriesKey],
                    density = density,
                    now = now
                )
            }
        }
    }
}

@Composable
private fun QuotaColumn(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    risk: QuotaRiskSummary?,
    density: ApiUsageCardDensity,
    now: Instant,
    modifier: Modifier = Modifier
) {
    val isExpired = quota.isExpiredAt(now)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HoverTooltipBox(
            title = quota.label,
            subtitle = expandedQuotaTitle(quota = quota, language = language),
            metrics = buildQuotaTooltipMetrics(quota = quota, language = language, now = now)
        ) {
            UsageArcChart(
                used = quota.used,
                total = quota.total,
                unit = quota.unit,
                size = density.arcSize,
                strokeWidth = density.arcStrokeWidth,
                // Janela vencida: o arco continua sendo o ultimo dado real da
                // fonte, so esmaecido para nao passar por leitura corrente.
                modifier = Modifier.alpha(if (isExpired) STALE_QUOTA_ALPHA else 1f),
                percentageTextStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = density.arcPercentageFontSize,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        if (risk != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RiskSemaphoreDot(risk = risk, quotaLabel = quota.label, language = language)
                Text(
                    text = expandedQuotaTitle(quota = quota, language = language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = expandedQuotaTitle(quota = quota, language = language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (showUsageDetails && quota.unit != UsageUnit.PERCENTAGE && quota.unit != UsageUnit.CURRENCY_USD) {
            Text(
                text = formatUsage(quota),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = resetLabel(quota = quota, language = language, now = now),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

