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
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Por onde uma falha não tratada chega ao handler default da JVM (issue #416, item L2).
 *
 * O KDoc do [CrashHandler] afirmava que exceção do laço da AWT e de coroutine
 * com `SupervisorJob` **não** chegava a ele. Nenhum escopo do app instala
 * `CoroutineExceptionHandler`, então o caminho real decide se uma falha
 * não-fatal vira marcador de queda. Esta sonda mede em vez de deduzir, e prende
 * a classificação de origem ([uncaughtOriginOf]) ao que a JVM e o kotlinx de
 * fato entregam.
 */
class UncaughtExceptionRoutingTest {

    @Test
    fun `a failing launch in a supervisor scope reaches the default handler`() {
        val (failedThread, received) = captureDefaultHandler { onFailure ->
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope.launch { throw IllegalStateException("sonda-coroutine") }
            onFailure.await(5, TimeUnit.SECONDS)
            scope.cancel()
        }

        assertEquals("sonda-coroutine", received?.message)
        // O último recurso do kotlinx.coroutines anexa o contexto como suprimida;
        // é por ela que o `CrashHandler` sabe que a falha veio de uma coroutine.
        assertEquals(UncaughtOrigin.COROUTINE, uncaughtOriginOf(failedThread!!, received!!))
    }

    @Test
    fun `an exception thrown on the AWT event thread reaches the default handler`() {
        val (failedThread, received) = captureDefaultHandler { onFailure ->
            EventQueue.invokeLater { throw IllegalStateException("sonda-edt") }
            onFailure.await(5, TimeUnit.SECONDS)
        }

        assertEquals("sonda-edt", received?.message)
        assertEquals(UncaughtOrigin.EDT, uncaughtOriginOf(failedThread!!, received!!))
    }

    @Test
    fun `an exception that kills a plain thread is classified as a thread failure`() {
        val (failedThread, received) = captureDefaultHandler { onFailure ->
            thread(name = "sonda-thread") { throw IllegalStateException("sonda-thread") }
            onFailure.await(5, TimeUnit.SECONDS)
        }

        assertEquals(UncaughtOrigin.THREAD, uncaughtOriginOf(failedThread!!, received!!))
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

    private fun captureDefaultHandler(block: (CountDownLatch) -> Unit): Pair<Thread?, Throwable?> {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val received = AtomicReference<Pair<Thread, Throwable>?>(null)
        val latch = CountDownLatch(1)
        Thread.setDefaultUncaughtExceptionHandler { failedThread, error ->
            received.compareAndSet(null, failedThread to error)
            latch.countDown()
        }
        try {
            block(latch)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
        val captured = received.get()
        return captured?.first to captured?.second
    }
}
