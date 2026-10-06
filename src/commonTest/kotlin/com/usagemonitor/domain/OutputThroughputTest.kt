package com.usagemonitor.domain

import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.combinedThroughput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutputThroughputTest {

    @Test
    fun `tokens per second divides output by generation time`() {
        assertEquals(95.0, OutputThroughput(outputTokens = 950L, generationMillis = 10_000L).tokensPerSecond)
    }

    @Test
    fun `no measured time means no rate`() {
        assertNull(OutputThroughput(outputTokens = 950L, generationMillis = 0L).tokensPerSecond)
    }

    @Test
    fun `combined throughput is total over total, not an average of rates`() {
        val fast = OutputThroughput(outputTokens = 1_000L, generationMillis = 5_000L)
        val slow = OutputThroughput(outputTokens = 100L, generationMillis = 5_000L)

        val combined = listOf(fast, null, slow).combinedThroughput()

        assertEquals(OutputThroughput(outputTokens = 1_100L, generationMillis = 10_000L), combined)
        assertEquals(110.0, combined?.tokensPerSecond)
    }

    @Test
    fun `nothing measured combines to null`() {
        assertNull(listOf<OutputThroughput?>(null, null).combinedThroughput())
    }
}
