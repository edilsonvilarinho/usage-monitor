package com.usagemonitor.presentation.ui

import androidx.compose.ui.geometry.Offset
import com.usagemonitor.domain.entity.ApiSource
import kotlin.math.roundToInt

internal fun normalizeCardOrder(storedOrder: List<ApiSource>): List<ApiSource> {
    val normalized = mutableListOf<ApiSource>()

    storedOrder.forEach { source ->
        if (source !in normalized) {
            normalized += source
        }
    }

    ApiSource.entries.forEach { source ->
        if (source !in normalized) {
            normalized += source
        }
    }

    return normalized
}

internal data class CardGridSlot(
    val source: ApiSource,
    val left: Float,
    val top: Float,
    val width: Int,
    val height: Int
) {
    val centerX: Float
        get() = left + (width / 2f)

    val centerY: Float
        get() = top + (height / 2f)
}

internal fun resolveDropTargetIndex(
    orderedSources: List<ApiSource>,
    boundsBySource: Map<ApiSource, CardGridSlot>,
    draggedSource: ApiSource,
    draggedCenter: Offset
): Int? {
    if (orderedSources.isEmpty()) {
        return null
    }

    val slots = orderedSources.mapNotNull { source -> boundsBySource[source] }
    if (slots.isEmpty()) {
        return null
    }

    val columnAnchors = slots
        .map { slot -> slot.left.roundToInt() }
        .distinct()
        .sorted()

    val columns = columnAnchors.size.coerceAtLeast(1)
    val orderedSlots = slots.sortedWith(
        compareBy<CardGridSlot> { slot -> slot.top.roundToInt() }
            .thenBy { slot -> slot.left.roundToInt() }
    )
    val rowMidpoints = orderedSlots
        .chunked(columns)
        .map { row -> row.map { slot -> slot.centerY }.average().toFloat() }

    val columnCenters = columnAnchors.map { anchor ->
        val matchingSlots = slots.filter { slot -> slot.left.roundToInt() == anchor }
        matchingSlots.map { slot -> slot.centerX }.average().toFloat()
    }

    val targetRow = resolveBandIndex(
        value = draggedCenter.y,
        centers = rowMidpoints
    )
    val targetColumn = resolveBandIndex(
        value = draggedCenter.x,
        centers = columnCenters
    )

    val sourceIndex = orderedSources.indexOf(draggedSource)
    if (sourceIndex == -1) {
        return null
    }

    return (targetRow * columns + targetColumn)
        .coerceIn(0, orderedSources.lastIndex)
}

private fun resolveBandIndex(
    value: Float,
    centers: List<Float>
): Int {
    if (centers.isEmpty()) {
        return 0
    }

    if (centers.size == 1) {
        return 0
    }

    for (index in 0 until centers.lastIndex) {
        val boundary = (centers[index] + centers[index + 1]) / 2f
        if (value < boundary) {
            return index
        }
    }

    return centers.lastIndex
}

internal fun reorderVisibleCards(
    currentOrder: List<ApiSource>,
    visibleSources: Set<ApiSource>,
    source: ApiSource,
    offset: Int
): List<ApiSource> {
    val normalizedOrder = normalizeCardOrder(currentOrder)
    val visibleOrder = normalizedOrder.filter { orderedSource -> orderedSource in visibleSources }

    if (visibleOrder.isEmpty()) {
        return normalizedOrder
    }

    val sourceIndex = visibleOrder.indexOf(source)
    if (sourceIndex == -1 || offset == 0) {
        return normalizedOrder
    }

    return moveVisibleCardToIndex(
        currentOrder = normalizedOrder,
        visibleSources = visibleSources,
        source = source,
        targetIndex = sourceIndex + offset
    )
}

internal fun moveVisibleCardToIndex(
    currentOrder: List<ApiSource>,
    visibleSources: Set<ApiSource>,
    source: ApiSource,
    targetIndex: Int
): List<ApiSource> {
    val normalizedOrder = normalizeCardOrder(currentOrder)
    val visibleOrder = normalizedOrder.filter { orderedSource -> orderedSource in visibleSources }

    if (visibleOrder.isEmpty()) {
        return normalizedOrder
    }

    val sourceIndex = visibleOrder.indexOf(source)
    if (sourceIndex == -1) {
        return normalizedOrder
    }

    val boundedTargetIndex = targetIndex.coerceIn(0, visibleOrder.lastIndex)
    if (sourceIndex == boundedTargetIndex) {
        return normalizedOrder
    }

    val reorderedVisible = visibleOrder.toMutableList()
    val movedSource = reorderedVisible.removeAt(sourceIndex)
    reorderedVisible.add(boundedTargetIndex, movedSource)

    var visibleCursor = 0
    return normalizedOrder.map { orderedSource ->
        if (orderedSource in visibleSources) {
            reorderedVisible[visibleCursor++]
        } else {
            orderedSource
        }
    }
}
