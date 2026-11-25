package com.usagemonitor.presentation.ui

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.usagemonitor.AutoStartManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

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
    } catch (error: LinkageError) {
        false
    } catch (error: RuntimeException) {
        false
    }
}

/**
 * Paga fora da EDT a carga da `dwmapi` e do JNA, que na primeira chamada leva dezenas de
 * milissegundos (medido: mediana 55 ms, p95 147 ms, até 4.113 ms na EDT). Fora do Windows não
 * faz nada. Falha de ligação ou de janela não derruba a HUD; cancelamento é relançado.
 */
internal suspend fun preloadWindowCloak(
    platform: AutoStartManager.Platform,
    dispatcher: CoroutineDispatcher,
    warmUp: () -> Unit,
) {
    if (!shouldCloakModalWindow(platform)) {
        return
    }
    try {
        withContext(dispatcher) { warmUp() }
    } catch (error: CancellationException) {
        throw error
    } catch (error: LinkageError) {
        // Biblioteca nativa ausente: o cloak recusa depois e a janela segue sem pré-aquecimento.
    } catch (error: RuntimeException) {
        // Janela sem HWND: o pré-aquecimento fica para o primeiro clique, sem derrubar a HUD.
    }
}

/**
 * Carrega `dwmapi` e, se houver janela AWT já exibida, lê o cloak dela. Chamada fora da EDT
 * por [preloadWindowCloak]. Sem janela exibida, só força a carga da biblioteca.
 */
internal fun warmUpWindowCloak() {
    val window = java.awt.Window.getWindows().firstOrNull { it.isDisplayable }
    if (window == null) {
        loadDwmApi()
        return
    }
    val cloaked = IntByReference(0)
    loadDwmApi().DwmGetWindowAttribute(Native.getWindowPointer(window), DWMWA_CLOAKED, cloaked, 4)
}

private fun loadDwmApi(): DwmApi = dwmApi
