package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModalWindowAutoSizeTest {

    private val laptop = ScreenWorkArea(
        x = 0.dp,
        y = 0.dp,
        size = DpSize(1366.dp, 728.dp)
    )

    @Test
    fun `crescimento acompanha a altura reportada e preserva a largura`() {
        val target = targetModalWindowSize(
            currentSize = DpSize(860.dp, 400.dp),
            requiredContentHeight = 500.dp,
            workArea = laptop
        )

        assertEquals(860.dp, target.width)
        assertEquals(534.dp, target.height)
    }

    @Test
    fun `reducao acompanha a altura reportada`() {
        val target = targetModalWindowSize(
            currentSize = DpSize(860.dp, 700.dp),
            requiredContentHeight = 350.dp,
            workArea = laptop
        )

        assertEquals(384.dp, target.height)
    }

    @Test
    fun `piso geral impede uma janela pequena demais`() {
        val target = targetModalWindowSize(
            currentSize = DpSize(860.dp, 400.dp),
            requiredContentHeight = 80.dp,
            workArea = laptop
        )

        assertEquals(DEFAULT_MODAL_MIN_HEIGHT, target.height)
    }

    @Test
    fun `teto respeita a area util menos a margem`() {
        val target = targetModalWindowSize(
            currentSize = DpSize(860.dp, 400.dp),
            requiredContentHeight = 900.dp,
            workArea = laptop
        )

        assertEquals(laptop.size.height - WINDOW_SCREEN_MARGIN, target.height)
    }

    @Test
    fun `area desconhecida nao introduz teto artificial`() {
        val target = targetModalWindowSize(
            currentSize = DpSize(860.dp, 400.dp),
            requiredContentHeight = 900.dp,
            workArea = ScreenWorkArea.Unknown
        )

        assertEquals(934.dp, target.height)
    }

    @Test
    fun `maximizada nao permite ajuste automatico`() {
        assertTrue(shouldApplyModalAutoSize(WindowPlacement.Floating))
        assertFalse(shouldApplyModalAutoSize(WindowPlacement.Maximized))
        assertFalse(shouldApplyModalAutoSize(WindowPlacement.Fullscreen))
    }

    @Test
    fun `redimensionamento manual sem mudanca de conteudo conserva o tamanho atual`() {
        val current = DpSize(860.dp, 600.dp)
        val target = targetModalWindowSize(
            currentSize = current,
            requiredContentHeight = 566.dp,
            workArea = laptop
        )

        assertEquals(current, target)
    }
}
