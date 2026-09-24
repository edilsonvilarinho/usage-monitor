package com.usagemonitor.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class AppAnimatedNumberTest {

    @Test
    fun `a direcao segue o valor numerico e nao o texto`() {
        assertEquals(1, numericDirection("41%", "68%"))
        assertEquals(-1, numericDirection("68%", "9%"))
        assertEquals(0, numericDirection("12%", "12%"))
    }

    @Test
    fun `separador decimal de virgula e de ponto sao lidos`() {
        assertEquals(1, numericDirection("US$ 12,84", "US$ 12,90"))
        assertEquals(-1, numericDirection("$190.00", "$18.50"))
    }

    @Test
    fun `texto sem numero nao tem direcao`() {
        assertEquals(0, numericDirection("—", "41%"))
        assertEquals(0, numericDirection("41%", "Sem dado"))
    }
}
