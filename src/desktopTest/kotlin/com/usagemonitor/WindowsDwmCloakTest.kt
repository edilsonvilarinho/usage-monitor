package com.usagemonitor

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.usagemonitor.AutoStartManager.Platform
import com.usagemonitor.presentation.ui.DWMWA_CLOAKED
import com.usagemonitor.presentation.ui.WindowsDwmCloak
import com.usagemonitor.presentation.ui.dwmApi
import com.usagemonitor.presentation.ui.warmUpWindowCloak
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `warm-up on a displayable window answers S_OK and leaves DWMWA_CLOAKED at 0`() {
        if (!windowsWithDisplay()) return

        // addNotify cria o peer sem mostrar a janela.
        val dialog = JDialog()
        try {
            dialog.addNotify()

            assertTrue(warmUpWindowCloak(arrayOf<java.awt.Window>(dialog)), "DWM deve responder S_OK para janela exibível")
            assertEquals(0, cloakedValue(dialog), "DWMWA_CLOAKED depois do warm-up")
        } finally {
            dialog.dispose()
        }
    }

    @Test
    fun `warm-up without a displayable window only loads the library and answers false`() {
        if (!windowsWithDisplay()) return

        // Sem addNotify: a janela não é exibível e o warm-up não a toca.
        val hidden = JDialog()
        try {
            assertFalse(warmUpWindowCloak(emptyArray()), "sem nenhuma janela")
            assertFalse(warmUpWindowCloak(arrayOf<java.awt.Window>(hidden)), "janela não exibível")
        } finally {
            hidden.dispose()
        }
    }

    @Test
    fun `set(true) makes a window that is not displayable yet displayable and cloaks it`() {
        if (!windowsWithDisplay()) return

        val dialog = JDialog()
        try {
            assertFalse(dialog.isDisplayable, "pré-condição: janela ainda não exibível")
            val cloak = WindowsDwmCloak(dialog)

            assertTrue(cloak.set(true), "set(true) numa janela ainda não exibível")
            assertTrue(dialog.isDisplayable, "set(true) deve criar o peer nativo")
            assertEquals(1, cloakedValue(dialog), "DWMWA_CLOAKED depois de set(true)")
            assertTrue(cloak.set(false), "set(false) depois de set(true)")
        } finally {
            dialog.dispose()
        }
    }

    @Test
    fun `set returns false when a LinkageError escapes the native call`() {
        if (!windowsWithDisplay()) return

        // addNotify falha como falharia sem a biblioteca: set não pode propagar.
        val dialog = object : JDialog() {
            override fun addNotify() {
                throw NoClassDefFoundError("simulado")
            }
        }
        try {
            assertFalse(WindowsDwmCloak(dialog).set(true), "LinkageError deve virar false")
        } finally {
            dialog.dispose()
        }
    }

    private fun windowsWithDisplay(): Boolean =
        AutoStartManager.currentPlatform() == Platform.WINDOWS && !GraphicsEnvironment.isHeadless()

    private fun cloakedValue(window: java.awt.Window): Int {
        val value = IntByReference(-1)
        val hr = dwmApi.DwmGetWindowAttribute(Native.getWindowPointer(window), DWMWA_CLOAKED, value, 4)
        assertEquals(0, hr, "HRESULT de DwmGetWindowAttribute")
        return value.value
    }
}
