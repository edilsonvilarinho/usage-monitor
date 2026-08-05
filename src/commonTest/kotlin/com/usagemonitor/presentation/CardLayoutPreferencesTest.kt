package com.usagemonitor.presentation

import androidx.compose.ui.geometry.Offset
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.CardGridSlot
import com.usagemonitor.presentation.ui.moveVisibleCardToIndex
import com.usagemonitor.presentation.ui.normalizeCardOrder
import com.usagemonitor.presentation.ui.reorderVisibleCards
import com.usagemonitor.presentation.ui.resolveDropTargetIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class CardLayoutPreferencesTest {
    private val codex = UsageTargetKey.forSource(ApiSource.CODEX)
    private val anthropicA = UsageTargetKey(ApiSource.ANTHROPIC, "profile-a")
    private val anthropicB = UsageTargetKey(ApiSource.ANTHROPIC, "profile-b")
    private val minimax = UsageTargetKey.forSource(ApiSource.MINIMAX)

    @Test
    fun `normalizeCardOrder removes duplicates and appends missing targets`() {
        val normalized = normalizeCardOrder(
            storedOrder = listOf(codex, anthropicA, codex),
            availableTargets = listOf(anthropicA, anthropicB, minimax, codex)
        )

        assertEquals(listOf(codex, anthropicA, anthropicB, minimax), normalized)
    }

    @Test
    fun `reorderVisibleCards moves visible cards without shifting hidden positions`() {
        val reordered = reorderVisibleCards(
            currentOrder = listOf(anthropicA, minimax, codex),
            visibleTargets = setOf(anthropicA, codex),
            target = codex,
            offset = -1
        )

        assertEquals(listOf(codex, minimax, anthropicA), reordered)
    }

    @Test
    fun `reorderVisibleCards ignores targets that are not visible`() {
        val order = listOf(anthropicA, minimax, codex)
        val reordered = reorderVisibleCards(
            currentOrder = order,
            visibleTargets = setOf(anthropicA, codex),
            target = minimax,
            offset = 1
        )

        assertEquals(order, reordered)
    }

    @Test
    fun `moveVisibleCardToIndex distinguishes two Anthropic profiles`() {
        val reordered = moveVisibleCardToIndex(
            currentOrder = listOf(anthropicA, anthropicB, codex),
            visibleTargets = setOf(anthropicA, anthropicB, codex),
            target = anthropicA,
            targetIndex = 2
        )

        assertEquals(listOf(anthropicB, codex, anthropicA), reordered)
    }

    @Test
    fun `resolveDropTargetIndex maps top right drop to second slot`() {
        val bounds = mapOf(
            codex to CardGridSlot(codex, 0f, 0f, 100, 80),
            anthropicA to CardGridSlot(anthropicA, 120f, 0f, 100, 80),
            minimax to CardGridSlot(minimax, 0f, 96f, 100, 80)
        )
        val targetIndex = resolveDropTargetIndex(
            orderedTargets = listOf(codex, anthropicA, minimax),
            boundsByTarget = bounds,
            draggedTarget = minimax,
            draggedCenter = Offset(170f, 40f)
        )

        assertEquals(1, targetIndex)
    }

    @Test
    fun `resolveDropTargetIndex maps top left drop to first slot`() {
        val bounds = mapOf(
            codex to CardGridSlot(codex, 0f, 0f, 100, 80),
            anthropicA to CardGridSlot(anthropicA, 120f, 0f, 100, 80),
            minimax to CardGridSlot(minimax, 0f, 96f, 100, 80)
        )
        val targetIndex = resolveDropTargetIndex(
            orderedTargets = listOf(codex, anthropicA, minimax),
            boundsByTarget = bounds,
            draggedTarget = minimax,
            draggedCenter = Offset(40f, 40f)
        )

        assertEquals(0, targetIndex)
    }
}
