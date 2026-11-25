package com.usagemonitor.presentation.ui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/** Chamadas do `dwmapi.dll` usadas aqui. Retornam HRESULT; só `0` (S_OK) é aceito. */
internal interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int

    fun DwmGetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
}

/** `DWMWA_CLOAK`: esconde a janela da composição sem mexer na visibilidade AWT. */
internal const val DWMWA_CLOAK = 13

/** `DWMWA_CLOAKED`: leitura do estado de cloak (só para teste). */
internal const val DWMWA_CLOAKED = 14

internal val dwmApi: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }

/**
 * Cloak DWM de uma janela AWT. Sem `HWND` ou sem a biblioteca, devolve `false`:
 * o chamador então não mostra a janela escondida.
 */
internal class WindowsDwmCloak(private val window: java.awt.Window) : WindowCloak {
    override fun set(cloaked: Boolean): Boolean = try {
        if (!window.isDisplayable) window.addNotify()
        val value = IntByReference(if (cloaked) 1 else 0)
        dwmApi.DwmSetWindowAttribute(Native.getWindowPointer(window), DWMWA_CLOAK, value, 4) == 0
    } catch (error: UnsatisfiedLinkError) {
        false
    } catch (error: RuntimeException) {
        false
    }
}
