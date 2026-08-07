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

    private class FakeWindowActivationTarget : WindowActivationTarget {
        override var isVisible: Boolean = false
        var broughtToFront = false
        var focusRequested = false
        var setBoundsCalled = false

        override fun toFront() {
            broughtToFront = true
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
