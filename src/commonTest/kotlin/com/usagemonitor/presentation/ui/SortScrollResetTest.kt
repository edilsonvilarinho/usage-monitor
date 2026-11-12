package com.usagemonitor.presentation.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SortScrollResetTest {

    @Test
    fun `first composition never scrolls`() {
        assertNull(sortScrollTarget(hasComposed = false, previous = "a", current = "b"))
    }

    @Test
    fun `changed sort key scrolls to the first item`() {
        assertEquals(0, sortScrollTarget(hasComposed = true, previous = "a", current = "b"))
    }

    @Test
    fun `same sort key does not scroll`() {
        assertNull(sortScrollTarget(hasComposed = true, previous = "a", current = "a"))
    }
}
