package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowScreenFitTest {

    private val laptop = ScreenWorkArea(
        x = 0.dp,
        y = 0.dp,
        size = DpSize(1366.dp, 728.dp)
    )

    @Test
    fun `janela mais alta que a tela encolhe para caber com folga`() {
        // 960x780 com a escala default de 115%: o caso da issue #72.
        val fitted = fitWindowSize(DpSize(1104.dp, 897.dp), laptop)

        assertEquals(1104.dp, fitted.width)
        assertEquals(728.dp - WINDOW_SCREEN_MARGIN, fitted.height)
    }

    @Test
    fun `janela que ja cabe passa intocada`() {
        val desired = DpSize(860.dp, 600.dp)

        assertEquals(desired, fitWindowSize(desired, laptop))
    }

    @Test
    fun `area util sem medida nao limita nada`() {
        val desired = DpSize(1600.dp, 1200.dp)

        assertEquals(desired, fitWindowSize(desired, ScreenWorkArea.Unknown))
    }

    @Test
    fun `dimensao nao especificada e devolvida como esta`() {
        val fitted = fitWindowSize(DpSize(Dp.Unspecified, 2000.dp), laptop)

        assertEquals(Dp.Unspecified, fitted.width)
        assertEquals(728.dp - WINDOW_SCREEN_MARGIN, fitted.height)
    }

    @Test
    fun `posicao acima do topo volta para o topo da area util`() {
        // Com o topo fora da tela a barra de titulo some, e ela e a unica que estas
        // janelas `undecorated` tem.
        val position = fitWindowPosition(
            x = 120.dp,
            y = (-64).dp,
            size = DpSize(900.dp, 700.dp),
            workArea = laptop
        )

        assertEquals(120.dp, position.x)
        assertEquals(0.dp, position.y)
    }

    @Test
    fun `posicao fora da borda direita volta para dentro`() {
        val position = fitWindowPosition(
            x = 1300.dp,
            y = 40.dp,
            size = DpSize(900.dp, 500.dp),
            workArea = laptop
        )

        assertEquals(466.dp, position.x)
        assertEquals(40.dp, position.y)
    }

    @Test
    fun `origem deslocada por barra de tarefas no topo e respeitada`() {
        val topBar = ScreenWorkArea(x = 0.dp, y = 48.dp, size = DpSize(1366.dp, 720.dp))

        val position = fitWindowPosition(
            x = 10.dp,
            y = 0.dp,
            size = DpSize(600.dp, 400.dp),
            workArea = topBar
        )

        assertEquals(48.dp, position.y)
    }

    @Test
    fun `janela maior que a area util fica presa ao canto de cima`() {
        val position = fitWindowPosition(
            x = 500.dp,
            y = 500.dp,
            size = DpSize(2000.dp, 1200.dp),
            workArea = laptop
        )

        assertEquals(0.dp, position.x)
        assertEquals(0.dp, position.y)
    }

    @Test
    fun `area util absurda nao produz janela minuscula`() {
        val broken = ScreenWorkArea(x = 0.dp, y = 0.dp, size = DpSize(40.dp, 40.dp))

        val fitted = fitWindowSize(DpSize(900.dp, 700.dp), broken)

        assertEquals(320.dp, fitted.width)
        assertEquals(320.dp, fitted.height)
    }
}
