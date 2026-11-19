package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals

/** Padrões do dono do buraco negro do modo flutuante (dobrados na A08e1): se um valor mudar sem o JSON, o modo deixa de ser o aprovado. */
class HudOrbitGeometryTest {

    @Test
    fun `defaults are the owner values`() {
        val p = HudOrbitParams()
        assertEquals(64.0, p.holeD.toDouble(), "holeD")
        assertEquals(29.0, p.tiltPercent.toDouble(), "tilt")
        assertEquals(3.0, p.diskWidthPercent.toDouble(), "diskW")
        assertEquals(3.5, p.diskSpinSeconds.toDouble(), "spin")
    }
}
