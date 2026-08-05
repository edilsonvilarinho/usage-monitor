package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.CardGridSlot
import com.usagemonitor.presentation.ui.resolveDropTargetIndex

// Threshold de largura (em dp) acima do qual o grid alterna de 1 para 2 colunas.
private val CompactColumnsThreshold = 720.dp

// Espaçamento horizontal e vertical entre os cards.
private val CardSpacing = 16.dp

/**
 * Grid responsivo de cards de uso de API.
 *
 * Stateless quanto à ordem dos cards (recebida em `items`); mantém somente
 * estado local de drag/drop em memória de composição. Reordenamentos são
 * comunicados via `onMoveCardToIndex`.
 */
@Composable
internal fun ResponsiveDashboardCardGrid(
    items: List<ApiUsageStats>,
    refreshingTargets: Set<UsageTargetKey>,
    minimizedCards: Set<UsageTargetKey>,
    language: AppLanguage,
    onRefreshCard: (UsageTargetKey) -> Unit,
    onMoveCardToIndex: (UsageTargetKey, Int) -> Unit,
    onToggleCardMinimized: (UsageTargetKey) -> Unit,
    onOpenHistoryCard: (ApiSource, UsageAccountKey?) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { CardSpacing.roundToPx() }
    val compactThresholdPx = with(density) { CompactColumnsThreshold.roundToPx() }
    val itemBounds = remember { mutableStateMapOf<UsageTargetKey, CardGridBounds>() }
    var dragState by remember { mutableStateOf<CardDragState?>(null) }
    var dropTargetIndex by remember { mutableStateOf<Int?>(null) }

    Layout(
        modifier = modifier,
        content = {
            items.forEachIndexed { index, stats ->
                val isBeingDragged = dragState?.target == stats.targetKey
                val isDropTarget = dropTargetIndex == index && !isBeingDragged
                val translation = if (isBeingDragged) {
                    dragState?.dragOffset ?: Offset.Zero
                } else {
                    Offset.Zero
                }

                key(stats.targetKey) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(
                                when {
                                    isBeingDragged -> 3f
                                    isDropTarget -> 1f
                                    else -> 0f
                                }
                            )
                            .onGloballyPositioned { coordinates ->
                                itemBounds[stats.targetKey] = CardGridBounds(
                                    topLeft = coordinates.positionInParent(),
                                    width = coordinates.size.width,
                                    height = coordinates.size.height
                                )
                            }
                            .graphicsLayer {
                                translationX = translation.x
                                translationY = translation.y
                            }
                    ) {
                        ApiUsageCard(
                            source = stats.source,
                            apiName = stats.profileLabel?.let { label -> "${stats.apiName} — $label" } ?: stats.apiName,
                            quotas = stats.quotas,
                            accountContext = stats.accountContext,
                            notices = stats.notices,
                            showUsageDetails = stats.source != ApiSource.ANTHROPIC,
                            isRefreshing = stats.targetKey in refreshingTargets,
                            isMinimized = stats.targetKey in minimizedCards,
                            isBeingDragged = isBeingDragged,
                            isDragTarget = isDropTarget,
                            language = language,
                            animationDelayMillis = index * 90,
                            onRefresh = { onRefreshCard(stats.targetKey) },
                            onOpenHistory = { onOpenHistoryCard(stats.source, stats.accountContext?.key) },
                            onToggleMinimized = { onToggleCardMinimized(stats.targetKey) },
                            onDragStart = {
                                dragState = CardDragState(target = stats.targetKey)
                                dropTargetIndex = items.indexOfFirst { item -> item.targetKey == stats.targetKey }
                            },
                            onDrag = { dragAmount ->
                                val activeDrag = dragState
                                if (activeDrag != null && activeDrag.target == stats.targetKey) {
                                    val updatedDrag = activeDrag.copy(
                                        dragOffset = activeDrag.dragOffset + dragAmount
                                    )
                                    dragState = updatedDrag

                                    val draggedBounds = itemBounds[stats.targetKey]
                                    if (draggedBounds == null) {
                                        dropTargetIndex = null
                                    } else {
                                        val draggedCenter = draggedBounds.center + updatedDrag.dragOffset
                                        dropTargetIndex = resolveDropTargetIndex(
                                            orderedTargets = items.map { item -> item.targetKey },
                                            boundsByTarget = itemBounds.mapValues { (target, bounds) ->
                                                CardGridSlot(
                                                    target = target,
                                                    left = bounds.topLeft.x,
                                                    top = bounds.topLeft.y,
                                                    width = bounds.width,
                                                    height = bounds.height
                                                )
                                            },
                                            draggedTarget = stats.targetKey,
                                            draggedCenter = draggedCenter
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                val activeDrag = dragState
                                val targetIndex = dropTargetIndex
                                dragState = null
                                dropTargetIndex = null

                                if (activeDrag != null && activeDrag.target == stats.targetKey && targetIndex != null) {
                                    onMoveCardToIndex(stats.targetKey, targetIndex)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        if (measurables.isEmpty()) {
            return@Layout layout(width = constraints.maxWidth, height = 0) {}
        }

        val columns = if (constraints.maxWidth < compactThresholdPx) 1 else 2
        val totalSpacing = spacingPx * (columns - 1)
        val itemWidth = ((constraints.maxWidth - totalSpacing).coerceAtLeast(0)) / columns
        val defaultChildConstraints = constraints.copy(
            minWidth = itemWidth,
            maxWidth = itemWidth,
            minHeight = 0
        )
        val fullRowConstraints = constraints.copy(
            minWidth = constraints.maxWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0
        )
        val rows = buildList {
            var index = 0

            while (index < measurables.size) {
                val remainingItems = measurables.size - index
                val isTrailingSingleCardRow = columns > 1 && remainingItems == 1
                val rowItemCount = if (isTrailingSingleCardRow) 1 else minOf(columns, remainingItems)
                val rowConstraints = if (isTrailingSingleCardRow) {
                    fullRowConstraints
                } else {
                    defaultChildConstraints
                }
                val rowPlaceables = List(rowItemCount) { rowIndex ->
                    measurables[index + rowIndex].measure(rowConstraints)
                }

                add(CardGridRow(placeables = rowPlaceables))
                index += rowItemCount
            }
        }
        val rowHeights = rows.map { row -> row.height }
        val layoutHeight = rowHeights.sum() + spacingPx * (rowHeights.size - 1).coerceAtLeast(0)

        layout(width = constraints.maxWidth, height = layoutHeight) {
            var yPosition = 0

            rows.forEach { row ->
                row.placeables.forEachIndexed { columnIndex, placeable ->
                    val xPosition = if (row.placeables.size == 1 && columns > 1) {
                        0
                    } else {
                        columnIndex * (itemWidth + spacingPx)
                    }

                    placeable.placeRelative(
                        x = xPosition,
                        y = yPosition
                    )
                }

                yPosition += row.height + spacingPx
            }
        }
    }
}

private data class CardDragState(
    val target: UsageTargetKey,
    val dragOffset: Offset = Offset.Zero
)

private data class CardGridBounds(
    val topLeft: Offset,
    val width: Int,
    val height: Int
) {
    val center: Offset
        get() = Offset(
            x = topLeft.x + width / 2f,
            y = topLeft.y + height / 2f
        )
}

private data class CardGridRow(
    val placeables: List<Placeable>
) {
    val height: Int
        get() = placeables.maxOf { placeable -> placeable.height }
}
