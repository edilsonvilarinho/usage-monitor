package com.usagemonitor

import com.usagemonitor.presentation.ui.modalOpenedBreadcrumb
import com.usagemonitor.presentation.ui.shouldAnimateModalWindow
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppDialogWindowTest {

    @Test
    fun `the live app animates modal windows where the platform can fade them`() {
        assertTrue(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = true))
        assertTrue(shouldAnimateModalWindow(AppMotionPolicy.Static, opacitySupported = true))
    }

    /** "Reduzir animações" abre e fecha na hora, mesmo onde daria para esmaecer. */
    @Test
    fun `reduced motion opens modal windows at once`() {
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Reduced, opacitySupported = true))
    }

    /**
     * Sem translucidez, animar só a escala numa janela opaca é o salto que o host
     * existe para eliminar: melhor não animar nada.
     */
    @Test
    fun `without window translucency modal windows open at once`() {
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = false))
    }

    /** O nome é fixo: o título pode trazer o apelido do perfil, e a trilha vira issue pública. */
    @Test
    fun `the breadcrumb carries the fixed name, the time and whether it was the first open`() {
        assertEquals(
            "janela Configurações pintada em 212 ms (primeira abertura)",
            modalOpenedBreadcrumb("Configurações", elapsedMillis = 212, firstOpen = true)
        )
        assertEquals(
            "janela histórico pintada em 34 ms (reabertura)",
            modalOpenedBreadcrumb("histórico", elapsedMillis = 34, firstOpen = false)
        )
    }
}
