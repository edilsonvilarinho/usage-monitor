package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.help.HelpMediaState
import com.usagemonitor.presentation.ui.help.ONBOARDING_BACK_TAG
import com.usagemonitor.presentation.ui.help.ONBOARDING_NEXT_TAG
import com.usagemonitor.presentation.ui.help.ONBOARDING_SKIP_TAG
import com.usagemonitor.presentation.ui.help.OnboardingGuideCatalog
import com.usagemonitor.presentation.ui.help.OnboardingGuideContent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OnboardingGuideContentTest {

    private val slides = OnboardingGuideCatalog.slides

    @Test
    fun `first card states the step and offers skip and next but not back`() = runDesktopComposeUiTest {
        var next = 0
        var finished = 0
        showGuide(0, onNext = { next++ }, onFinish = { finished++ })

        onNodeWithText("Passo 1 de ${slides.size}").assertIsDisplayed()
        onNodeWithText(slides.first().textPt).assertIsDisplayed()
        onNodeWithTag(ONBOARDING_BACK_TAG).assertDoesNotExist()

        onNodeWithTag(ONBOARDING_NEXT_TAG).performClick()
        onNodeWithTag(ONBOARDING_SKIP_TAG).performClick()
        assertEquals(1, next)
        assertEquals(1, finished)
    }

    @Test
    fun `last card turns next into done and drops skip`() = runDesktopComposeUiTest {
        var back = 0
        var finished = 0
        showGuide(slides.lastIndex, onBack = { back++ }, onFinish = { finished++ })

        onNodeWithText("Concluir").assertIsDisplayed()
        onNodeWithTag(ONBOARDING_SKIP_TAG).assertDoesNotExist()

        onNodeWithTag(ONBOARDING_BACK_TAG).performClick()
        onNodeWithTag(ONBOARDING_NEXT_TAG).performClick()
        assertEquals(1, back)
        assertEquals(1, finished)
    }

    /** A ilustração é a demo da Ajuda, encontrada pelo nome do cartão. */
    @Test
    fun `the illustration is the help demo named after the card`() = runDesktopComposeUiTest {
        showGuide(0, media = HelpMediaState.Frame(ImageBitmap(64, 40)))

        onNodeWithContentDescription("Demonstração: ${slides.first().titlePt}").assertIsDisplayed()
    }

    /** Sem a gravação, o texto do cartão continua na tela. */
    @Test
    fun `without media the card text stays on screen`() = runDesktopComposeUiTest {
        showGuide(2, media = HelpMediaState.Unavailable)

        onNodeWithText("Demonstração indisponível.").assertIsDisplayed()
        onNodeWithText(slides[2].textPt).assertIsDisplayed()
    }

    @Test
    fun `english translates the step and the buttons`() = runDesktopComposeUiTest {
        showGuide(1, language = AppLanguage.EN)

        onNodeWithText("Step 2 of ${slides.size}").assertIsDisplayed()
        onNodeWithText("Next").assertIsDisplayed()
        onNodeWithText("Back").assertIsDisplayed()
        onNodeWithText("Skip guide").assertIsDisplayed()
    }

    /** Cada cartão aponta para um tópico diferente da Ajuda. */
    @Test
    fun `cards point to distinct help topics`() {
        assertEquals(slides.size, slides.map { slide -> slide.topic }.toSet().size)
    }

    private fun ComposeUiTest.showGuide(
        index: Int,
        language: AppLanguage = AppLanguage.PT,
        media: HelpMediaState = HelpMediaState.Unavailable,
        onBack: () -> Unit = {},
        onNext: () -> Unit = {},
        onFinish: () -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.size(width = 1_080.dp, height = 700.dp)) {
                    OnboardingGuideContent(
                        slideIndex = index,
                        language = language,
                        onBack = onBack,
                        onNext = onNext,
                        onFinish = onFinish,
                        media = media
                    )
                }
            }
        }
    }
}
