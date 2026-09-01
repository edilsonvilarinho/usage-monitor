package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.COMPACT_EXIT_DESCRIPTION
import com.usagemonitor.presentation.ui.HUD_BAR_OPEN_DESCRIPTION
import com.usagemonitor.presentation.ui.HudBar
import com.usagemonitor.presentation.ui.TitleBarButton
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppChrome
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O cromo das janelas não tinha teste nenhum.
 *
 * Ele vive em `desktopMain` porque mexe com a janela AWT, e a maior parte dele é
 * `WindowScope.` — `DesktopWindowFrame`, `DesktopTitleBar`, `DesktopDialogTitleBar`
 * e `CompactTitleBarOverlay` todos precisam de uma janela real para o
 * `WindowDraggableArea`, e `runDesktopComposeUiTest` não fornece uma. O que dá
 * para exercitar aqui é o botão de cromo, que é o único que não depende do escopo
 * de janela — e é ele que carrega as três decisões que este arquivo registra: a
 * altura vinda do token, o retângulo que preenche a barra e a descrição semântica
 * dos glifos que não se explicam.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopWindowFrameTest {

    /**
     * O botão preenche a altura da barra menos a divisória de 1dp.
     *
     * Não é detalhe: o botão arredondado flutuando dentro da barra era o único
     * lugar do app onde um controle não encostava na própria moldura, e é por
     * isso que ele não é um `AppIconButton`, que tem 26dp e borda.
     */
    @Test
    fun `o botao de cromo preenche a altura da barra`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    TitleBarButton(label = "×", onClick = {})
                }
            }
        }

        onNodeWithText("×").assertIsDisplayed()
        onNodeWithText("×").assertHeightIsEqualTo(AppChrome.titleBar - 1.dp)
    }

    /**
     * Minimizar, maximizar e fechar são o vocabulário de janela que todo sistema
     * desenha igual; o quadrado do modo somente cards, não — e por isso só ele
     * carrega descrição. É a semântica que o leitor de tela lê, não a tooltip.
     */
    @Test
    fun `o glifo que nao se explica carrega descricao`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    TitleBarButton(
                        label = "▣",
                        onClick = {},
                        description = COMPACT_EXIT_DESCRIPTION
                    )
                }
            }
        }

        onNodeWithContentDescription(COMPACT_EXIT_DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun `o botao de cromo despacha o clique`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    TitleBarButton(label = "–", onClick = { clicks += 1 })
                }
            }
        }

        onNodeWithText("–").performClick()
        assertEquals(1, clicks)
    }

    /**
     * As cinco alturas do cromo são contrato com o design system
     * (`tokens/spacing.css`), e a barra de título e a barra de controles têm de
     * continuar iguais: é o que faz a janela do dashboard e a janela de sessões
     * lerem como o mesmo produto.
     */
    /**
     * `HudBar` não depende de `WindowScope` (decisão já tomada: sem arrasto),
     * então é exercitável aqui inteiro, ao contrário do resto do cromo.
     */
    @Test
    fun `a barra HUD preenche a altura do token`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    HudBar(
                        statusLabel = "Crítico",
                        statusTone = AppTone.CRITICAL,
                        sourceLabel = "Anthropic · Padrão",
                        resetLabel = "reset em 42min",
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("Crítico").assertIsDisplayed()
        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).assertHeightIsEqualTo(AppChrome.hud)
    }

    /** Não há botão próprio: a faixa inteira é o alvo de clique. */
    @Test
    fun `a barra HUD despacha o clique em qualquer ponto da faixa`() = runDesktopComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    HudBar(
                        statusLabel = "Normal",
                        statusTone = AppTone.OK,
                        sourceLabel = null,
                        resetLabel = null,
                        onOpenFull = { clicks += 1 }
                    )
                }
            }
        }

        onNodeWithContentDescription(HUD_BAR_OPEN_DESCRIPTION).performClick()
        assertEquals(1, clicks)
    }

    /** Estado de carregamento: fonte e reset ainda não chegaram. */
    @Test
    fun `a barra HUD sem fonte nem reset nao quebra o layout`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true) {
                Box(modifier = Modifier.width(400.dp).height(120.dp)) {
                    HudBar(
                        statusLabel = "Normal",
                        statusTone = AppTone.OK,
                        sourceLabel = null,
                        resetLabel = null,
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithText("Normal").assertIsDisplayed()
    }

    @Test
    fun `as alturas do cromo batem com o contrato do design system`() {
        assertEquals(34.dp, AppChrome.titleBar)
        assertEquals(34.dp, AppChrome.toolbar)
        assertEquals(30.dp, AppChrome.statusBar)
        assertEquals(28.dp, AppChrome.control)
        assertEquals(28.dp, AppChrome.updateStrip)
        assertEquals(AppChrome.titleBar, AppChrome.toolbar)
        assertEquals(24.dp, AppChrome.hud)
    }
}
