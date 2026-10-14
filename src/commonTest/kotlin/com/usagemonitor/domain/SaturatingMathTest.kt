package com.usagemonitor.domain

import com.usagemonitor.domain.entity.saturatingAdd
import kotlin.test.Test
import kotlin.test.assertEquals

class SaturatingMathTest {

    @Test
    fun `soma comum nao muda`() {
        assertEquals(5L, saturatingAdd(2L, 3L))
    }

    @Test
    fun `soma que transbordaria trava no maximo`() {
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE - 1L, 2L))
        assertEquals(Long.MAX_VALUE, saturatingAdd(Long.MAX_VALUE, Long.MAX_VALUE))
    }
}
