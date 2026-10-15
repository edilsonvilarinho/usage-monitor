package com.usagemonitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Por onde uma falha não tratada chega ao handler default da JVM (issue #416, item L2).
 *
 * O KDoc do [CrashHandler] afirmava que exceção do laço da AWT e de coroutine
 * com `SupervisorJob` **não** chegava a ele. Nenhum escopo do app instala
 * `CoroutineExceptionHandler`, então o caminho real decide se uma falha
 * não-fatal vira marcador de queda. Esta sonda mede em vez de deduzir.
 */
class UncaughtExceptionRoutingTest {

    @Test
    fun `a failing launch in a supervisor scope reaches the default handler`() {
        val received = captureDefaultHandler { onFailure ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch { throw IllegalStateException("sonda-coroutine") }
            onFailure.await(5, TimeUnit.SECONDS)
            scope.cancel()
        }

        assertEquals("sonda-coroutine", received?.message)
    }

    @Test
    fun `an exception thrown on the AWT event thread reaches the default handler`() {
        val received = captureDefaultHandler { onFailure ->
            EventQueue.invokeLater { throw IllegalStateException("sonda-edt") }
            onFailure.await(5, TimeUnit.SECONDS)
        }

        assertEquals("sonda-edt", received?.message)
    }

    @Test
    fun `the event thread keeps dispatching after an uncaught exception`() {
        captureDefaultHandler { onFailure ->
            EventQueue.invokeLater { throw IllegalStateException("sonda-edt-segue") }
            onFailure.await(5, TimeUnit.SECONDS)
        }
        val served = CountDownLatch(1)
        EventQueue.invokeLater { served.countDown() }

        assertTrue(served.await(5, TimeUnit.SECONDS))
    }

    private fun captureDefaultHandler(block: (CountDownLatch) -> Unit): Throwable? {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val received = AtomicReference<Throwable?>(null)
        val latch = CountDownLatch(1)
        Thread.setDefaultUncaughtExceptionHandler { _, error ->
            received.compareAndSet(null, error)
            latch.countDown()
        }
        try {
            block(latch)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        return received.get()
    }
}
