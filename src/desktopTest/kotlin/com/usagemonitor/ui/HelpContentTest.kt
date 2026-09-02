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
import com.usagemonitor.presentation.ui.help.HELP_MEDIA_TAG
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpContent
import com.usagemonitor.presentation.ui.help.HelpMediaState
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.ui.help.helpTopicTestTag
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HelpContentTest {

    @Test
    fun `shows the selected topic with its description and activation steps`() = runDesktopComposeUiTest {
        showHelp(HelpTopic.WINDOW_MODES)

        val entry = HelpCatalog.entry(HelpTopic.WINDOW_MODES, AppLanguage.PT)
        onNodeWithText(entry.summary).assertIsDisplayed()
        // A seção de ativação tem de estar acima da dobra na altura default da
        // janela: é a pergunta que a tela existe para responder.
        onNodeWithText("Como ativar").assertIsDisplayed()
        onNodeWithText(entry.steps.first()).assertIsDisplayed()
        // O atalho é o "como ativar" deste tópico: sem ele a descrição fala de
        // um modo que a pessoa não tem como ligar. Está na lista, e chega à
        // vista rolando — não se exige que caiba na primeira tela.
        onNodeWithText(entry.steps[1]).assertExists()
    }

    @Test
    fun `the topic rail reports the chosen topic`() = runDesktopComposeUiTest {
        var chosen: HelpTopic? = null
        showHelp(HelpTopic.DASHBOARD, onSelectTopic = { topic -> chosen = topic })

        onNodeWithTag(helpTopicTestTag(HelpTopic.ALERTS)).performClick()

        assertEquals(HelpTopic.ALERTS, chosen)
    }

    /** Mídia ausente não pode esconder o texto: a demo ilustra o tópico, não é o tópico. */
    @Test
    fun `without media the description and the steps stay on screen`() = runDesktopComposeUiTest {
        showHelp(HelpTopic.BUDGET, media = HelpMediaState.Unavailable)

        val entry = HelpCatalog.entry(HelpTopic.BUDGET, AppLanguage.PT)
        onNodeWithText("Demonstração indisponível.").assertIsDisplayed()
        onNodeWithText(entry.steps.first()).assertIsDisplayed()
    }

    /**
     * Sem hover não há tooltip e a demo não tem texto que o leitor de tela
     * alcance: é o `contentDescription` que diz o que ela mostra, e é por ele
     * que esta suíte a encontra.
     */
    @Test
    fun `the demo frame names the topic it shows`() = runDesktopComposeUiTest {
        showHelp(HelpTopic.TEAM, media = HelpMediaState.Frame(ImageBitmap(64, 40)))

        onNodeWithTag(HELP_MEDIA_TAG).assertIsDisplayed()
        onNodeWithContentDescription("Demonstração: Visão de time").assertIsDisplayed()
    }

    @Test
    fun `english translates the chrome and the topic`() = runDesktopComposeUiTest {
        showHelp(HelpTopic.PRESENCE, language = AppLanguage.EN)

        onNodeWithText("Features").assertIsDisplayed()
        onNodeWithText("How to enable").assertIsDisplayed()
        onNodeWithText("Close").assertIsDisplayed()
        // O título traduzido aparece duas vezes — no trilho e no cabeçalho do
        // painel —, então quem o busca por texto encontra dois nós. O resumo é
        // do painel e só existe uma vez.
        onNodeWithText(HelpCatalog.entry(HelpTopic.PRESENCE, AppLanguage.EN).summary)
            .assertIsDisplayed()
    }

    @Test
    fun `closing reports the event`() = runDesktopComposeUiTest {
        var closed = 0
        showHelp(HelpTopic.DASHBOARD, onClose = { closed++ })

        onNodeWithText("Fechar").performClick()

        assertEquals(1, closed)
    }

    private fun ComposeUiTest.showHelp(
        topic: HelpTopic,
        language: AppLanguage = AppLanguage.PT,
        media: HelpMediaState = HelpMediaState.Unavailable,
        onSelectTopic: (HelpTopic) -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.size(width = 900.dp, height = 620.dp)) {
                    HelpContent(
                        selectedTopic = topic,
                        onSelectTopic = onSelectTopic,
                        language = language,
                        onClose = onClose,
                        media = media
                    )
                }
            }
        }
    }
}
