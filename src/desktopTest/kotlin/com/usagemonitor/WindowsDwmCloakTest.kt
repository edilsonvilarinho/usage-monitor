package com.usagemonitor

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.usagemonitor.AutoStartManager.Platform
import com.usagemonitor.presentation.ui.DWMWA_CLOAKED
import com.usagemonitor.presentation.ui.WindowsDwmCloak
import com.usagemonitor.presentation.ui.dwmApi
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Só no Windows e fora de modo headless: a chamada ao DWM não existe em outro lugar. */
class WindowsDwmCloakTest {

    @Test
    fun `cloaking a window the system has not shown reads DWMWA_CLOAKED 1, and uncloaking reads 0`() {
        if (AutoStartManager.currentPlatform() != Platform.WINDOWS || GraphicsEnvironment.isHeadless()) return

        // Nunca mostrada: o teste não abre janela na tela.
        val dialog = JDialog()
        try {
            dialog.addNotify()
            val cloak = WindowsDwmCloak(dialog)

            assertTrue(cloak.set(true), "o DWM recusou DWMWA_CLOAK = 1")
            assertEquals(1, cloakedValue(dialog), "DWMWA_CLOAKED depois de set(true)")
            assertTrue(cloak.set(false), "o DWM recusou DWMWA_CLOAK = 0")
            assertEquals(0, cloakedValue(dialog), "DWMWA_CLOAKED depois de set(false)")
        } finally {
            dialog.dispose()
        }
    }

    private fun cloakedValue(window: java.awt.Window): Int {
        val value = IntByReference(-1)
        val hr = dwmApi.DwmGetWindowAttribute(Native.getWindowPointer(window), DWMWA_CLOAKED, value, 4)
        assertEquals(0, hr, "HRESULT de DwmGetWindowAttribute")
        return value.value
    }
}
