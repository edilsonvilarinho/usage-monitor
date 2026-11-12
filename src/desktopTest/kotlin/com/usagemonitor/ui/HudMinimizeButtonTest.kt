package com.usagemonitor.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudMinimizeButton
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HudMinimizeButtonTest {
    private val target = UsageTargetKey.forSource(ApiSource.CODEX)

    /** A conta visível oferece "Minimizar". */
    @Test
    fun `botao de conta visivel tem o rotulo minimizar`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                HudMinimizeButton(target, minimized = false, language = AppLanguage.PT, buttonSize = 28.dp) {}
            }
        }
        onNodeWithContentDescription("Minimizar").assertExists()
    }

    /** A conta minimizada oferece "Restore" em inglês. */
    @Test
    fun `botao de conta minimizada tem o rotulo restore em ingles`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = true) {
                HudMinimizeButton(target, minimized = true, language = AppLanguage.EN, buttonSize = 28.dp) {}
            }
        }
        onNodeWithContentDescription("Restore").assertExists()
    }

    /** O clique devolve a chave da própria conta, para quem alterna o conjunto. */
    @Test
    fun `clique no botao devolve a chave da conta`() = runDesktopComposeUiTest {
        val toggled = mutableListOf<UsageTargetKey>()
        setContent {
            ScreenTestTheme(isDark = true) {
                HudMinimizeButton(target, minimized = false, language = AppLanguage.PT, buttonSize = 28.dp) { key ->
                    toggled += key
                }
            }
        }
        onNodeWithContentDescription("Minimizar").performClick()
        assertEquals(listOf(target), toggled)
    }
}
