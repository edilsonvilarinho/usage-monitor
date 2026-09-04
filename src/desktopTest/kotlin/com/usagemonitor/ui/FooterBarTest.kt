package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.FOOTER_ADMIN_OVERVIEW_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_COUNTDOWN_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_EXPORT_SNAPSHOT_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_HELP_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_VERSION_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_TEAM_PRESENCE_TEST_TAG
import com.usagemonitor.presentation.ui.components.FOOTER_WINDOW_MODE_OPTION_TAG_PREFIX
import com.usagemonitor.presentation.ui.components.FOOTER_WINDOW_MODE_TEST_TAG
import com.usagemonitor.presentation.ui.components.FooterBar
import com.usagemonitor.presentation.ui.components.WindowMode
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Instant
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalTestApi::class)
class FooterBarTest {

    @Test
    fun `FooterBar displays version and countdown badges with settings icon`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Por tag: os dois emblemas viram texto de uma barra de estado, e um
        // assert por texto encontraria a versão em qualquer outro canto da tela.
        // `useUnmergedTree`: o emblema é âncora de tooltip, e o `TooltipBox`
        // agrega os descendentes na árvore merged — a tag só existe na crua.
        onNodeWithTag(FOOTER_VERSION_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("v1.1.0")
        onNodeWithTag(FOOTER_COUNTDOWN_TEST_TAG, useUnmergedTree = true)
            .assertTextEquals("02:05")
        onNodeWithContentDescription("Abrir configurações").assertIsDisplayed()
        onAllNodesWithText("Histórico").assertCountEquals(0)
    }

    /**
     * A ajuda é a porta óbvia do rodapé, e some no modo somente cards e no HUD —
     * por isso ela também tem item na bandeja e `F1`, que este teste não alcança.
     */
    @Test
    fun `FooterBar abre a ajuda`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var opened = 0

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        onOpenHelp = { opened++ },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithContentDescription("Abrir ajuda").assertIsDisplayed()
        onNodeWithTag(FOOTER_HELP_TEST_TAG).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `FooterBar esconde a visao de todas as contas sem callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Quem não administra é a maioria: o botão nem existe.
        onAllNodesWithTag(FOOTER_ADMIN_OVERVIEW_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `FooterBar mostra e aciona a visao de todas as contas`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var opened = 0

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        onOpenAdminOverview = { opened += 1 }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_ADMIN_OVERVIEW_TEST_TAG).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `FooterBar esconde a presenca do time sem o callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // A porta do integrante comum é o botão do card, escopado na conta dele.
        onAllNodesWithTag(FOOTER_TEAM_PRESENCE_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `FooterBar mostra e aciona a presenca do time`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var opened = 0

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        onOpenTeamPresence = { opened += 1 }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_TEAM_PRESENCE_TEST_TAG).performClick()

