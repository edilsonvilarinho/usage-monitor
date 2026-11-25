package com.usagemonitor

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.usagemonitor.AutoStartManager.Platform
import com.usagemonitor.presentation.ui.DWMWA_CLOAKED
import com.usagemonitor.presentation.ui.DwmApi
import com.usagemonitor.presentation.ui.WindowsDwmCloak
import com.usagemonitor.presentation.ui.dwmApi
import com.usagemonitor.presentation.ui.warmUpWindowCloak
import org.junit.Assume
import java.awt.GraphicsEnvironment
import javax.swing.JDialog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Só no Windows e fora de modo headless: a chamada ao DWM não existe em outro lugar. Fora disso o teste
 * é pulado (`Assume`), nunca aprovado sem rodar.
 */
class WindowsDwmCloakTest {

    @Test
    fun `cloaking a window the system has not shown reads DWMWA_CLOAKED 1, and uncloaking reads 0`() {
        Assume.assumeTrue(windowsWithDisplay())

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
        Assume.assumeTrue(windowsWithDisplay())

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
        Assume.assumeTrue(windowsWithDisplay())

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
        Assume.assumeTrue(windowsWithDisplay())

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
        Assume.assumeTrue(windowsWithDisplay())

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

    /** `set` só vale com S_OK; a API entra pelo provedor, e o valor enviado é o de DWMWA_CLOAK. */
    @Test
    fun `set answers true only for S_OK and sends the cloak value through the injected api`() {
        Assume.assumeTrue(windowsWithDisplay())

        val fake = FakeDwm(hresult = 0)
        val dialog = JDialog()
        try {
            dialog.addNotify()

            assertTrue(WindowsDwmCloak(dialog, { fake }).set(true), "S_OK deve virar true")
            assertEquals(1, fake.lastValue, "valor de DWMWA_CLOAK enviado à API injetada")
        } finally {
            dialog.dispose()
        }
    }

    /** HRESULT de falha (E_ACCESSDENIED, 0x80070005) não vira cloak ativo. */
    @Test
    fun `set answers false when the DWM returns a failing HRESULT`() {
        Assume.assumeTrue(windowsWithDisplay())

        val dialog = JDialog()
        try {
            dialog.addNotify()

            assertFalse(WindowsDwmCloak(dialog, { FakeDwm(hresult = -2147024891) }).set(true), "HRESULT 0x80070005")
        } finally {
            dialog.dispose()
        }
    }

    /** Exceção de runtime na chamada nativa vira false, sem propagar para a HUD. */
    @Test
    fun `set answers false when the DWM call throws a RuntimeException`() {
        Assume.assumeTrue(windowsWithDisplay())

        val dialog = JDialog()
        try {
            dialog.addNotify()

            assertFalse(WindowsDwmCloak(dialog, { FakeDwm(failure = IllegalStateException("simulado")) }).set(true))
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

    /** DwmApi falso: responde o HRESULT dado ou lança a falha; guarda o último valor de DWMWA_CLOAK. */
    private class FakeDwm(
        private val hresult: Int = 0,
        private val failure: RuntimeException? = null,
    ) : DwmApi {
        var lastValue: Int? = null

        override fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int {
            if (failure != null) throw failure
            lastValue = value.value
            return hresult
        }

        override fun DwmGetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int = 0
    }
}
