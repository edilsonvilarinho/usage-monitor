package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.BUG_REPORT_DESCRIPTION_TEST_TAG
import com.usagemonitor.presentation.ui.components.BUG_REPORT_PREVIEW_TEST_TAG
import com.usagemonitor.presentation.ui.components.BUG_REPORT_SCREENSHOT_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.BUG_REPORT_STATUS_TEST_TAG
import com.usagemonitor.presentation.ui.components.BugReportDialog
import com.usagemonitor.presentation.ui.theme.AppTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BugReportDialogTest {

    @Test
    fun `the dialog says nothing is sent automatically`() = runDesktopComposeUiTest {
        showDialog()

        onNodeWithText("Reportar um bug").assertIsDisplayed()
        onNodeWithText(
            "Salve o pacote de diagnóstico e abra a issue no GitHub. " +
                "Nada é enviado automaticamente: você revisa e publica."
        ).assertIsDisplayed()
    }

    /**
     * Botão desabilitado sem motivo é pior que botão desabilitado: a frase diz o
     * que falta em vez de deixar o usuário adivinhar.
     */
    @Test
    fun `both actions are disabled while the description is blank, with the reason on screen`() =
        runDesktopComposeUiTest {
            showDialog(description = "   ")

            onNodeWithText("Salvar arquivo").assertIsNotEnabled()
            onNodeWithText("Abrir issue no GitHub").assertIsNotEnabled()
            onNodeWithText("Escreva a descrição para habilitar as ações.").assertIsDisplayed()
        }

    @Test
    fun `a described bug enables both actions and drops the hint`() = runDesktopComposeUiTest {
        showDialog(description = "o card do Codex ficou em branco")

        onNodeWithText("Salvar arquivo").assertIsEnabled()
        onNodeWithText("Abrir issue no GitHub").assertIsEnabled()
        onNodeWithText("Escreva a descrição para habilitar as ações.").assertDoesNotExist()
    }

    @Test
    fun `typing reports the new description`() = runDesktopComposeUiTest {
        var typed: String? = null
        showDialog(onDescriptionChange = { value -> typed = value })

        onNodeWithTag(BUG_REPORT_DESCRIPTION_TEST_TAG).performTextInput("caiu")

        assertEquals("caiu", typed)
    }

    /**
     * O placeholder fica fora da árvore semântica: sem isso, o campo vazio passa
     * a "conter" o texto de exemplo e duplica nós para o `onNodeWithText`.
     */
    @Test
    fun `the placeholder does not leak into the field semantics`() = runDesktopComposeUiTest {
        showDialog(description = "")

        onNodeWithText("Descreva o que você fez e o que aconteceu.").assertDoesNotExist()
    }

    @Test
    fun `the preview starts collapsed and opens on demand`() = runDesktopComposeUiTest {
        setContent {
            // Estado de composição, não um `var` do teste: com um `var` comum a
            // recomposição não acontece e a prévia nunca aparece.
            var expanded by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(false)
            }
            AppTheme(isDark = true) {
                BugReportDialog(
                    description = "caiu",
                    onDescriptionChange = {},
                    previewText = "## O que aconteceu",
                    previewExpanded = expanded,
                    onTogglePreview = { expanded = !expanded },
                    onSaveFile = {},
                    onOpenIssue = {},
                    onDismiss = {}
                )
            }
        }

        onNodeWithTag(BUG_REPORT_PREVIEW_TEST_TAG).assertDoesNotExist()
        onNodeWithText("Ver prévia").performClick()
        onNodeWithTag(BUG_REPORT_PREVIEW_TEST_TAG).assertIsDisplayed()
        onNodeWithText("Ocultar prévia").assertIsDisplayed()
    }

    /**
     * Interruptor que não pode fazer nada é pior que interruptor nenhum: sem tela
     * a captura devolveria sempre nada e a caixa marcada prometeria uma imagem
     * que não vem.
     */
    @Test
    fun `without capture support the switch is not drawn at all`() = runDesktopComposeUiTest {
        showDialog(screenshotSupported = false)

        onNodeWithTag(BUG_REPORT_SCREENSHOT_SWITCH_TEST_TAG).assertDoesNotExist()
        onNodeWithText("Incluir captura da janela").assertDoesNotExist()
    }

    @Test
    fun `with capture support the switch is drawn and starts off`() = runDesktopComposeUiTest {
        showDialog()

        onNodeWithTag(BUG_REPORT_SCREENSHOT_SWITCH_TEST_TAG).assertIsOff()
    }

    @Test
    fun `the two actions report their clicks`() = runDesktopComposeUiTest {
        val clicks = mutableListOf<String>()
        showDialog(
            description = "caiu",
            onSaveFile = { clicks += "save" },
            onOpenIssue = { clicks += "issue" }
        )

        onNodeWithText("Salvar arquivo").performClick()
        onNodeWithText("Abrir issue no GitHub").performClick()

        assertEquals(listOf("save", "issue"), clicks)
    }

    @Test
    fun `a status message is shown when there is one`() = runDesktopComposeUiTest {
        showDialog(statusMessage = "Arquivo salvo em C:/tmp/relatorio.json")

        onNodeWithTag(BUG_REPORT_STATUS_TEST_TAG).assertIsDisplayed()
        onNodeWithText("Arquivo salvo em C:/tmp/relatorio.json").assertIsDisplayed()
    }

    @Test
    fun `english translates the title and both actions`() = runDesktopComposeUiTest {
        showDialog(description = "it crashed", language = AppLanguage.EN)

        onNodeWithText("Report a bug").assertIsDisplayed()
        onNodeWithText("Save file").assertIsEnabled()
        onNodeWithText("Open issue on GitHub").assertIsEnabled()
    }

    /** Nenhuma animação infinita: a suíte assenta sozinha. */
    @Test
    fun `the dialog settles without an endless animation`() = runDesktopComposeUiTest {
        showDialog(description = "caiu")

        waitForIdle()

        assertTrue(true)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showDialog(
        description: String = "",
        onDescriptionChange: (String) -> Unit = {},
        previewExpanded: Boolean = false,
        screenshotSupported: Boolean = true,
        statusMessage: String? = null,
        language: AppLanguage = AppLanguage.PT,
        onSaveFile: () -> Unit = {},
        onOpenIssue: () -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                BugReportDialog(
                    description = description,
                    onDescriptionChange = onDescriptionChange,
                    previewText = "## O que aconteceu",
                    previewExpanded = previewExpanded,
                    onTogglePreview = {},
                    onSaveFile = onSaveFile,
                    onOpenIssue = onOpenIssue,
                    onDismiss = {},
                    language = language,
                    screenshotSupported = screenshotSupported,
                    statusMessage = statusMessage
                )
            }
        }
    }
}
