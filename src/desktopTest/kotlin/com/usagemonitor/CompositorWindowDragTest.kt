package com.usagemonitor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.DialogModalityType
import com.usagemonitor.presentation.ui.X11CompositorMove
import com.usagemonitor.presentation.ui.dialogModality
import com.usagemonitor.presentation.ui.isCosmicDesktop
import com.usagemonitor.presentation.ui.windowDragByCompositor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O COSMIC ignora toda posição que uma janela X11 gerenciada pede e não mantém
 * o modal acima do que ele bloqueia (#434): o arrasto passa ao compositor, a HUD
 * deixa de ser gerenciada e os diálogos deixam de ser modais. Tudo isso vale
 * **só** nele; os outros desktops seguem o caminho medido de antes.
 */
class CompositorWindowDragTest {

    private val otherDesktops = listOf("KDE", "GNOME", "ubuntu:GNOME", "Pantheon", "X-Cinnamon", "XFCE", "", null)

    @Test
    fun `COSMIC is recognised in any item of the desktop list`() {
        assertTrue(isCosmicDesktop("COSMIC"))
        assertTrue(isCosmicDesktop("pop:COSMIC"))
        assertTrue(isCosmicDesktop("cosmic"))
        for (desktop in otherDesktops) {
            assertFalse(isCosmicDesktop(desktop), "$desktop")
        }
    }

    @Test
    fun `only COSMIC on Linux drags windows through the compositor`() {
        assertTrue(windowDragByCompositor("Linux", "COSMIC"))
        assertTrue(windowDragByCompositor("Linux", "pop:COSMIC"))
        for (desktop in otherDesktops) {
            assertFalse(windowDragByCompositor("Linux", desktop), "$desktop")
        }
        for (os in listOf("Windows 11", "Mac OS X", "FreeBSD")) {
            assertFalse(windowDragByCompositor(os, "COSMIC"), os)
        }
    }

    @Test
    fun `only COSMIC on Linux makes the HUD unmanaged`() {
        val linux = AutoStartManager.Platform.LINUX
        assertEquals(java.awt.Window.Type.POPUP, hudWindowType(linux, "COSMIC"))
        assertEquals(java.awt.Window.Type.POPUP, hudWindowType(linux, "pop:COSMIC"))
        for (desktop in otherDesktops) {
            assertEquals(java.awt.Window.Type.NORMAL, hudWindowType(linux, desktop), "$desktop")
        }
        for (platform in listOf(AutoStartManager.Platform.WINDOWS, AutoStartManager.Platform.MACOS, AutoStartManager.Platform.OTHER)) {
            assertEquals(java.awt.Window.Type.NORMAL, hudWindowType(platform, "COSMIC"), "$platform")
        }
    }

    /** O COSMIC não mantém o modal acima do que ele bloqueia: lá, nenhum diálogo bloqueia. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `dialogs are modeless only on COSMIC`() {
        assertEquals(DialogModalityType.Modeless, dialogModality(cosmic = true))
        assertEquals(DialogModalityType.DocumentModal, dialogModality(cosmic = false))
    }

    /** O `XEvent` tem 24 `long`; o Xlib lê a união inteira ao converter o evento. */
    @Test
    fun `the client message is as large as an XEvent`() {
        assertEquals(24 * com.sun.jna.Native.LONG_SIZE, X11CompositorMove.ClientMessage().size())
    }
}
