package com.usagemonitor

import com.usagemonitor.AutoStartManager.Platform
import com.usagemonitor.presentation.ui.preloadWindowCloak
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Carga do cloak fora da EDT: a função pura decide pela plataforma e pelo despacho. */
class WindowCloakPreloadTest {

    @Test
    fun `preload does not call warmUp nor dispatch outside Windows`() {
        val dispatcher = RecordingDispatcher()
        var calls = 0
        runBlocking { preloadWindowCloak(Platform.LINUX, dispatcher) { calls++ } }
        assertEquals(0, calls, "warmUp fora do Windows")
        assertEquals(0, dispatcher.dispatches, "despachos fora do Windows")
    }

    @Test
    fun `preload calls warmUp once, through the given dispatcher, on Windows`() {
        val dispatcher = RecordingDispatcher()
        var calls = 0
        runBlocking { preloadWindowCloak(Platform.WINDOWS, dispatcher) { calls++ } }
        assertEquals(1, calls, "chamadas de warmUp no Windows")
        assertEquals(1, dispatcher.dispatches, "despachos para o dispatcher dado")
    }

    @Test
    fun `a missing native library does not break the preload`() {
        var calls = 0
        runBlocking {
            preloadWindowCloak(Platform.WINDOWS, RecordingDispatcher()) {
                calls++
                throw NoClassDefFoundError("dwmapi")
            }
        }
        assertEquals(1, calls, "warmUp chamado antes do erro de ligação")
    }

    @Test
    fun `a RuntimeException from warmUp does not break the preload`() {
        var calls = 0
        runBlocking {
            preloadWindowCloak(Platform.WINDOWS, RecordingDispatcher()) {
                calls++
                throw IllegalStateException("janela sem HWND")
            }
        }
        assertEquals(1, calls, "warmUp chamado antes da RuntimeException")
    }

    @Test
    fun `cancellation is rethrown by the preload`() {
        assertFailsWith<CancellationException> {
            runBlocking {
                preloadWindowCloak(Platform.WINDOWS, RecordingDispatcher()) {
                    throw CancellationException("cancelado")
                }
            }
        }
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatches++
            block.run()
        }
    }
}
