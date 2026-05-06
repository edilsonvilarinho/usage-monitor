package com.usagemonitor.presentation

import androidx.compose.ui.geometry.Offset
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.ui.CardGridSlot
import com.usagemonitor.presentation.ui.moveVisibleCardToIndex
import com.usagemonitor.presentation.ui.normalizeCardOrder
import com.usagemonitor.presentation.ui.reorderVisibleCards
import com.usagemonitor.presentation.ui.resolveDropTargetIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class CardLayoutPreferencesTest {

    @Test
    fun `normalizeCardOrder removes duplicates and appends missing APIs`() {
        val normalized = normalizeCardOrder(
            listOf(
                ApiSource.CODEX,
                ApiSource.ANTHROPIC,
                ApiSource.CODEX
            )
        )

        assertEquals(
            listOf(
                ApiSource.CODEX,
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX,
                ApiSource.DEEPSEEK
            ),
            normalized
        )
    }

    @Test
    fun `reorderVisibleCards moves visible cards without shifting hidden positions`() {
        val reordered = reorderVisibleCards(
            currentOrder = listOf(
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX,
                ApiSource.CODEX
            ),
            visibleSources = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX),
            source = ApiSource.CODEX,
            offset = -1
        )

        assertEquals(
            listOf(
                ApiSource.CODEX,
                ApiSource.MINIMAX,
                ApiSource.ANTHROPIC,
                ApiSource.DEEPSEEK
            ),
            reordered
        )
    }

    @Test
    fun `reorderVisibleCards ignores sources that are not visible`() {
        val reordered = reorderVisibleCards(
            currentOrder = listOf(
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX,
                ApiSource.CODEX
            ),
            visibleSources = setOf(ApiSource.ANTHROPIC, ApiSource.CODEX),
            source = ApiSource.MINIMAX,
            offset = 1
        )

        assertEquals(
            listOf(
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX,
                ApiSource.CODEX,
                ApiSource.DEEPSEEK
            ),
            reordered
        )
    }

    @Test
    fun `moveVisibleCardToIndex inserts dragged card at dropped slot`() {
        val reordered = moveVisibleCardToIndex(
            currentOrder = listOf(
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX,
                ApiSource.CODEX
            ),
            visibleSources = setOf(ApiSource.ANTHROPIC, ApiSource.MINIMAX, ApiSource.CODEX),
            source = ApiSource.ANTHROPIC,
            targetIndex = 2
        )

        assertEquals(
            listOf(
                ApiSource.MINIMAX,
                ApiSource.CODEX,
                ApiSource.ANTHROPIC,
                ApiSource.DEEPSEEK
            ),
            reordered
        )
    }

    @Test
    fun `resolveDropTargetIndex maps a top right drop to the second slot`() {
        val targetIndex = resolveDropTargetIndex(
            orderedSources = listOf(
                ApiSource.CODEX,
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX
            ),
            boundsBySource = mapOf(
                ApiSource.CODEX to CardGridSlot(
                    source = ApiSource.CODEX,
                    left = 0f,
                    top = 0f,
                    width = 100,
                    height = 80
                ),
                ApiSource.ANTHROPIC to CardGridSlot(
                    source = ApiSource.ANTHROPIC,
                    left = 120f,
                    top = 0f,
                    width = 100,
                    height = 80
                ),
                ApiSource.MINIMAX to CardGridSlot(
                    source = ApiSource.MINIMAX,
                    left = 0f,
                    top = 96f,
                    width = 100,
                    height = 80
                )
            ),
            draggedSource = ApiSource.MINIMAX,
            draggedCenter = Offset(170f, 40f)
        )

        assertEquals(1, targetIndex)
    }

    @Test
    fun `resolveDropTargetIndex maps a top left drop to the first slot`() {
        val targetIndex = resolveDropTargetIndex(
            orderedSources = listOf(
                ApiSource.CODEX,
                ApiSource.ANTHROPIC,
                ApiSource.MINIMAX
            ),
            boundsBySource = mapOf(
                ApiSource.CODEX to CardGridSlot(
                    source = ApiSource.CODEX,
                    left = 0f,
                    top = 0f,
                    width = 100,
                    height = 80
                ),
                ApiSource.ANTHROPIC to CardGridSlot(
                    source = ApiSource.ANTHROPIC,
                    left = 120f,
                    top = 0f,
                    width = 100,
                    height = 80
                ),
                ApiSource.MINIMAX to CardGridSlot(
                    source = ApiSource.MINIMAX,
                    left = 0f,
                    top = 96f,
                    width = 100,
                    height = 80
                )
            ),
            draggedSource = ApiSource.MINIMAX,
            draggedCenter = Offset(40f, 40f)
        )

        assertEquals(0, targetIndex)
    }
}
