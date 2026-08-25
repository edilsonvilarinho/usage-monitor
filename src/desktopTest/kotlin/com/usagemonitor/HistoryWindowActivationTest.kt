package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoryWindowActivationTest {

    @Test
    fun `activate window brings it to front without forcing fullscreen bounds`() {
        val window = FakeWindowActivationTarget()

        activateWindow(window)

        assertTrue(window.isVisible)
        assertTrue(window.broughtToFront)
        assertTrue(window.focusRequested)
        assertFalse(window.setBoundsCalled)
    }

    // `toFront()` sozinho so pisca o botao na barra quando o processo nao detem o
    // primeiro plano -- que e o caso de quem clica no atalho com o app ja rodando.
    @Test
    fun `activation flips always on top to defeat the foreground lock`() {
        val window = FakeWindowActivationTarget()

        activateWindow(window)

        assertTrue(window.wasAlwaysOnTopWhenBroughtToFront)
    }

    @Test
    fun `always on top goes back to false after the activation`() {
        val window = FakeWindowActivationTarget()

        activateWindow(window)

        assertFalse(window.isAlwaysOnTop)
    }

    // A janela principal tem `alwaysOnTop` como preferencia do usuario: apagar a
    // escolha dele ao ativar a janela seria trocar um defeito por outro.
    @Test
    fun `a window the user pinned on top stays pinned`() {
        val window = FakeWindowActivationTarget(alwaysOnTop = true)

        activateWindow(window)

        assertTrue(window.isAlwaysOnTop)
        assertTrue(window.broughtToFront)
    }

    private class FakeWindowActivationTarget(
        alwaysOnTop: Boolean = false
    ) : WindowActivationTarget {
        override var isVisible: Boolean = false
        override var isAlwaysOnTop: Boolean = alwaysOnTop
        var broughtToFront = false
        var focusRequested = false
        var setBoundsCalled = false
        var wasAlwaysOnTopWhenBroughtToFront = false

        override fun toFront() {
            broughtToFront = true
            wasAlwaysOnTopWhenBroughtToFront = isAlwaysOnTop
        }

        override fun requestFocus() {
            focusRequested = true
        }

        override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
            setBoundsCalled = true
            assertEquals(0, x)
            assertEquals(0, y)
            assertEquals(0, width)
            assertEquals(0, height)
        }
    }
}
