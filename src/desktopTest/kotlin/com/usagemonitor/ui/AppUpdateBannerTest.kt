package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.ui.APP_UPDATE_BANNER_ACTION_TAG
import com.usagemonitor.presentation.ui.APP_UPDATE_BANNER_TAG
import com.usagemonitor.presentation.ui.AppUpdateBanner
import com.usagemonitor.presentation.ui.updateBannerAction
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.AppUpdateFailureReason
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Os quatro estados da faixa, desenhados fora do `DashboardScreen`: a faixa é
 * stateless e exercitá-la direto evita subir o dashboard inteiro para conferir
 * um texto.
 */
@OptIn(ExperimentalTestApi::class)
class AppUpdateBannerTest {

    private val update = AppUpdateInfo(
        version = "38.0.0",
        releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v38.0.0"
    )

    /**
     * A ação por estado tem dona única: a faixa e o balão da engrenagem da HUD a
     * leem daqui, e divergir entre os dois seria o mesmo clique fazendo coisas
     * diferentes conforme o modo de janela.
     */
    @Test
    fun `the action dispatch is the same function for the banner and the hud`() {
        val calls = mutableListOf<String>()
        val openRelease = { calls += "open"; Unit }
        val restart = { calls += "restart"; Unit }
        val states = listOf(
            AppUpdateUiState.Available(update),
            AppUpdateUiState.Downloading(update, percent = 42),
            AppUpdateUiState.Ready(update),
            AppUpdateUiState.Failed(update, AppUpdateFailureReason.DOWNLOAD)
        )

        states.forEach { state -> updateBannerAction(state, openRelease, restart)?.invoke() }

        assertEquals(listOf("open", "restart", "open"), calls)
        assertNull(updateBannerAction(AppUpdateUiState.Downloading(update, percent = 42), openRelease, restart))
    }

    @Test
    fun `available offers the manual download`() = runDesktopComposeUiTest {
        var opened = false
        var restarted = false
        showBanner(
            state = AppUpdateUiState.Available(update),
            onOpenRelease = { opened = true },
            onRestartAndUpdate = { restarted = true }
        )

        onNodeWithText("Nova versão 38.0.0 disponível").assertIsDisplayed()
        onNodeWithText("Baixar atualização").assertIsDisplayed()

        onNodeWithTag(APP_UPDATE_BANNER_ACTION_TAG).performClick()
        assertEquals(true, opened)
        assertEquals(false, restarted)
    }

    @Test
    fun `downloading shows the percentage as text`() = runDesktopComposeUiTest {
        showBanner(state = AppUpdateUiState.Downloading(update, percent = 42))

        // Texto, nunca indicador animado: animação infinita trava o waitForIdle.
        onNodeWithText("Baixando a versão 38.0.0 — 42%").assertIsDisplayed()
    }

    @Test
    fun `downloading without a known total omits the percentage`() = runDesktopComposeUiTest {
        showBanner(state = AppUpdateUiState.Downloading(update, percent = null))

        // Inventar um percentual seria pior que omiti-lo.
        onNodeWithText("Baixando a versão 38.0.0…").assertIsDisplayed()
    }

    @Test
    fun `downloading has no action and does not fire one on click`() = runDesktopComposeUiTest {
        var opened = false
        var restarted = false
        showBanner(
            state = AppUpdateUiState.Downloading(update, percent = 42),
            onOpenRelease = { opened = true },
            onRestartAndUpdate = { restarted = true }
        )

        // Botão sem ação não existe, e a faixa não é alvo de clique nenhum.
        onNodeWithTag(APP_UPDATE_BANNER_ACTION_TAG).assertDoesNotExist()
        onNodeWithTag(APP_UPDATE_BANNER_TAG).assertHasNoClickAction()
        assertEquals(false, opened)
        assertEquals(false, restarted)
    }

