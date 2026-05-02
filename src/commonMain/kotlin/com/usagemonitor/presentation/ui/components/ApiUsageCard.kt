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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val COMPACT_QUOTA_BADGE_TAG = "compactQuotaBadge"

// Durações centralizadas das animações do card (em ms). Mantém legibilidade ao
// alterar timing globalmente sem caçar literais espalhados pelo composable.
private object CardAnimations {
    const val EXPAND_DURATION_MS = 550
    const val MINIMIZE_DURATION_MS = 240
    const val PULSE_DURATION_MS = 1500
}

@Composable
fun ApiUsageCard(
    source: ApiSource,
    apiName: String,
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    isRefreshing: Boolean,
    isMinimized: Boolean = false,
    isBeingDragged: Boolean = false,
    isDragTarget: Boolean = false,
    language: AppLanguage,
    animationDelayMillis: Int,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onToggleMinimized: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val orderedQuotas = buildList {
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
        Box(
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = apiName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CardIconActionButton(
                            label = refreshActionLabel(
                                isRefreshing = isRefreshing,
                                language = language
                            ),
                            onClick = onRefresh,
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        CardIconActionButton(
                            label = historyActionLabel(language = language),
                            onClick = onOpenHistory
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        CardIconActionButton(
                            label = minimizeActionLabel(
                                isMinimized = isMinimized,
                                language = language
                            ),
                            onClick = onToggleMinimized
                        ) {
                            Icon(
                                imageVector = if (isMinimized) {
                                    Icons.Rounded.Add
                                } else {
                                    Icons.Rounded.Remove
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = isMinimized,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 50)) +
                            scaleIn(
                                animationSpec = tween(durationMillis = CardAnimations.MINIMIZE_DURATION_MS),
                                initialScale = 0.97f
                            )).togetherWith(
                            fadeOut(animationSpec = tween(durationMillis = 140)) +
                                scaleOut(
                                    animationSpec = tween(durationMillis = 180),
                                    targetScale = 0.98f
                                )
                        ).using(SizeTransform(clip = false))
                    },
                    label = "cardLayoutMode"
                ) { minimized ->
                    if (minimized) {
                        CompactQuotaSummary(
                            source = source,
                            quotas = orderedQuotas,
                            showUsageDetails = showUsageDetails
                        )
                    } else {
                        ExpandedQuotaSummary(
                            quotas = orderedQuotas,
                            showUsageDetails = showUsageDetails,
                            language = language
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardIconActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(34.dp)
            .semantics {
                contentDescription = label
            }
    ) {
        content()
    }
}

@Composable
private fun CompactQuotaSummary(
    source: ApiSource,
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
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
                modifier = Modifier.fillMaxWidth(badgeWidthFraction)
            )
        }

        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .testTag(COMPACT_QUOTA_BADGE_TAG)
            .clip(AppShapes.extraLarge)
            .background(accentColorFor(source = source).copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = quota.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = compactPercentageLabel(quota),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        if (showUsageDetails && quota.unit != UsageUnit.PERCENTAGE) {
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

@Composable
private fun ExpandedQuotaSummary(
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
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
                    language = language
                )
            }

            if (index != quotas.lastIndex) {
                Spacer(modifier = Modifier.width(14.dp))
            }
        }
    }
}

@Composable
private fun QuotaColumn(
    quota: QuotaInfo,
    showUsageDetails: Boolean,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UsageArcChart(
            used = quota.used,
            total = quota.total,
            unit = quota.unit,
            size = 92.dp,
            strokeWidth = 10.dp,
            percentageTextStyle = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (showUsageDetails && quota.unit != UsageUnit.PERCENTAGE) {
            Text(
                text = formatUsage(quota),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (quota.periodType == PeriodType.WEEKLY) {
            Text(
                text = if (language == AppLanguage.PT) "Semanal" else "Weekly",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = resetLabel(quota = quota, language = language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun cardContainerColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) {
        scheme.surface.copy(alpha = 0.98f)
    } else {
        scheme.surface
    }
}

private fun accentColorFor(source: ApiSource): Color {
    return when (source) {
        ApiSource.ANTHROPIC -> Color(0xFF4F8CFF)
        ApiSource.MINIMAX -> Color(0xFFFF8A3D)
        ApiSource.CODEX -> Color(0xFF27BFA3)
    }
}

private fun refreshActionLabel(isRefreshing: Boolean, language: AppLanguage): String {
    return if (isRefreshing) {
        if (language == AppLanguage.PT) "Atualizando" else "Refreshing"
    } else {
        if (language == AppLanguage.PT) "Atualizar" else "Refresh"
    }
}

private fun minimizeActionLabel(isMinimized: Boolean, language: AppLanguage): String {
    if (isMinimized) {
        return if (language == AppLanguage.PT) "Expandir card" else "Expand card"
    }

    return if (language == AppLanguage.PT) "Minimizar card" else "Minimize card"
}

private fun historyActionLabel(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Abrir histórico" else "Open history"
}

private fun resetLabel(quota: QuotaInfo, language: AppLanguage): String {
    if (!quota.hasKnownResetAt) {
        return if (language == AppLanguage.PT) {
            "Janela de reset ainda não disponível"
        } else {
            "Reset window not available yet"
        }
    }

    val saoPauloTz = TimeZone.of("America/Sao_Paulo")
    val resetLocal = quota.periodEndAt.toLocalDateTime(saoPauloTz)
    val dayFormatted = when (resetLocal.dayOfWeek) {
        DayOfWeek.MONDAY -> if (language == AppLanguage.PT) "Seg" else "Mon"
        DayOfWeek.TUESDAY -> if (language == AppLanguage.PT) "Ter" else "Tue"
        DayOfWeek.WEDNESDAY -> if (language == AppLanguage.PT) "Qua" else "Wed"
        DayOfWeek.THURSDAY -> if (language == AppLanguage.PT) "Qui" else "Thu"
        DayOfWeek.FRIDAY -> if (language == AppLanguage.PT) "Sex" else "Fri"
        DayOfWeek.SATURDAY -> if (language == AppLanguage.PT) "Sáb" else "Sat"
        DayOfWeek.SUNDAY -> if (language == AppLanguage.PT) "Dom" else "Sun"
    }

    return if (language == AppLanguage.PT) {
        "Reinício: $dayFormatted ${resetLocal.hour}h${resetLocal.minute.toString().padStart(2, '0')} BRT"
    } else {
        "Reset: $dayFormatted ${resetLocal.hour}:${resetLocal.minute.toString().padStart(2, '0')} BRT"
    }
}

private fun compactPercentageLabel(quota: QuotaInfo): String {
    if (quota.unit == UsageUnit.PERCENTAGE) {
        return "${quota.used}%"
    }

    return "${(quota.percentageUsed * 100).roundToInt()}%"
}

private fun formatUsage(quota: QuotaInfo): String {
    return when (quota.unit) {
        UsageUnit.PERCENTAGE -> "${quota.used}%"
        UsageUnit.TOKENS -> {
            val displayUsed = if (quota.rawUsed > 0L) quota.rawUsed else quota.used
            val displayTotal = if (quota.rawTotal > 0L) quota.rawTotal else quota.total
            "${abbreviate(displayUsed)}/${abbreviate(displayTotal)} tok"
        }
        UsageUnit.REQUESTS -> "${abbreviate(quota.used)}/${abbreviate(quota.total)} req"
    }
}

private fun abbreviate(n: Long): String {
    return when {
        n >= 1_000_000L -> "${removeDecimal("%.1f".format(n / 1_000_000f))}M"
        n >= 1_000L -> "${removeDecimal("%.1f".format(n / 1_000f))}K"
        else -> n.toString()
    }
}

private fun removeDecimal(value: String): String = value.replace(",0", "").replace(".0", "")
