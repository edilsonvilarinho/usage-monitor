package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.REPORT_BUG_BUTTON_TEST_TAG
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.presentation.ui.components.SettingsTab
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A aba Geral cresceu com a seção nova, e a cena do teste tem de crescer junto:
 * `assertIsDisplayed` mede contra os limites da janela, não os do `Box` interno.
 */
private const val SCENE_HEIGHT = 2200

@OptIn(ExperimentalTestApi::class)
class DiagnosticsSettingsSectionTest {

    @Test
    fun `the general tab offers the report action and says nothing is sent`() = runDesktopComposeUiTest(width = 1024, height = SCENE_HEIGHT) {
        showGeneralTab()

        onNodeWithText("Diagnóstico").assertIsDisplayed()
        onNodeWithTag(REPORT_BUG_BUTTON_TEST_TAG).assertIsDisplayed()
        onNodeWithText(
            "O app guarda uma trilha dos últimos passos e dos erros em " +
                "~/.usage-monitor/diagnostics. Ao reportar, você revisa o pacote antes de " +
                "publicá-lo: nada é enviado automaticamente."
        ).assertIsDisplayed()
    }

    @Test
    fun `clicking the action reports it once`() = runDesktopComposeUiTest(width = 1024, height = SCENE_HEIGHT) {
        var clicks = 0
        showGeneralTab(onReportBug = { clicks += 1 })

        onNodeWithTag(REPORT_BUG_BUTTON_TEST_TAG).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `english translates the section and the action`() = runDesktopComposeUiTest(width = 1024, height = SCENE_HEIGHT) {
        showGeneralTab(language = AppLanguage.EN)

        onNodeWithText("Diagnostics").assertIsDisplayed()
        onNodeWithText("Report a bug").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showGeneralTab(
        language: AppLanguage = AppLanguage.PT,
        onReportBug: () -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = AppThemePreset.OBSIDIANA_DARK,
                    currentLanguage = language,
                    enabledApis = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    onReportBug = onReportBug,
                    initialTab = SettingsTab.GENERAL
                )
            }
        }
    }
}