        assertEquals(1, opened)
    }


    // ------------------------------------- retrato do Dashboard (issue #215)

    @Test
    fun `FooterBar esconde a exportacao sem o callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Os geradores de captura montam o rodapé sem escrever em disco.
        onAllNodesWithTag(FOOTER_EXPORT_SNAPSHOT_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `FooterBar mostra e aciona a exportacao do retrato`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        var exported = 0

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        onExportSnapshot = { exported += 1 }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_EXPORT_SNAPSHOT_TEST_TAG).performClick()

        assertEquals(1, exported)
    }

    // --------------------------------------------- modos de janela (issue #187)

    @Test
    fun `FooterBar esconde o menu de modos sem o callback`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        // Os geradores de captura montam o rodapé sem despachar nada, e um menu
        // que não troca coisa alguma seria decoração.
        onAllNodesWithTag(FOOTER_WINDOW_MODE_TEST_TAG).assertCountEquals(0)
    }

    /**
     * O menu diz **quais** modos existem, que é metade da queixa da issue: os
     * dois modos alternativos só eram descobertos por acidente.
     */
    @Test
    fun `o menu de modos lista as tres molduras com a corrente marcada`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        windowMode = WindowMode.STANDARD,
                        onWindowModeChange = {}
                    )
                }
            }
        }

        // Fechado, nenhuma opção existe na árvore.
        onAllNodesWithText("Barra HUD").assertCountEquals(0)

        onNodeWithTag(FOOTER_WINDOW_MODE_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithText("Padrão").assertIsDisplayed()
        onNodeWithText("Somente os cards").assertIsDisplayed()
        onNodeWithText("Barra HUD").assertIsDisplayed()
        onNodeWithTag(FOOTER_WINDOW_MODE_OPTION_TAG_PREFIX + "STANDARD").assertIsSelected()
        onNodeWithTag(FOOTER_WINDOW_MODE_OPTION_TAG_PREFIX + "HUD").assertIsNotSelected()
    }

    @Test
    fun `escolher um modo despacha o valor e fecha o menu`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")
        val chosen = mutableListOf<WindowMode>()

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        windowMode = WindowMode.STANDARD,
                        onWindowModeChange = { mode -> chosen += mode }
                    )
                }
            }
        }

        onNodeWithTag(FOOTER_WINDOW_MODE_TEST_TAG).performClick()
        waitForIdle()
        onNodeWithTag(FOOTER_WINDOW_MODE_OPTION_TAG_PREFIX + "HUD").performClick()
        waitForIdle()

        assertEquals(listOf(WindowMode.HUD), chosen)
        onAllNodesWithText("Barra HUD").assertCountEquals(0)
    }

    /**
     * **O caso que a #164 pagou uma vez.** Popup no Compose Desktop é camada
     * dentro da janela, recortada pelos limites dela — e o rodapé é a última
     * linha da janela. A cena aqui é o piso de arrasto da janela principal
     * (240×320dp), com o rodapé encostado na borda de baixo: se o menu abrisse
     * para baixo, ele nasceria fora da janela e as opções não estariam na tela.
     */
    @Test
    fun `no rodape da janela minima o menu abre para cima e cabe`() = runDesktopComposeUiTest {
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Column(modifier = Modifier.width(240.dp).height(320.dp)) {
                    Box(modifier = Modifier.weight(1f))
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false,
                        windowMode = WindowMode.HUD,
                        onWindowModeChange = {}
                    )
                }
            }
        }

        val footerTop = onNodeWithTag(FOOTER_WINDOW_MODE_TEST_TAG).fetchSemanticsNode().boundsInRoot.top

        onNodeWithTag(FOOTER_WINDOW_MODE_TEST_TAG).performClick()
        waitForIdle()

        onNodeWithText("Padrão").assertIsDisplayed()
        onNodeWithText("Somente os cards").assertIsDisplayed()
        onNodeWithText("Barra HUD").assertIsDisplayed()

        val menuBottom = onNodeWithTag(FOOTER_WINDOW_MODE_OPTION_TAG_PREFIX + "HUD")
            .fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(menuBottom <= footerTop, "esperava o menu acima do rodapé: $menuBottom > $footerTop")
    }

    @Test
    fun `FooterBar opens settings action`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "1.1.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 125.seconds,
                        onRefresh = {},
                        onOpenSettings = { opened = true },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithContentDescription("Abrir configurações").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `FooterBar keeps controls accessible in narrow width`() = runDesktopComposeUiTest {
        var opened = false
        val fixedNow = Instant.parse("2025-01-01T12:00:00Z")

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(320.dp)) {
                    FooterBar(
                        appVersion = "6.0.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = fixedNow + 433.seconds,
                        onRefresh = {},
                        onOpenSettings = { opened = true },
                        nowProvider = { fixedNow },
                        countdownUpdatesEnabled = false
                    )
                }
            }
        }

        onNodeWithText("v6.0.0").assertIsDisplayed()
        onNodeWithText("07:13").assertIsDisplayed()
        onNodeWithContentDescription("Abrir configurações").performClick()
        assertEquals(true, opened)
    }

    @Test
    fun `FooterBar decrements countdown and stops at zero without waiting real seconds`() = runDesktopComposeUiTest {
        val start = Instant.parse("2025-01-01T12:00:00Z")
        val tickChannel = Channel<Unit>(Channel.UNLIMITED)
        var currentNow = start

        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(640.dp)) {
                    FooterBar(
                        appVersion = "9.0.0",
                        language = AppLanguage.PT,
                        nextRefreshAt = start + 3.seconds,
                        onRefresh = {},
                        onOpenSettings = {},
                        nowProvider = { currentNow },
                        waitNextTick = { tickChannel.receive() }
                    )
                }
            }
        }

        onNodeWithText("00:03").assertIsDisplayed()

        currentNow += 1.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:02").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        currentNow += 1.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:01").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        currentNow += 1.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:00").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }

        currentNow += 5.seconds
        tickChannel.trySend(Unit)
        waitUntil(timeoutMillis = 5_000) {
            runCatching {
                onNodeWithText("00:00").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }
}
