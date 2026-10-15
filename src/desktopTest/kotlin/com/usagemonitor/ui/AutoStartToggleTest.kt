package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AutoStartToggle
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AutoStartToggleTest {

    @Test
    fun `the default root keeps the switch usable`() = runDesktopComposeUiTest {
        showToggle(enabled = true, available = true)

        onNode(isToggleable()).assertIsEnabled()
        onNode(isToggleable()).assertIsOn()
        onNodeWithText("Registra a aplicação na inicialização do usuário atual.").assertIsDisplayed()
    }

    /**
     * Com `--user-data-dir` (#419) a entrada de inicialização abriria a pasta
     * padrão. O valor do sistema é da outra instância: mostrar ligado seria
     * prometer que esta abre no login.
     */
    @Test
    fun `an isolated data root disables the switch, shows it off and says why`() = runDesktopComposeUiTest {
        showToggle(enabled = true, available = false)

        onNode(isToggleable()).assertIsNotEnabled()
        onNode(isToggleable()).assertIsOff()
        onNodeWithText(
            "Indisponível com --user-data-dir: a entrada de inicialização abriria o app na pasta padrão, não nesta."
        ).assertIsDisplayed()
    }

    @Test
    fun `english translates the reason`() = runDesktopComposeUiTest {
        showToggle(enabled = false, available = false, language = AppLanguage.EN)

        onNodeWithText(
            "Unavailable with --user-data-dir: the startup entry would open the app on the default folder, not this one."
        ).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showToggle(
        enabled: Boolean,
        available: Boolean,
        language: AppLanguage = AppLanguage.PT
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(560.dp)) {
                    AutoStartToggle(
                        enabled = enabled,
                        language = language,
                        onToggle = {},
                        available = available
                    )
                }
            }
        }
    }
}
