package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.STARTUP_TIPS_SWITCH_TEST_TAG
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.ui.help.STARTUP_TIP_HELP_TAG
import com.usagemonitor.presentation.ui.help.STARTUP_TIP_NEXT_TAG
import com.usagemonitor.presentation.ui.help.StartupTipCatalog
import com.usagemonitor.presentation.ui.help.StartupTipContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StartupTipContentTest {

    private val tips = StartupTipCatalog.tips

    @Test
    fun `shows the tip under its help topic with a written counter`() = runDesktopComposeUiTest {
        showTip(2)

        val tip = tips[2]
        onNodeWithText(HelpCatalog.entry(tip.topic, AppLanguage.PT).title).assertIsDisplayed()
        onNodeWithText("Dica 3 de ${tips.size}").assertIsDisplayed()
        onNodeWithText(tip.pt).assertIsDisplayed()
    }

    @Test
    fun `next, help and close report their events`() = runDesktopComposeUiTest {
        var next = 0
        var closed = 0
        var helpTopic: HelpTopic? = null
        showTip(0, onNext = { next++ }, onClose = { closed++ }, onOpenHelp = { topic -> helpTopic = topic })

        onNodeWithTag(STARTUP_TIP_NEXT_TAG).performClick()
        onNodeWithTag(STARTUP_TIP_HELP_TAG).performClick()
        onNodeWithText("Fechar").performClick()

        assertEquals(1, next)
        assertEquals(1, closed)
        assertEquals(tips[0].topic, helpTopic)
    }

    /** O interruptor do modal é o mesmo das Configurações: desligar sai daqui. */
    @Test
    fun `turning tips off from the modal reports false`() = runDesktopComposeUiTest {
        var enabled: Boolean? = null
        showTip(0, onTipsEnabledChange = { value -> enabled = value })

        onNodeWithText("Mostrar a dica do dia ao abrir").assertIsDisplayed()
        onNodeWithTag(STARTUP_TIPS_SWITCH_TEST_TAG).assertIsOn().performClick()

        assertEquals(false, enabled)
    }

    @Test
    fun `english translates the counter and the buttons`() = runDesktopComposeUiTest {
        showTip(1, language = AppLanguage.EN)

        onNodeWithText("Tip 2 of ${tips.size}").assertIsDisplayed()
        onNodeWithText(tips[1].en).assertIsDisplayed()
        onNodeWithText("Next tip").assertIsDisplayed()
        onNodeWithText("Open in Help").assertIsDisplayed()
    }

    private fun ComposeUiTest.showTip(
        index: Int,
        language: AppLanguage = AppLanguage.PT,
        onTipsEnabledChange: (Boolean) -> Unit = {},
        onNext: () -> Unit = {},
        onOpenHelp: (HelpTopic) -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.size(width = 680.dp, height = 600.dp)) {
                    StartupTipContent(
                        tipIndex = index,
                        language = language,
                        tipsEnabled = true,
                        onTipsEnabledChange = onTipsEnabledChange,
                        onNext = onNext,
                        onOpenHelp = onOpenHelp,
                        onClose = onClose
                    )
                }
            }
        }
    }
}
