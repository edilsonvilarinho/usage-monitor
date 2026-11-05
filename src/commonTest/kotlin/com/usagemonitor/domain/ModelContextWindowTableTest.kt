package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ModelContextWindowTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelContextWindowTableTest {

    @Test
    fun `5-5 and 5-1 models have a one million token window`() {
        assertEquals(1_000_000L, ModelContextWindowTable.forModel("claude-opus-5-5"))
        assertEquals(1_000_000L, ModelContextWindowTable.forModel("claude-sonnet-5-5"))
        assertEquals(1_000_000L, ModelContextWindowTable.forModel("claude-fable-5-1"))
        assertEquals(1_000_000L, ModelContextWindowTable.forModel("claude-mythos-5-1"))
        assertEquals(1_000_000L, ModelContextWindowTable.forModel("claude-haiku-5-5"))
    }

    @Test
    fun `date suffix keeps the window of its 5-5 model`() {
        assertEquals(1_000_000L, ModelContextWindowTable.forModel("claude-opus-5-5-20270101"))
    }

    @Test
    fun `later minor version has no window`() {
        assertNull(ModelContextWindowTable.forModel("claude-opus-5-6"))
    }

    @Test
    fun `haiku 4-5 keeps its 200k window`() {
        assertEquals(200_000L, ModelContextWindowTable.forModel("claude-haiku-4-5-20251001"))
    }
}
