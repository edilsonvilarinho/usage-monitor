package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.presentation.ui.components.sessionPulseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionPulseFrameTest {

    @Test
    fun `without severities there is nothing to draw`() {
        assertNull(sessionPulseFrame(phase = 0.5f, severities = emptyList()))
    }

    @Test
    fun `a single severity keeps its color across the whole cycle`() {
        val severities = listOf(CliSessionHealth.ATTENTION)

        listOf(0f, 0.25f, 0.5f, 0.75f, 0.99f).forEach { phase ->
            assertEquals(CliSessionHealth.ATTENTION, sessionPulseFrame(phase, severities)?.health)
        }
    }

    @Test
    fun `the alpha breathes from the floor to full and back`() {
        val severities = listOf(CliSessionHealth.SATURATED)

        val start = sessionPulseFrame(0f, severities)!!.alpha
        val middle = sessionPulseFrame(0.5f, severities)!!.alpha
        val end = sessionPulseFrame(0.999f, severities)!!.alpha

        assertEquals(1.0f, middle)
        assertTrue(start < middle)
        assertTrue(end < middle)
        // Não chega a zero: o pisca é suave, não intermitente.
        assertTrue(start > 0f)
    }

    @Test
    fun `two severities alternate at the half of the cycle`() {
        val severities = listOf(CliSessionHealth.ATTENTION, CliSessionHealth.SATURATED)

        assertEquals(CliSessionHealth.ATTENTION, sessionPulseFrame(0.25f, severities)?.health)
        assertEquals(CliSessionHealth.SATURATED, sessionPulseFrame(0.75f, severities)?.health)
    }

    /** A cor troca no ponto mais apagado do ciclo — sem corte seco entre elas. */
    @Test
    fun `the color changes at the dimmest point of the cycle`() {
        val severities = listOf(CliSessionHealth.ATTENTION, CliSessionHealth.SATURATED)

        val handover = sessionPulseFrame(0.5f, severities)!!
        val peak = sessionPulseFrame(0.75f, severities)!!

        assertEquals(CliSessionHealth.SATURATED, handover.health)
        assertTrue(handover.alpha < peak.alpha)
    }

    /** A fase da animação pode passar de 1 ao reiniciar; o ciclo é circular. */
    @Test
    fun `a phase beyond one wraps around`() {
        val severities = listOf(CliSessionHealth.ATTENTION, CliSessionHealth.SATURATED)

        assertEquals(sessionPulseFrame(0.25f, severities), sessionPulseFrame(1.25f, severities))
    }
}
