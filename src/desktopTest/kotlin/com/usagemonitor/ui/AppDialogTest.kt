package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.APP_DIALOG_SCRIM_TAG
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppConfirmationDialog
import com.usagemonitor.presentation.ui.components.AppDialog
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Etiqueta da sonda de largura: um `fillMaxWidth` dentro do corpo mede o quanto o cartão dá a ele. */
private const val APP_DIALOG_WIDTH_PROBE_TAG = "app_dialog_width_probe"

@OptIn(ExperimentalTestApi::class)
class AppDialogTest {

    /** A entrada é finita: depois do `waitForIdle` o cartão está inteiro na tela. */
    @Test
    fun `the dialog settles with title, text and both actions on screen`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    title = { Text("Remover integrante?") },
                    text = { Text("O histórico dele sai do time.") },
                    confirmButton = { AppButton(label = "Remover", onClick = {}, tone = AppButtonTone.DANGER) },
                    dismissButton = { AppButton(label = "Cancelar", onClick = {}, tone = AppButtonTone.GHOST) }
                )
            }
        }

        onNodeWithText("Remover integrante?").assertIsDisplayed()
        onNodeWithText("O histórico dele sai do time.").assertIsDisplayed()
        onNodeWithText("Remover").assertIsDisplayed()
        onNodeWithText("Cancelar").assertIsDisplayed()
    }

    /** Desistir à esquerda, a ação proposta encostada na borda — a ordem do `AlertDialog`. */
    @Test
    fun `the dismiss action sits to the left of the confirm action`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    confirmButton = { AppButton(label = "Salvar", onClick = {}) },
                    dismissButton = { AppButton(label = "Cancelar", onClick = {}) }
                )
            }
        }

        val confirm = onNodeWithText("Salvar").fetchSemanticsNode().boundsInRoot
        val dismiss = onNodeWithText("Cancelar").fetchSemanticsNode().boundsInRoot
        assertTrue(dismiss.right <= confirm.left, "Cancelar $dismiss deveria vir antes de Salvar $confirm")
    }

    @Test
    fun `clicking outside the card asks to close, clicking the card does not`() = runDesktopComposeUiTest {
        var dismissals = 0
        setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = { dismissals += 1 },
                    text = { Text("Corpo do diálogo") },
                    confirmButton = { AppButton(label = "Ok", onClick = {}) }
                )
            }
        }

        onNodeWithText("Corpo do diálogo").performClick()
        assertEquals(0, dismissals)

        // O canto do fundo: o centro dele é justamente onde o cartão está.
        onNodeWithTag(APP_DIALOG_SCRIM_TAG).performTouchInput { click(Offset(4f, 4f)) }
        assertEquals(1, dismissals)
    }

    /**
     * Cartão de 528 dp: teto de 560 menos o padding de 16 dos dois lados. A sonda ocupa
     * a largura inteira do corpo, então mede o teto que o cartão entrega ao conteúdo.
     */
    @Test
    fun `the card gives its content 528 dp, the 560 dp maximum less its padding`() = runDesktopComposeUiTest {
        setContent {
            AppTheme {
                AppDialog(
                    onDismissRequest = {},
                    text = {
                        Box(Modifier.fillMaxWidth().testTag(APP_DIALOG_WIDTH_PROBE_TAG)) {
                            Text("Corpo do diálogo")
                        }
                    },
                    confirmButton = { AppButton(label = "Ok", onClick = {}) }
                )
            }
        }

        onNodeWithTag(APP_DIALOG_WIDTH_PROBE_TAG).assertWidthIsEqualTo(528.dp)
    }

    @Test
    fun `the confirmation dialog confirms without dismissing`() = runDesktopComposeUiTest {
        var confirmed = 0
        var dismissed = 0
        setContent {
            AppTheme {
                AppConfirmationDialog(
                    title = "Apagar conta?",
                    message = "Integrantes, sessões e turnos saem do servidor.",
                    confirmLabel = "Apagar",
                    cancelLabel = "Cancelar",
                    onConfirm = { confirmed += 1 },
                    onDismiss = { dismissed += 1 }
                )
            }
        }

        onNodeWithText("Apagar").performClick()

        assertEquals(1, confirmed)
        assertEquals(0, dismissed)
    }
}
