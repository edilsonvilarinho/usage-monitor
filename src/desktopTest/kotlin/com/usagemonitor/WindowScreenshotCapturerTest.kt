package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertNull

class WindowScreenshotCapturerTest {

    /**
     * A janela chega por função e não por valor porque a janela principal só
     * existe depois da composição. Antes disso o resultado é ausência de
     * captura, não uma exceção: a captura é *best-effort*.
     */
    @Test
    fun `no window means no capture`() {
        assertNull(RobotWindowScreenshotCapturer { null }.capture())
    }

    @Test
    fun `the no-op capturer never captures`() {
        assertNull(NoWindowScreenshotCapturer.capture())
    }
}
