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
import com.usagemonitor.presentation.ui.UpdateBannerContent
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.updateBannerAction
import com.usagemonitor.presentation.ui.updateBannerContent
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
            AppUpdateUiState.Downloaded(update),
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
        showBanner(state = AppUpdateUiState.Downloaded(update), onRestartAndUpdate = { restarted = true })

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
            AppUpdateUiState.Downloaded(update),
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

    /**
     * Beta diz "beta" em texto em todo estado e nos dois idiomas (issue #355):
     * a faixa tem o mesmo tom da estável, e cor nunca informa sozinha.
     */
    @Test
    fun `a beta update says beta in every state and language`() {
        val beta = AppUpdateInfo(version = "39.0.0-beta.1", releasePageUrl = "https://example.com")
        val states = listOf(
            AppUpdateUiState.Available(beta),
            AppUpdateUiState.Downloading(beta, percent = 42),
            AppUpdateUiState.Downloading(beta, percent = null),
            AppUpdateUiState.Downloaded(beta),
            AppUpdateUiState.Failed(beta, AppUpdateFailureReason.DOWNLOAD),
            AppUpdateUiState.Failed(beta, AppUpdateFailureReason.SCHEDULE)
        )
        AppLanguage.entries.forEach { language ->
            states.forEach { state ->
                val content = updateBannerContent(state, language)
                // "beta" como palavra, e não só dentro do número "-beta.1".
                val word = Regex("""(^|\s)[Bb]eta(\s|$)""")
                assertTrue(word.containsMatchIn(content.title), "$language $state: ${content.title}")
                assertTrue(word.containsMatchIn(content.headline), "$language $state: ${content.headline}")
                // O número não cabe na linha curta do balão e desce para o detalhe.
                assertTrue(content.detail.startsWith("39.0.0-beta.1 · "), "$language $state: ${content.detail}")
            }
        }
        assertEquals(
            "Nova versão beta 39.0.0-beta.1 disponível",
            updateBannerContent(AppUpdateUiState.Available(beta), AppLanguage.PT).title
        )
        assertEquals(
            "Beta version 39.0.0-beta.1 is available",
            updateBannerContent(AppUpdateUiState.Available(beta), AppLanguage.EN).title
        )
        assertEquals("Nova versão beta", updateBannerContent(AppUpdateUiState.Available(beta), AppLanguage.PT).headline)
    }

    @Test
    fun `a stable update keeps its wording`() {
        assertEquals("Nova versão 38.0.0 disponível", updateBannerContent(AppUpdateUiState.Available(update), AppLanguage.PT).title)
        assertEquals("Version 38.0.0 is available", updateBannerContent(AppUpdateUiState.Available(update), AppLanguage.EN).title)
        assertEquals("Version 38.0.0 ready", updateBannerContent(AppUpdateUiState.Downloaded(update), AppLanguage.EN).headline)
        assertEquals(
            "Could not download version 38.0.0",
            updateBannerContent(AppUpdateUiState.Failed(update, AppUpdateFailureReason.DOWNLOAD), AppLanguage.EN).title
        )
    }

    @Test
    fun `ready announces the exit behaviour and offers the restart`() = runDesktopComposeUiTest {
        var opened = false
        var restarted = false
        showBanner(
            state = AppUpdateUiState.Downloaded(update),
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
            ScreenTestTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    AppUpdateBanner(
                        state = AppUpdateUiState.Downloaded(update),
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

    /**
     * Conteúdo inteiro de cada estado, nos dois idiomas, na versão estável: título,
     * rótulo da ação, tom, cabeçalho e detalhe. O data class compara os cinco campos
     * de uma vez. O valor é escrito à mão, não lido da produção: trocar um texto,
     * um tom ou o ramo de um idioma quebra aqui.
     */
    @Test
    fun `stable update has the exact content of every state in both languages`() {
        assertEquals(
            UpdateBannerContent(
                title = "Nova versão 38.0.0 disponível",
                actionLabel = "Baixar atualização",
                tone = AppTone.INFO,
                headline = "Nova versão 38.0.0",
                detail = "Baixe pela página da versão"
            ),
            updateBannerContent(AppUpdateUiState.Available(update), AppLanguage.PT),
            "PT available"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Version 38.0.0 is available",
                actionLabel = "Download update",
                tone = AppTone.INFO,
                headline = "New version 38.0.0",
                detail = "Download it from the release page"
            ),
            updateBannerContent(AppUpdateUiState.Available(update), AppLanguage.EN),
            "EN available"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Baixando a versão 38.0.0 — 42%",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Baixando 38.0.0",
                detail = "42% concluído"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(update, percent = 42), AppLanguage.PT),
            "PT downloading 42"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Downloading version 38.0.0 — 42%",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Downloading 38.0.0",
                detail = "42% done"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(update, percent = 42), AppLanguage.EN),
            "EN downloading 42"
        )
        // Zero é porcentagem medida, não ausência: não cai no ramo "sem total".
        assertEquals(
            UpdateBannerContent(
                title = "Baixando a versão 38.0.0 — 0%",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Baixando 38.0.0",
                detail = "0% concluído"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(update, percent = 0), AppLanguage.PT),
            "PT downloading 0"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Downloading version 38.0.0 — 0%",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Downloading 38.0.0",
                detail = "0% done"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(update, percent = 0), AppLanguage.EN),
            "EN downloading 0"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Baixando a versão 38.0.0…",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Baixando 38.0.0",
                detail = "Em segundo plano"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(update, percent = null), AppLanguage.PT),
            "PT downloading null"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Downloading version 38.0.0…",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Downloading 38.0.0",
                detail = "In the background"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(update, percent = null), AppLanguage.EN),
            "EN downloading null"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Versão 38.0.0 pronta — será aplicada ao fechar o Usage Monitor",
                actionLabel = "Reiniciar o app e atualizar",
                tone = AppTone.OK,
                headline = "Versão 38.0.0 pronta",
                detail = "Aplicada ao fechar o Usage Monitor"
            ),
            updateBannerContent(AppUpdateUiState.Downloaded(update), AppLanguage.PT),
            "PT downloaded"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Version 38.0.0 is ready — applies when Usage Monitor closes",
                actionLabel = "Restart app and update",
                tone = AppTone.OK,
                headline = "Version 38.0.0 ready",
                detail = "Applies when Usage Monitor closes"
            ),
            updateBannerContent(AppUpdateUiState.Downloaded(update), AppLanguage.EN),
            "EN downloaded"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Falha ao baixar a versão 38.0.0",
                actionLabel = "Baixar manualmente",
                tone = AppTone.WARNING,
                headline = "Falha na versão 38.0.0",
                detail = "O download não terminou"
            ),
            updateBannerContent(AppUpdateUiState.Failed(update, AppUpdateFailureReason.DOWNLOAD), AppLanguage.PT),
            "PT failed download"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Could not download version 38.0.0",
                actionLabel = "Download manually",
                tone = AppTone.WARNING,
                headline = "Version 38.0.0 failed",
                detail = "The download did not finish"
            ),
            updateBannerContent(AppUpdateUiState.Failed(update, AppUpdateFailureReason.DOWNLOAD), AppLanguage.EN),
            "EN failed download"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Falha ao iniciar a instalação da versão 38.0.0",
                actionLabel = "Baixar manualmente",
                tone = AppTone.WARNING,
                headline = "Falha na versão 38.0.0",
                detail = "A instalação não iniciou"
            ),
            updateBannerContent(AppUpdateUiState.Failed(update, AppUpdateFailureReason.SCHEDULE), AppLanguage.PT),
            "PT failed schedule"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Could not start the version 38.0.0 install",
                actionLabel = "Download manually",
                tone = AppTone.WARNING,
                headline = "Version 38.0.0 failed",
                detail = "The install did not start"
            ),
            updateBannerContent(AppUpdateUiState.Failed(update, AppUpdateFailureReason.SCHEDULE), AppLanguage.EN),
            "EN failed schedule"
        )
    }

    /**
     * Beta: o cabeçalho diz "beta" em todo estado (issue #355) e o número desce
     * para o detalhe, com " · " antes do texto do estável. Os dois idiomas e os
     * dois motivos de falha, com valor literal.
     */
    @Test
    fun `beta update has the exact content of every state in both languages`() {
        val beta = AppUpdateInfo(version = "39.0.0-beta.1", releasePageUrl = "https://example.com")
        assertEquals(
            UpdateBannerContent(
                title = "Nova versão beta 39.0.0-beta.1 disponível",
                actionLabel = "Baixar atualização",
                tone = AppTone.INFO,
                headline = "Nova versão beta",
                detail = "39.0.0-beta.1 · Baixe pela página da versão"
            ),
            updateBannerContent(AppUpdateUiState.Available(beta), AppLanguage.PT),
            "PT beta available"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Beta version 39.0.0-beta.1 is available",
                actionLabel = "Download update",
                tone = AppTone.INFO,
                headline = "New beta version",
                detail = "39.0.0-beta.1 · Download it from the release page"
            ),
            updateBannerContent(AppUpdateUiState.Available(beta), AppLanguage.EN),
            "EN beta available"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Baixando a versão beta 39.0.0-beta.1 — 42%",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Baixando a beta",
                detail = "39.0.0-beta.1 · 42% concluído"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(beta, percent = 42), AppLanguage.PT),
            "PT beta downloading 42"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Downloading beta version 39.0.0-beta.1 — 42%",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Downloading beta",
                detail = "39.0.0-beta.1 · 42% done"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(beta, percent = 42), AppLanguage.EN),
            "EN beta downloading 42"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Baixando a versão beta 39.0.0-beta.1…",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Baixando a beta",
                detail = "39.0.0-beta.1 · Em segundo plano"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(beta, percent = null), AppLanguage.PT),
            "PT beta downloading null"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Downloading beta version 39.0.0-beta.1…",
                actionLabel = null,
                tone = AppTone.INFO,
                headline = "Downloading beta",
                detail = "39.0.0-beta.1 · In the background"
            ),
            updateBannerContent(AppUpdateUiState.Downloading(beta, percent = null), AppLanguage.EN),
            "EN beta downloading null"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Versão beta 39.0.0-beta.1 pronta — será aplicada ao fechar o Usage Monitor",
                actionLabel = "Reiniciar o app e atualizar",
                tone = AppTone.OK,
                headline = "Beta pronta",
                detail = "39.0.0-beta.1 · Aplicada ao fechar o Usage Monitor"
            ),
            updateBannerContent(AppUpdateUiState.Downloaded(beta), AppLanguage.PT),
            "PT beta downloaded"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Beta version 39.0.0-beta.1 is ready — applies when Usage Monitor closes",
                actionLabel = "Restart app and update",
                tone = AppTone.OK,
                headline = "Beta ready",
                detail = "39.0.0-beta.1 · Applies when Usage Monitor closes"
            ),
            updateBannerContent(AppUpdateUiState.Downloaded(beta), AppLanguage.EN),
            "EN beta downloaded"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Falha ao baixar a versão beta 39.0.0-beta.1",
                actionLabel = "Baixar manualmente",
                tone = AppTone.WARNING,
                headline = "Falha na beta",
                detail = "39.0.0-beta.1 · O download não terminou"
            ),
            updateBannerContent(AppUpdateUiState.Failed(beta, AppUpdateFailureReason.DOWNLOAD), AppLanguage.PT),
            "PT beta failed download"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Could not download beta version 39.0.0-beta.1",
                actionLabel = "Download manually",
                tone = AppTone.WARNING,
                headline = "Beta failed",
                detail = "39.0.0-beta.1 · The download did not finish"
            ),
            updateBannerContent(AppUpdateUiState.Failed(beta, AppUpdateFailureReason.DOWNLOAD), AppLanguage.EN),
            "EN beta failed download"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Falha ao iniciar a instalação da versão beta 39.0.0-beta.1",
                actionLabel = "Baixar manualmente",
                tone = AppTone.WARNING,
                headline = "Falha na beta",
                detail = "39.0.0-beta.1 · A instalação não iniciou"
            ),
            updateBannerContent(AppUpdateUiState.Failed(beta, AppUpdateFailureReason.SCHEDULE), AppLanguage.PT),
            "PT beta failed schedule"
        )
        assertEquals(
            UpdateBannerContent(
                title = "Could not start the beta version 39.0.0-beta.1 install",
                actionLabel = "Download manually",
                tone = AppTone.WARNING,
                headline = "Beta failed",
                detail = "39.0.0-beta.1 · The install did not start"
            ),
            updateBannerContent(AppUpdateUiState.Failed(beta, AppUpdateFailureReason.SCHEDULE), AppLanguage.EN),
            "EN beta failed schedule"
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.showBanner(
        state: AppUpdateUiState,
        onOpenRelease: () -> Unit = {},
        onRestartAndUpdate: () -> Unit = {}
    ) {
        setContent {
            ScreenTestTheme(isDark = true) {
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
