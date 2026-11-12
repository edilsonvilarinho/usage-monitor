package com.usagemonitor

import com.usagemonitor.presentation.ui.MODAL_IDLE_RELEASE_MILLIS
import com.usagemonitor.presentation.ui.ModalRequestAction
import com.usagemonitor.presentation.ui.modalPrewarmRequested
import com.usagemonitor.presentation.ui.modalRequestAction
import com.usagemonitor.presentation.ui.shouldCreateModalWindow
import com.usagemonitor.presentation.ui.shouldReleaseHiddenModalWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** O que um pedido faz com a janela modal, e se o pré-aquecimento acontece (sem janela). */
class ModalRequestActionTest {

    @Test
    fun `a request to show a window already on screen only brings it forward`() {
        assertEquals(ModalRequestAction.REFOCUS, modalRequestAction(visible = true, onScreen = true))
    }

    @Test
    fun `a request to show a hidden window opens it`() {
        assertEquals(ModalRequestAction.OPEN, modalRequestAction(visible = true, onScreen = false))
    }

    @Test
    fun `a request to hide a window on screen closes it`() {
        assertEquals(ModalRequestAction.CLOSE, modalRequestAction(visible = false, onScreen = true))
    }

    @Test
    fun `a request to hide a window already hidden does nothing`() {
        assertEquals(ModalRequestAction.IGNORE, modalRequestAction(visible = false, onScreen = false))
    }

    @Test
    fun `prewarm needs the request, the platform and a settled start`() {
        assertTrue(modalPrewarmRequested(requested = true, allowed = true, ready = true))
        assertFalse(modalPrewarmRequested(requested = false, allowed = true, ready = true))
        assertFalse(modalPrewarmRequested(requested = true, allowed = false, ready = true))
        assertFalse(modalPrewarmRequested(requested = true, allowed = true, ready = false))
    }

    @Test
    fun `a window hidden and off screen after being opened is released`() {
        assertTrue(shouldReleaseHiddenModalWindow(visible = false, onScreen = false, opens = 1))
    }

    @Test
    fun `a window requested to be visible is never released`() {
        assertFalse(shouldReleaseHiddenModalWindow(visible = true, onScreen = false, opens = 1))
    }

    @Test
    fun `a window still leaving the screen is not released yet`() {
        assertFalse(shouldReleaseHiddenModalWindow(visible = false, onScreen = true, opens = 1))
    }

    @Test
    fun `a prewarmed window that was never opened is not released`() {
        assertFalse(shouldReleaseHiddenModalWindow(visible = false, onScreen = false, opens = 0))
    }

    @Test
    fun `a released window is not recreated only because prewarm is still requested`() {
        assertFalse(shouldCreateModalWindow(visible = false, prewarm = true, createdBefore = true))
    }

    @Test
    fun `an open request recreates a released window`() {
        assertTrue(shouldCreateModalWindow(visible = true, prewarm = false, createdBefore = true))
    }

    @Test
    fun `prewarm creates a window that was never created`() {
        assertTrue(shouldCreateModalWindow(visible = false, prewarm = true, createdBefore = false))
    }

    @Test
    fun `without a request or prewarm no window is created`() {
        assertFalse(shouldCreateModalWindow(visible = false, prewarm = false, createdBefore = false))
    }

    @Test
    fun `the idle release waits sixty seconds`() {
        assertEquals(60_000L, MODAL_IDLE_RELEASE_MILLIS)
    }
}