    /**
     * Issue #291: o rótulo com seta sobre a faixa clicável não parecia botão. A
     * ação é um botão, e só ele: com a faixa também clicável, clicar fora do botão
     * faria a mesma coisa sem nada indicar isso.
     */
    @Test
    fun `the action is a button and the strip itself is not clickable`() = runDesktopComposeUiTest {
        var restarted = false
        showBanner(state = AppUpdateUiState.Ready(update), onRestartAndUpdate = { restarted = true })

        onNodeWithTag(APP_UPDATE_BANNER_TAG).assertHasNoClickAction()
        onNodeWithTag(APP_UPDATE_BANNER_ACTION_TAG)
            .assertHasClickAction()
            .assertTextEquals("Reiniciar o app e atualizar")

        onNodeWithTag(APP_UPDATE_BANNER_TAG).performClick()
        assertEquals(false, restarted)
    }

    /**
     * O balão da engrenagem da HUD mostra o aviso partido em dois; os quatro
     * estados precisam dos dois pedaços nos dois idiomas, ou o banner de lá sai
     * com uma linha vazia.
     */
    @Test
    fun `every state has a headline and a detail in both languages`() {
        val states = listOf(
            AppUpdateUiState.Available(update),
            AppUpdateUiState.Downloading(update, percent = 42),
            AppUpdateUiState.Downloading(update, percent = null),
            AppUpdateUiState.Ready(update),
            AppUpdateUiState.Failed(update, AppUpdateFailureReason.DOWNLOAD),
            AppUpdateUiState.Failed(update, AppUpdateFailureReason.SCHEDULE)
        )
        AppLanguage.entries.forEach { language ->
            states.forEach { state ->
                val content = updateBannerContent(state, language)
                assertTrue(content.headline.contains("38.0.0"), "$language $state: ${content.headline}")
                assertTrue(content.detail.isNotBlank(), "$language $state")
            }
        }
        val downloading = updateBannerContent(AppUpdateUiState.Downloading(update, 42), AppLanguage.PT)
        assertEquals("Baixando 38.0.0", downloading.headline)
        assertEquals("42% concluído", downloading.detail)
    }

    @Test
    fun `ready announces the exit behaviour and offers the restart`() = runDesktopComposeUiTest {
        var opened = false
        var restarted = false
        showBanner(
            state = AppUpdateUiState.Ready(update),
            onOpenRelease = { opened = true },
            onRestartAndUpdate = { restarted = true }
        )

        onNodeWithText("Versão 38.0.0 pronta — será aplicada ao fechar o Usage Monitor").assertIsDisplayed()
        onNodeWithText("Reiniciar o app e atualizar").assertIsDisplayed()

        onNodeWithTag(APP_UPDATE_BANNER_ACTION_TAG).performClick()
        assertEquals(true, restarted)
        assertEquals(false, opened)
    }

    @Test
    fun `download failure sends the user back to the manual path`() = runDesktopComposeUiTest {
        var opened = false
        showBanner(
            state = AppUpdateUiState.Failed(update, AppUpdateFailureReason.DOWNLOAD),
            onOpenRelease = { opened = true }
        )

        onNodeWithText("Falha ao baixar a versão 38.0.0").assertIsDisplayed()
        onNodeWithText("Baixar manualmente").assertIsDisplayed()

        onNodeWithTag(APP_UPDATE_BANNER_ACTION_TAG).performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `schedule failure says the install could not start, not that the download failed`() =
        runDesktopComposeUiTest {
            showBanner(state = AppUpdateUiState.Failed(update, AppUpdateFailureReason.SCHEDULE))

            onNodeWithText("Falha ao iniciar a instalação da versão 38.0.0").assertIsDisplayed()
        }

    @Test
    fun `english keeps every state translated`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    AppUpdateBanner(
                        state = AppUpdateUiState.Ready(update),
                        language = AppLanguage.EN,
                        onOpenRelease = {},
                        onRestartAndUpdate = {}
                    )
                }
            }
        }

        onNodeWithText("Version 38.0.0 is ready — applies when Usage Monitor closes").assertIsDisplayed()
        onNodeWithText("Restart app and update").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showBanner(
        state: AppUpdateUiState,
        onOpenRelease: () -> Unit = {},
        onRestartAndUpdate: () -> Unit = {}
    ) {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    AppUpdateBanner(
                        state = state,
                        language = AppLanguage.PT,
                        onOpenRelease = onOpenRelease,
                        onRestartAndUpdate = onRestartAndUpdate
                    )
                }
            }
        }
    }
}
