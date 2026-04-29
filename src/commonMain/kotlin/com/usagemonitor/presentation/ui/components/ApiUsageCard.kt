package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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

@Composable
fun ApiUsageCard(
    source: ApiSource,
    apiName: String,
    quotas: List<QuotaInfo>,
    showUsageDetails: Boolean,
    isRefreshing: Boolean,
    language: AppLanguage,
    animationDelayMillis: Int,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedQuotas = buildList {
        quotas.firstOrNull { it.periodType == PeriodType.INTERVAL }?.let(::add)
        quotas.firstOrNull { it.periodType == PeriodType.WEEKLY }?.let(::add)
        quotas.filterNot { quota -> quota in this }
            .forEach(::add)
    }

    var visible by remember(source) { mutableStateOf(false) }

    LaunchedEffect(source) {
        visible = false
        delay(animationDelayMillis.toLong())
        visible = true
    }

    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 550),
        label = "cardAlpha"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.96f,
        animationSpec = tween(durationMillis = 550),
        label = "cardScale"
    )
    val cardOffsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = 550),
        label = "cardOffsetY"
    )
    val cardElevation by animateDpAsState(
        targetValue = if (isRefreshing) 12.dp else 6.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cardElevation"
    )
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isRefreshing) 0.22f else 0.12f,
        animationSpec = tween(durationMillis = 400),
        label = "pulseAlpha"
    )
    val shimmerProgress = if (isRefreshing) {
        rememberInfiniteTransition(label = "cardShimmer")
            .animateFloat(
                initialValue = -1f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1500),
                    repeatMode = RepeatMode.Restart
                ),
                label = "shimmerProgress"
            ).value
    } else {
        0f
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                scaleX = cardScale
                scaleY = cardScale
                translationY = cardOffsetY.toPx()
            },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardContainerColor()
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = cardElevation)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
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
                    .padding(horizontal = 18.dp, vertical = 16.dp),
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

                    FilledTonalButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        contentPadding = PaddingValues(
                            horizontal = 10.dp,
                            vertical = 8.dp
                        )
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            text = refreshActionLabel(
                                isRefreshing = isRefreshing,
                                language = language
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top
                ) {
                    for (index in orderedQuotas.indices) {
                        val quota = orderedQuotas[index]
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

                        if (index != orderedQuotas.lastIndex) {
                            Spacer(modifier = Modifier.width(14.dp))
                        }
                    }
                }
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
            strokeWidth = 11.dp,
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

private fun resetLabel(quota: QuotaInfo, language: AppLanguage): String {
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
