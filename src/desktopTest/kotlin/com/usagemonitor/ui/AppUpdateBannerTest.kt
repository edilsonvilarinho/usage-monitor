package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.ui.APP_UPDATE_BANNER_TAG
import com.usagemonitor.presentation.ui.AppUpdateBanner
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.viewmodel.AppUpdateFailureReason
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import kotlin.test.Test
import kotlin.test.assertEquals

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
        onNodeWithText("Baixar atualização →").assertIsDisplayed()

        onNodeWithTag(APP_UPDATE_BANNER_TAG).performClick()
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

        // Faixa clicável sem rótulo de ação seria um alvo de clique invisível.
        onNodeWithTag(APP_UPDATE_BANNER_TAG).performClick()
        assertEquals(false, opened)
        assertEquals(false, restarted)
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

        onNodeWithText("Versão 38.0.0 pronta — será aplicada ao fechar").assertIsDisplayed()
        onNodeWithText("Reiniciar e atualizar agora →").assertIsDisplayed()

        onNodeWithTag(APP_UPDATE_BANNER_TAG).performClick()
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
        onNodeWithText("Baixar manualmente →").assertIsDisplayed()

        onNodeWithTag(APP_UPDATE_BANNER_TAG).performClick()
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

        onNodeWithText("Version 38.0.0 is ready — it will be applied on exit").assertIsDisplayed()
        onNodeWithText("Restart and update now →").assertIsDisplayed()
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
