package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.BETA_UPDATES_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.components.BetaUpdatesToggle
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class BetaUpdatesToggleTest {

    /**
     * O texto diz as duas coisas que o usuário precisa saber antes de ligar: que
     * beta pode ter defeitos e que desligar não volta para a estável anterior.
     */
    @Test
    fun `the hint warns about bugs and about no rollback`() = runDesktopComposeUiTest {
        showToggle(enabled = false, language = AppLanguage.PT)

        onNodeWithText("Receber versões beta").assertIsDisplayed()
        onNodeWithText("podem ter defeitos", substring = true).assertIsDisplayed()
        onNodeWithText("Desligar não volta para a versão anterior", substring = true).assertIsDisplayed()
        onNodeWithTag(BETA_UPDATES_SWITCH_TEST_TAG).assertIsOff()
    }

    @Test
    fun `the english label names the channel`() = runDesktopComposeUiTest {
        showToggle(enabled = true, language = AppLanguage.EN)

        onNodeWithText("Receive beta updates").assertIsDisplayed()
        onNodeWithTag(BETA_UPDATES_SWITCH_TEST_TAG).assertIsOn()
    }

    @Test
    fun `toggling reports the new value`() = runDesktopComposeUiTest {
        var lastValue: Boolean? = null
        showToggle(enabled = false, language = AppLanguage.PT, onToggle = { value -> lastValue = value })

        onNodeWithTag(BETA_UPDATES_SWITCH_TEST_TAG).performClick()

        assertEquals(true, lastValue)
    }

    /** A aba Geral traz o interruptor e repassa o clique a quem é dono da preferência. */
    @Test
    fun `the general settings tab shows the beta switch`() = runDesktopComposeUiTest {
        var lastValue: Boolean? = null
        setContent {
            ScreenTestTheme(isDark = true) {
                SettingsDialogContent(
                    currentTheme = com.usagemonitor.presentation.ui.theme.AppThemePreset.entries.first(),
                    currentLanguage = AppLanguage.PT,
                    enabledApis = emptySet(),
                    autoStartEnabled = false,
                    onThemeChange = {},
                    onLanguageChange = {},
                    onAutoStartChange = {},
                    onApiToggle = { _, _ -> },
                    receiveBetaUpdates = false,
                    onReceiveBetaUpdatesChange = { value -> lastValue = value }
                )
            }
        }

        // A aba Geral rola; o interruptor pode nascer fora da vista.
        onNodeWithTag(BETA_UPDATES_SWITCH_TEST_TAG).performScrollTo().assertIsOff().performClick()

        assertEquals(true, lastValue)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showToggle(
        enabled: Boolean,
        language: AppLanguage,
        onToggle: (Boolean) -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(560.dp)) {
                    BetaUpdatesToggle(
                        enabled = enabled,
                        language = language,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}
