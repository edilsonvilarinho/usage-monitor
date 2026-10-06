package com.usagemonitor

import java.awt.EventQueue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwtWindowDispatchTest {
    @Test
    fun `native operation waits until current paint callback returns`() {
        var painting = false
        var called = false
        EventQueue.invokeAndWait {
            painting = true
            postAwtWindowOperation {
                assertTrue(EventQueue.isDispatchThread())
                assertFalse(painting, "Reentry into ignoringRedrawRequests is not allowed")
                called = true
            }
            assertFalse(called)
            painting = false
        }
        EventQueue.invokeAndWait { }
        assertTrue(called)
    }

    @Test
    fun `awaiting next event turn is cancellable before activation`() = runBlocking {
        var activations = 0
        EventQueue.invokeAndWait {
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                awaitAwtEventTurn()
                activations += 1
            }
            job.cancel()
        }
        EventQueue.invokeAndWait { }
        assertEquals(0, activations)
    }
}
