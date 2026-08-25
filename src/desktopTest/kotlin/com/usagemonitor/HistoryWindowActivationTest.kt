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

    // Medido: com o sinalizador ja ligado, atribuir `true` de novo nao reordena
    // nada e a janela fica atras da que detem o primeiro plano. Por isso o ciclo
    // completo, tambem quando ela ja estava presa no topo.
    @Test
    fun `the always on top flag is cycled even when it was already on`() {
        val window = FakeWindowActivationTarget(alwaysOnTop = true)

        activateWindow(window)

        assertEquals(listOf(false, true, true), window.alwaysOnTopWrites)
    }

    @Test
    fun `the always on top flag is cycled when it was off`() {
        val window = FakeWindowActivationTarget(alwaysOnTop = false)

        activateWindow(window)

        assertEquals(listOf(false, true, false), window.alwaysOnTopWrites)
    }

    private class FakeWindowActivationTarget(
        alwaysOnTop: Boolean = false
    ) : WindowActivationTarget {
        override var isVisible: Boolean = false
        val alwaysOnTopWrites = mutableListOf<Boolean>()
        override var isAlwaysOnTop: Boolean = alwaysOnTop
            set(value) {
                alwaysOnTopWrites += value
                field = value
            }
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
