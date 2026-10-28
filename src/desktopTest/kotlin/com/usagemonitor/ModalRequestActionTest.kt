package com.usagemonitor

import com.usagemonitor.presentation.ui.ModalRequestAction
import com.usagemonitor.presentation.ui.modalPrewarmRequested
import com.usagemonitor.presentation.ui.modalRequestAction
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
}
