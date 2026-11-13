package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.CardAxisSpan
import com.usagemonitor.presentation.ui.dragShiftOffsets
import com.usagemonitor.presentation.ui.dragTargetIndex
import com.usagemonitor.presentation.ui.moveVisibleCardToIndex
import kotlin.test.Test
import kotlin.test.assertEquals

class CardDragRuleTest {
    private val a = UsageTargetKey(ApiSource.ANTHROPIC, "a")
    private val b = UsageTargetKey(ApiSource.ANTHROPIC, "b")
    private val c = UsageTargetKey(ApiSource.CODEX)
    private val hidden = UsageTargetKey(ApiSource.MINIMAX)

    /** Três itens de 100 px lado a lado: meios em 50, 150 e 250. */
    private val spans = listOf(
        CardAxisSpan(0f, 100f),
        CardAxisSpan(100f, 200f),
        CardAxisSpan(200f, 300f)
    )

    @Test
    fun `dragTargetIndex keeps the slot until the pointer passes the neighbour midpoint`() {
        assertEquals(0, dragTargetIndex(spans, draggedIndex = 0, pointer = 120f))
        assertEquals(0, dragTargetIndex(spans, draggedIndex = 0, pointer = 149f))
        assertEquals(1, dragTargetIndex(spans, draggedIndex = 0, pointer = 151f))
        assertEquals(2, dragTargetIndex(spans, draggedIndex = 0, pointer = 260f))
    }

    @Test
    fun `dragTargetIndex works in the opposite direction`() {
        assertEquals(0, dragTargetIndex(spans, draggedIndex = 2, pointer = 10f))
        assertEquals(1, dragTargetIndex(spans, draggedIndex = 2, pointer = 60f))
        assertEquals(2, dragTargetIndex(spans, draggedIndex = 2, pointer = 160f))
    }

    @Test
    fun `dragTargetIndex clamps pointers outside the row and handles an empty row`() {
        assertEquals(0, dragTargetIndex(spans, draggedIndex = 1, pointer = -500f))
        assertEquals(2, dragTargetIndex(spans, draggedIndex = 1, pointer = 9999f))
        assertEquals(0, dragTargetIndex(emptyList(), draggedIndex = 0, pointer = 10f))
    }

    @Test
    fun `dragShiftOffsets opens the vacant slot at the target and slides the neighbours into it`() {
        // A (0) vai para a vaga 2: B e C deslizam uma vaga para trás; A segue a vaga 2.
        assertEquals(listOf(200f, -100f, -100f), dragShiftOffsets(spans, draggedIndex = 0, targetIndex = 2))
        // C (2) vai para a vaga 1: A fica; B desliza uma vaga para a frente.
        assertEquals(listOf(0f, 100f, -100f), dragShiftOffsets(spans, draggedIndex = 2, targetIndex = 1))
    }

    @Test
    fun `dragShiftOffsets uses each slot start so items of different widths keep their slots`() {
        val mixed = listOf(
            CardAxisSpan(0f, 100f),
            CardAxisSpan(100f, 150f),
            CardAxisSpan(150f, 250f)
        )

        assertEquals(listOf(150f, -100f, -50f), dragShiftOffsets(mixed, draggedIndex = 0, targetIndex = 2))
    }

    @Test
    fun `dragShiftOffsets is all zeros when nothing moves`() {
        assertEquals(listOf(0f, 0f, 0f), dragShiftOffsets(spans, draggedIndex = 1, targetIndex = 1))
    }

    @Test
    fun `a drop index from dragTargetIndex reorders the visible cards and keeps the hidden one in place`() {
        val targetIndex = dragTargetIndex(
            listOf(CardAxisSpan(0f, 100f), CardAxisSpan(100f, 200f), CardAxisSpan(200f, 300f)),
            draggedIndex = 0,
            pointer = 160f
        )

        val reordered = moveVisibleCardToIndex(
            currentOrder = listOf(a, hidden, b, c),
            visibleTargets = setOf(a, b, c),
            target = a,
            targetIndex = targetIndex
        )

        assertEquals(listOf(b, hidden, a, c), reordered)
    }
}
