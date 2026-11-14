package com.usagemonitor.data

import com.usagemonitor.data.repository.suspendRunCatching
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SuspendRunCatchingTest {

    @Test
    fun `wraps the success value`() {
        assertEquals(42, suspendRunCatching { 42 }.getOrNull())
    }

    @Test
    fun `captures an ordinary failure as Result failure`() {
        val failure = IllegalStateException("falha comum")

        val result = suspendRunCatching<Int> { throw failure }

        assertTrue(result.isFailure)
        assertEquals(failure, result.exceptionOrNull())
    }

    @Test
    fun `rethrows CancellationException instead of wrapping it`() {
        assertFailsWith<CancellationException> { suspendRunCatching<Int> { throw CancellationException("cancelado") } }
    }
}
