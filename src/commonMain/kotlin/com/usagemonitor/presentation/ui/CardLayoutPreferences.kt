package com.usagemonitor.presentation.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey

internal fun normalizeCardOrder(
    storedOrder: List<UsageTargetKey>,
    availableTargets: Collection<UsageTargetKey> = ApiSource.entries.map(UsageTargetKey::forSource)
): List<UsageTargetKey> {
    val normalized = mutableListOf<UsageTargetKey>()

    storedOrder.forEach { target ->
        if (target in availableTargets && target !in normalized) {
            normalized += target
        }
    }

    availableTargets.forEach { target ->
        if (target !in normalized) {
            normalized += target
        }
    }

    return normalized
}

internal data class CardGridSlot(
    val target: UsageTargetKey,
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
    orderedTargets: List<UsageTargetKey>,
    boundsByTarget: Map<UsageTargetKey, CardGridSlot>,
    draggedTarget: UsageTargetKey,
    draggedCenter: Offset
): Int? {
    if (orderedTargets.isEmpty()) {
        return null
    }

    val slots = orderedTargets.mapNotNull { target -> boundsByTarget[target] }
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

    val sourceIndex = orderedTargets.indexOf(draggedTarget)
    if (sourceIndex == -1) {
        return null
    }

    return (targetRow * columns + targetColumn)
        .coerceIn(0, orderedTargets.lastIndex)
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
    currentOrder: List<UsageTargetKey>,
    visibleTargets: Set<UsageTargetKey>,
    target: UsageTargetKey,
    offset: Int
): List<UsageTargetKey> {
    val normalizedOrder = normalizeCardOrder(currentOrder, currentOrder)
    val visibleOrder = normalizedOrder.filter { orderedTarget -> orderedTarget in visibleTargets }

    if (visibleOrder.isEmpty()) {
        return normalizedOrder
    }

    val sourceIndex = visibleOrder.indexOf(target)
    if (sourceIndex == -1 || offset == 0) {
        return normalizedOrder
    }

    return moveVisibleCardToIndex(
        currentOrder = normalizedOrder,
        visibleTargets = visibleTargets,
        target = target,
        targetIndex = sourceIndex + offset
    )
}

internal fun moveVisibleCardToIndex(
    currentOrder: List<UsageTargetKey>,
    visibleTargets: Set<UsageTargetKey>,
    target: UsageTargetKey,
    targetIndex: Int
): List<UsageTargetKey> {
    val normalizedOrder = normalizeCardOrder(currentOrder, currentOrder)
    val visibleOrder = normalizedOrder.filter { orderedTarget -> orderedTarget in visibleTargets }

    if (visibleOrder.isEmpty()) {
        return normalizedOrder
    }

    val sourceIndex = visibleOrder.indexOf(target)
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
    return normalizedOrder.map { orderedTarget ->
        if (orderedTarget in visibleTargets) {
            reorderedVisible[visibleCursor++]
        } else {
            orderedTarget
        }
    }
}
