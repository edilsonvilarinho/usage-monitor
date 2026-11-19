package com.usagemonitor

import com.usagemonitor.presentation.ui.hudDiskAngleDegrees
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Ângulo do disco de acreção do buraco flutuante (A07a): função pura, tempo em ms e volta em segundos. */
class HudFloatingHoleMathTest {

    @Test
    fun `no instante zero o disco esta em zero graus`() {
        assertNear(0f, hudDiskAngleDegrees(0L, 3.5f), "t=0")
    }

    @Test
    fun `meia volta do disco e 180 graus`() {
        assertNear(180f, hudDiskAngleDegrees(1_750L, 3.5f), "meia volta")
    }

    @Test
    fun `um quarto de volta e 90 graus`() {
        assertNear(90f, hudDiskAngleDegrees(875L, 3.5f), "quarto de volta")
    }

    @Test
    fun `uma volta completa volta a zero graus`() {
        assertNear(0f, hudDiskAngleDegrees(3_500L, 3.5f), "volta completa")
        assertNear(0f, hudDiskAngleDegrees(7_000L, 3.5f), "duas voltas")
    }

    @Test
    fun `tempo negativo cai no intervalo de zero a 360`() {
        // -875 ms sao -0,25 volta, ou 270 graus em [0, 360).
        assertNear(270f, hudDiskAngleDegrees(-875L, 3.5f), "tempo negativo")
    }

    @Test
    fun `velocidade zero ou negativa deixa o disco parado em zero`() {
        assertNear(0f, hudDiskAngleDegrees(1_000L, 0f), "spin zero")
        assertNear(0f, hudDiskAngleDegrees(1_000L, -2f), "spin negativo")
    }

    @Test
    fun `o angulo fica sempre em zero inclusive e 360 exclusive`() {
        for (t in -10_000L..10_000L step 37L) {
            val angle = hudDiskAngleDegrees(t, 3.5f)
            assertTrue(angle >= 0f && angle < 360f, "t=$t angle=$angle")
        }
    }

    private fun assertNear(expected: Float, actual: Float, label: String) {
        assertTrue(abs(expected - actual) < 0.01f, "$label: esperado=$expected, obtido=$actual")
    }
}
