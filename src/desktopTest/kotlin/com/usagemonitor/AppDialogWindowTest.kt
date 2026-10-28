package com.usagemonitor

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.AutoStartManager.Platform
import com.usagemonitor.presentation.ui.closesModal
import com.usagemonitor.presentation.ui.modalOpenedBreadcrumb
import com.usagemonitor.presentation.ui.modalPrewarmedBreadcrumb
import com.usagemonitor.presentation.ui.rememberLastNonNull
import com.usagemonitor.presentation.ui.shouldAnimateModalWindow
import com.usagemonitor.presentation.ui.shouldPrewarmModalWindow
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppDialogWindowTest {

    @Test
    fun `the live app animates modal windows where the platform can fade them`() {
        assertTrue(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = true, Platform.WINDOWS))
        assertTrue(shouldAnimateModalWindow(AppMotionPolicy.Static, opacitySupported = true, Platform.WINDOWS))
    }

    /** "Reduzir animações" abre e fecha na hora, mesmo onde daria para esmaecer. */
    @Test
    fun `reduced motion opens modal windows at once`() {
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Reduced, opacitySupported = true, Platform.WINDOWS))
    }

    /**
     * Sem translucidez, animar só a escala numa janela opaca é o salto que o host
     * existe para eliminar: melhor não animar nada.
     */
    @Test
    fun `without window translucency modal windows open at once`() {
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = false, Platform.WINDOWS))
    }

    /**
     * O esmaecimento pela opacidade da janela só foi medido no Windows. No
     * elementary OS o modal de Configurações ficou translúcido (issue #340).
     */
    @Test
    fun `modal windows fade only on Windows`() {
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = true, Platform.LINUX))
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = true, Platform.MACOS))
        assertFalse(shouldAnimateModalWindow(AppMotionPolicy.Live, opacitySupported = true, Platform.OTHER))
    }

    /** O nome é fixo: o título pode trazer o apelido do perfil, e a trilha vira issue pública. */
    @Test
    fun `the breadcrumb carries the fixed name, the time and whether it was the first open`() {
        assertEquals(
            "janela Configurações pintada em 212 ms (primeira abertura)",
            modalOpenedBreadcrumb("Configurações", elapsedMillis = 212, firstOpen = true)
        )
        assertEquals(
            "janela histórico pintada em 34 ms (reabertura)",
            modalOpenedBreadcrumb("histórico", elapsedMillis = 34, firstOpen = false)
        )
    }

    /**
     * A janela pré-aquecida aparece transparente por dois quadros: só onde a
     * opacidade da janela foi medida. Em qualquer outro caso, a primeira abertura
     * continua criando a janela no clique.
     */
    @Test
    fun `modal windows are prewarmed only on Windows with window translucency`() {
        assertTrue(shouldPrewarmModalWindow(opacitySupported = true, Platform.WINDOWS))
        assertFalse(shouldPrewarmModalWindow(opacitySupported = false, Platform.WINDOWS))
        assertFalse(shouldPrewarmModalWindow(opacitySupported = true, Platform.LINUX))
        assertFalse(shouldPrewarmModalWindow(opacitySupported = true, Platform.MACOS))
        assertFalse(shouldPrewarmModalWindow(opacitySupported = true, Platform.OTHER))
    }

    @Test
    fun `the prewarm breadcrumb is not an open`() {
        assertEquals(
            "janela Configurações pré-aquecida em 310 ms",
            modalPrewarmedBreadcrumb("Configurações", elapsedMillis = 310)
        )
    }

    /** Esc fecha pelo mesmo caminho do ×: chama quem fecha e consome a tecla. */
    @Test
    fun `escape pressed closes the modal and is consumed`() {
        var closed = 0

        assertTrue(closesModal(KeyEventType.KeyDown, Key.Escape) { closed++ })
        assertEquals(1, closed)
    }

    /** Soltar o Esc, ou outra tecla, segue para quem a quiser: a janela não fecha duas vezes. */
    @Test
    fun `releasing escape or pressing another key leaves the modal open`() {
        var closed = 0

        assertFalse(closesModal(KeyEventType.KeyUp, Key.Escape) { closed++ })
        assertFalse(closesModal(KeyEventType.KeyDown, Key.Enter) { closed++ })
        assertEquals(0, closed)
    }

    /** Ao fechar, quem abre zera o assunto no mesmo clique; a janela esmaece com o último. */
    @Test
    fun `the last non null value survives the subject going away`() = runDesktopComposeUiTest {
        var subject by mutableStateOf<String?>(null)
        setContent { Text(rememberLastNonNull(subject) ?: "vazio") }

        onNodeWithText("vazio").assertExists()
        subject = "Anthropic"
        waitForIdle()
        onNodeWithText("Anthropic").assertExists()
        subject = null
        waitForIdle()
        onNodeWithText("Anthropic").assertExists()
        subject = "Codex"
        waitForIdle()
        onNodeWithText("Codex").assertExists()
    }
}
