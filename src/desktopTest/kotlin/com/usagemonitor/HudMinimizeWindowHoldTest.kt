package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class HudMinimizeWindowHoldTest {

    private val before = HudNotchSizes(collapsed = DpSize(181.dp, 40.dp), expanded = DpSize(200.dp, 60.dp))
    private val after = HudNotchSizes(collapsed = DpSize(116.dp, 40.dp), expanded = DpSize(200.dp, 60.dp))

    @Test
    fun `durante a transicao a janela segura o maior tamanho entre antes e depois`() {
        assertEquals(DpSize(181.dp, 40.dp), hudHeldNotchSizes(before, after, moving = true).collapsed, "a janela encolheu antes do fim da transição")
    }

    @Test
    fun `parada a janela e o tamanho depois`() {
        assertEquals(after, hudHeldNotchSizes(before, after, moving = false))
    }
}
